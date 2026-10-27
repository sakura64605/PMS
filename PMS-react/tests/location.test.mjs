import { test, after } from "node:test";
import assert from "node:assert/strict";
import { JSDOM } from "jsdom";
import React, { act } from "react";
import { createServer } from "vite";
import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";

const dom = new JSDOM('<div id="root"></div>', { url: "https://www.petcircle.tech/" });
for (const name of ["window", "document", "localStorage", "HTMLElement", "Event", "navigator"])
  Object.defineProperty(globalThis, name, { value: dom.window[name], configurable: true });
globalThis.IS_REACT_ACT_ENVIRONMENT = true;
window.HTMLDialogElement.prototype.showModal = function () {
  this.setAttribute("open", "");
};
window.HTMLDialogElement.prototype.close = function () {
  this.removeAttribute("open");
};
const server = await createServer({
  configFile: false,
  root: fileURLToPath(new URL("..", import.meta.url)),
  plugins: [react()],
  server: { middlewareMode: true, hmr: false },
});
const { loadAmap, mapPlace, locateForMap } = await server.ssrLoadModule("/src/location.ts");
const { LocationPicker, LocationAddressField } =
  await server.ssrLoadModule("/src/LocationPicker.tsx");
const { createRoot } = await import("react-dom/client");
after(async () => {
  await server.close();
  dom.window.close();
});
const point = (longitude, latitude) => ({ getLng: () => longitude, getLat: () => latitude });

test("coordinate values are validated and provider/coordinate system are explicit", () => {
  assert.equal(mapPlace(point(120, 30), "公园", "杭州市").coordinateSystem, "GCJ02");
  for (const pair of [
    [NaN, 30],
    [120, Infinity],
    [181, 30],
    [120, -91],
  ])
    assert.throws(() => mapPlace(point(...pair), "", ""), /无效坐标/);
});

test("position access requires secure context and conversion failure never returns raw GPS", async () => {
  let requests = 0;
  Object.defineProperty(window, "isSecureContext", { value: false, configurable: true });
  Object.defineProperty(navigator, "geolocation", {
    value: {
      getCurrentPosition(success) {
        requests++;
        success({ coords: { longitude: 120, latitude: 30 } });
      },
    },
    configurable: true,
  });
  await assert.rejects(locateForMap({}), /HTTPS/);
  assert.equal(requests, 0);
  Object.defineProperty(window, "isSecureContext", { value: true, configurable: true });
  await assert.rejects(
    locateForMap({
      convertFrom(input, type, callback) {
        assert.deepEqual(input, [120, 30]);
        assert.equal(type, "gps");
        callback("error", {});
      },
    }),
    /坐标转换失败/,
  );
  const location = await locateForMap({
    convertFrom(input, type, callback) {
      callback("complete", { locations: [point(120.01, 30.01)] });
    },
  });
  assert.equal(location.longitude, 120.01);
  assert.equal(location.coordinateSystem, "GCJ02");
});

let configurationCalls = 0;
const configuration = (enabled = true) => {
  globalThis.fetch = async () => {
    configurationCalls++;
    return new Response(
      JSON.stringify({
        code: 200,
        data: { enabled, jsApiKey: "public_js_key_0123456789", serviceHost: "/_AMapService" },
      }),
    );
  };
};
test("disabled map does not load a provider script and can be retried after configuration", async () => {
  configuration(false);
  await assert.rejects(loadAmap(), /地图暂未启用/);
  assert.equal(document.querySelectorAll("script[data-pms-amap]").length, 0);
});

const maps = [],
  searches = [],
  geocodes = [];
let destroyed = 0;
const fakeAmap = {
  Map: class {
    constructor() {
      maps.push(this);
    }
    on(event, callback) {
      this.click = callback;
    }
    setCenter() {}
    destroy() {
      destroyed++;
    }
  },
  Marker: class {
    setPosition() {}
  },
  PlaceSearch: class {
    search(keyword, callback) {
      searches.push({ keyword, callback });
    }
  },
  Geocoder: class {
    getAddress(position, callback) {
      geocodes.push({ position, callback });
    }
  },
};
test("SDK failures clean up; retry shares one load and installs only the server proxy address", async () => {
  configuration();
  const append = document.head.appendChild.bind(document.head);
  document.head.appendChild = (script) => {
    assert.equal(window._AMapSecurityConfig.serviceHost, "https://www.petcircle.tech/_AMapService");
    assert.equal("securityJsCode" in window._AMapSecurityConfig, false);
    append(script);
    queueMicrotask(() => script.onerror?.(new Event("error")));
    return script;
  };
  await assert.rejects(loadAmap(), /地图加载失败/);
  assert.equal(document.querySelectorAll("script[data-pms-amap]").length, 0);
  const before = configurationCalls;
  document.head.appendChild = (script) => {
    append(script);
    window.AMap = fakeAmap;
    queueMicrotask(() => script.onload?.(new Event("load")));
    return script;
  };
  const first = loadAmap(),
    second = loadAmap();
  assert.equal(first, second);
  assert.equal(await first, fakeAmap);
  assert.equal(configurationCalls - before, 1);
  assert.equal(document.querySelectorAll("script[data-pms-amap]").length, 1);
  document.head.appendChild = append;
});

test("picker ignores stale geocoding and confirms the explicitly selected search result", async () => {
  const host = document.getElementById("root"),
    root = createRoot(host);
  let selected;
  await act(async () =>
    root.render(
      React.createElement(LocationPicker, {
        initialQuery: "杭州公园",
        onClose() {},
        onConfirm(place) {
          selected = place;
        },
      }),
    ),
  );
  await act(async () => maps.at(-1).click({ lnglat: point(121, 31) }));
  const oldGeocode = geocodes.at(-1);
  // A map reverse-geocode is pending; a later manual map click must take precedence.
  await act(async () => maps.at(-1).click({ lnglat: point(120, 30) }));
  await act(async () =>
    oldGeocode.callback("complete", { regeocode: { formattedAddress: "过期地址" } }),
  );
  assert.equal(host.textContent.includes("过期地址"), false);
  await act(async () =>
    geocodes.at(-1).callback("complete", { regeocode: { formattedAddress: "当前地址" } }),
  );
  await act(async () =>
    [...host.querySelectorAll("button")].find((button) => button.textContent === "搜索").click(),
  );
  await act(async () =>
    searches.at(-1).callback("complete", {
      poiList: {
        pois: [{ id: "poi-1", name: "公园东门", address: "杭州市", location: point(120.1, 30.1) }],
      },
    }),
  );
  await act(async () => host.querySelector(".location-results button").click());
  await act(async () =>
    [...host.querySelectorAll("button")]
      .find((button) => button.textContent === "使用此地点")
      .click(),
  );
  assert.equal(selected.poiId, "poi-1");
  assert.equal(selected.longitude, 120.1);
  assert.equal(selected.coordinateSystem, "GCJ02");
  await act(async () => root.unmount());
  assert.equal(destroyed, 1);
});

test("address input stays manually editable without opening or loading a map", async () => {
  const host = document.getElementById("root"),
    root = createRoot(host);
  const before = configurationCalls;
  await act(async () =>
    root.render(
      React.createElement(LocationAddressField, {
        label: "活动地点",
        value: "用户填写地点",
        onChange() {},
      }),
    ),
  );
  assert.equal(host.querySelector("input").value, "用户填写地点");
  assert.equal(host.querySelector("dialog"), null);
  assert.equal(configurationCalls, before);
  await act(async () => root.unmount());
});
