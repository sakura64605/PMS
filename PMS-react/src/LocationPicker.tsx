import { useEffect, useId, useRef, useState } from "react";
import { LocateFixed, MapPin, Search } from "lucide-react";
import { Button, Modal } from "./core";
import {
  loadAmap,
  locateForMap,
  mapPlace,
  type AmapApi,
  type AmapMap,
  type MapPlace,
} from "./location";

export function LocationAddressField({
  label,
  value,
  onChange,
}: {
  label: string;
  value: string;
  onChange: (address: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const inputId = useId();
  return (
    <>
      <div className="field">
        <label htmlFor={inputId}>
          {label}
          <b aria-hidden="true"> *</b>
        </label>
        <div className="location-address-input">
          <input
            id={inputId}
            required
            value={value}
            onChange={(event) => onChange(event.target.value)}
          />
          <button type="button" className="btn" onClick={() => setOpen(true)}>
            <MapPin size={16} />
            地图选点
          </button>
        </div>
      </div>
      {open && (
        <LocationPicker
          initialQuery={value}
          onClose={() => setOpen(false)}
          onConfirm={(place) => {
            onChange(
              place.address && place.address.includes(place.name)
                ? place.address
                : [place.address, place.name].filter(Boolean).join(" "),
            );
            setOpen(false);
          }}
        />
      )}
    </>
  );
}

export function LocationPicker({
  initialQuery = "",
  onClose,
  onConfirm,
}: {
  initialQuery?: string;
  onClose: () => void;
  onConfirm: (place: MapPlace) => void;
}) {
  const container = useRef<HTMLDivElement>(null);
  const runtime = useRef<{
    api: AmapApi;
    map: AmapMap;
    marker?: { setPosition(point: [number, number]): void };
  }>(null);
  const active = useRef(false),
    sequence = useRef(0);
  const deadline = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const [query, setQuery] = useState(initialQuery),
    [retry, setRetry] = useState(0);
  const [ready, setReady] = useState(false),
    [busy, setBusy] = useState(false);
  const [error, setError] = useState(""),
    [selected, setSelected] = useState<MapPlace | null>(null);
  const [places, setPlaces] = useState<MapPlace[]>([]);

  function nextRequest() {
    clearTimeout(deadline.current);
    const request = ++sequence.current;
    deadline.current = setTimeout(() => {
      if (active.current && sequence.current === request) {
        sequence.current++;
        setBusy(false);
        setError("地图请求超时，请重试或手动填写地点");
      }
    }, 20000);
    return request;
  }

  function show(place: MapPlace) {
    const current = runtime.current;
    if (!current || !active.current) return;
    const point: [number, number] = [place.longitude, place.latitude];
    current.map.setCenter(point);
    if (current.marker) current.marker.setPosition(point);
    else current.marker = new current.api.Marker({ map: current.map, position: point });
    setSelected(place);
  }

  function resolvePoint(point: [number, number], request: number) {
    const current = runtime.current;
    if (!current) return;
    new current.api.Geocoder().getAddress(point, (status, result) => {
      if (!active.current || sequence.current !== request) return;
      clearTimeout(deadline.current);
      setBusy(false);
      const address = result.regeocode?.formattedAddress;
      if (status !== "complete" || !address) {
        setError("无法识别该位置的地址，请搜索地点或重新选点");
        return;
      }
      try {
        show(mapPlace({ getLng: () => point[0], getLat: () => point[1] }, "", address));
      } catch (failure) {
        setError((failure as Error).message);
      }
    });
  }

  useEffect(() => {
    let disposed = false;
    active.current = true;
    setReady(false);
    setError("");
    loadAmap()
      .then((amap) => {
        if (disposed || !active.current || !container.current) return;
        const map = new amap.Map(container.current, { zoom: 12 });
        runtime.current = { api: amap, map };
        map.on("click", ({ lnglat }) => {
          const request = nextRequest();
          setError("");
          setSelected(null);
          setBusy(true);
          resolvePoint([lnglat.getLng(), lnglat.getLat()], request);
        });
        setReady(true);
      })
      .catch((failure: Error) => {
        if (!disposed && active.current) setError(failure.message);
      });
    return () => {
      disposed = true;
      active.current = false;
      sequence.current++;
      clearTimeout(deadline.current);
      runtime.current?.map.destroy();
      runtime.current = null;
    };
  }, [retry]);

  async function search() {
    const current = runtime.current;
    if (!current || !query.trim()) return;
    const request = nextRequest();
    setBusy(true);
    setError("");
    setSelected(null);
    setPlaces([]);
    new current.api.PlaceSearch({ pageSize: 10 }).search(query.trim(), (status, result) => {
      if (!active.current || sequence.current !== request) return;
      clearTimeout(deadline.current);
      setBusy(false);
      if (status !== "complete") {
        setError("未找到地点或搜索暂不可用，请换个关键词重试");
        return;
      }
      const found: MapPlace[] = [];
      for (const poi of result.poiList?.pois || []) {
        if (!poi.location) continue;
        try {
          found.push(
            mapPlace(
              poi.location,
              poi.name || "",
              typeof poi.address === "string" ? poi.address : "",
              poi.id,
            ),
          );
        } catch {
          /* Ignore malformed provider rows; never invent a point. */
        }
      }
      setPlaces(found);
      if (!found.length) setError("没有找到可选地点，请换个关键词");
    });
  }

  async function locate() {
    const current = runtime.current;
    if (!current) return;
    const request = nextRequest();
    setBusy(true);
    setError("");
    setSelected(null);
    try {
      const point = await locateForMap(current.api);
      if (active.current && sequence.current === request)
        resolvePoint([point.longitude, point.latitude], request);
    } catch (failure) {
      if (active.current && sequence.current === request) {
        clearTimeout(deadline.current);
        setBusy(false);
        setError((failure as Error).message);
      }
    }
  }

  return (
    <Modal title="选择地点" onClose={onClose}>
      <div className="location-picker">
        <div className="location-search">
          <input
            aria-label="搜索地点"
            value={query}
            maxLength={80}
            placeholder="输入城市、地点或地址"
            onChange={(event) => setQuery(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter") {
                event.preventDefault();
                void search();
              }
            }}
          />
          <Button disabled={!ready || busy || !query.trim()} action={search}>
            <Search size={16} />
            搜索
          </Button>
          <Button disabled={!ready || busy} action={locate}>
            <LocateFixed size={16} />
            定位
          </Button>
        </div>
        <small>点击定位才会申请位置权限，仅用于本次选点；也可以搜索或点击地图选择。</small>
        {error && (
          <p role="alert">
            {error}{" "}
            {!ready && (
              <button type="button" className="btn" onClick={() => setRetry((value) => value + 1)}>
                重试地图
              </button>
            )}
          </p>
        )}
        {!ready && !error && <p role="status">正在加载地图…</p>}
        <div className="location-map" ref={container} aria-label="地点地图" />
        {!!places.length && (
          <ul className="location-results">
            {places.map((place, index) => (
              <li key={place.poiId || index}>
                <button
                  type="button"
                  onClick={() => {
                    sequence.current++;
                    clearTimeout(deadline.current);
                    setBusy(false);
                    setError("");
                    show(place);
                  }}
                >
                  <strong>{place.name}</strong>
                  <small>{place.address}</small>
                </button>
              </li>
            ))}
          </ul>
        )}
        {selected && (
          <p className="location-selected">
            <MapPin size={16} />
            {[selected.address, selected.name].filter(Boolean).join(" ")}
          </p>
        )}
        <div className="form-actions">
          <Button action={onClose}>取消</Button>
          <Button
            tone="primary"
            disabled={!selected || busy}
            action={() => {
              if (selected) onConfirm(selected);
            }}
          >
            使用此地点
          </Button>
        </div>
      </div>
    </Modal>
  );
}
