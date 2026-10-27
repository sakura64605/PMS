import { api } from "./api";

export type MapPlace = {
  longitude: number;
  latitude: number;
  coordinateSystem: "GCJ02";
  provider: "AMAP";
  name: string;
  address: string;
  poiId?: string;
};
export type MapPoint = { getLng(): number; getLat(): number };
export type AmapPoi = { id?: string; name?: string; address?: string; location?: MapPoint };
export type AmapMap = {
  on(event: "click", listener: (event: { lnglat: MapPoint }) => void): void;
  setCenter(point: [number, number]): void;
  destroy(): void;
};
export type AmapApi = {
  Map: new (
    container: HTMLElement,
    options: { zoom: number; center?: [number, number] },
  ) => AmapMap;
  Marker: new (options: { map: AmapMap; position: [number, number] }) => {
    setPosition(point: [number, number]): void;
  };
  PlaceSearch: new (options: { pageSize: number }) => {
    search(
      keyword: string,
      callback: (status: string, result: { poiList?: { pois?: AmapPoi[] } }) => void,
    ): void;
  };
  Geocoder: new () => {
    getAddress(
      point: [number, number],
      callback: (status: string, result: { regeocode?: { formattedAddress?: string } }) => void,
    ): void;
  };
  convertFrom(
    point: [number, number],
    type: "gps",
    callback: (status: string, result: { locations?: MapPoint[] }) => void,
  ): void;
};
declare global {
  interface Window {
    AMap?: AmapApi;
    _AMapSecurityConfig?: { serviceHost: string };
  }
}

let loading: Promise<AmapApi> | undefined;
export function loadAmap(): Promise<AmapApi> {
  if (loading) return loading;
  loading = (async () => {
    const config = await api<{ enabled: boolean; jsApiKey: string | null; serviceHost: string }>(
      "/location/config",
    );
    if (!config.enabled) throw new Error("地图暂未启用，请先手动填写地点");
    if (
      !config.jsApiKey ||
      !/^[A-Za-z0-9_-]{16,128}$/.test(config.jsApiKey) ||
      config.serviceHost !== "/_AMapService"
    )
      throw new Error("地图配置异常，请先手动填写地点");
    window._AMapSecurityConfig = {
      serviceHost: new URL(config.serviceHost, window.location.origin).href,
    };
    return new Promise<AmapApi>((resolve, reject) => {
      const script = document.createElement("script");
      script.async = true;
      script.dataset.pmsAmap = "true";
      const cleanup = () => {
        clearTimeout(timer);
        script.onload = null;
        script.onerror = null;
      };
      const fail = () => {
        cleanup();
        script.remove();
        reject(new Error("地图加载失败，请重试或手动填写地点"));
      };
      const timer = setTimeout(fail, 15000);
      script.onload = () => {
        cleanup();
        if (window.AMap) resolve(window.AMap);
        else {
          script.remove();
          reject(new Error("地图初始化失败，请重试"));
        }
      };
      script.onerror = fail;
      script.src = `https://webapi.amap.com/maps?v=2.0&key=${encodeURIComponent(config.jsApiKey!)}&plugin=AMap.PlaceSearch,AMap.Geocoder`;
      document.head.appendChild(script);
    });
  })().catch((error) => {
    loading = undefined;
    throw error;
  });
  return loading;
}

export function mapPlace(point: MapPoint, name: string, address: string, poiId?: string): MapPlace {
  const longitude = point.getLng(),
    latitude = point.getLat();
  if (
    !Number.isFinite(longitude) ||
    !Number.isFinite(latitude) ||
    Math.abs(longitude) > 180 ||
    Math.abs(latitude) > 90
  )
    throw new Error("地图返回了无效坐标，请重新选点");
  return { longitude, latitude, coordinateSystem: "GCJ02", provider: "AMAP", name, address, poiId };
}

/** Called only after the user explicitly clicks Locate; never silently assumes GPS equals GCJ02. */
export function locateForMap(amap: AmapApi): Promise<MapPlace> {
  return new Promise((resolve, reject) => {
    if (!window.isSecureContext || !navigator.geolocation) {
      reject(new Error("当前环境不能定位，请使用 HTTPS 或手动搜索地点"));
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (position) => {
        amap.convertFrom(
          [position.coords.longitude, position.coords.latitude],
          "gps",
          (status, result) => {
            if (status !== "complete" || !result.locations?.[0]) {
              reject(new Error("定位坐标转换失败，请手动搜索地点"));
              return;
            }
            try {
              resolve(mapPlace(result.locations[0], "当前位置", ""));
            } catch (error) {
              reject(error);
            }
          },
        );
      },
      () => reject(new Error("未能获取位置，请允许定位后重试或手动搜索地点")),
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 },
    );
  });
}
