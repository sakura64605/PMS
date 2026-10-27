type Socket = Pick<WebSocket, "readyState" | "close" | "onopen" | "onclose" | "onmessage">;

/** A single owned connection, with capped backoff and no retry-attempt cutoff. */
export function maintainNotificationConnection(create: () => Promise<Socket>, refresh: () => void) {
  let disposed = false,
    connecting = false,
    attempt = 0;
  let socket: Socket | undefined;
  let timer: ReturnType<typeof setTimeout> | undefined;
  let watchdog: ReturnType<typeof setTimeout> | undefined;
  const schedule = () => {
    if (disposed || timer || navigator.onLine === false) return;
    timer = setTimeout(
      () => {
        timer = undefined;
        void connect();
      },
      Math.min(30_000, 2000 * 2 ** Math.min(attempt++, 4)),
    );
  };
  async function connect() {
    if (disposed || connecting || navigator.onLine === false || socket?.readyState === 1) return;
    connecting = true;
    try {
      const current = await create();
      if (disposed) {
        current.close();
        return;
      }
      socket = current;
      current.onmessage = () => {
        if (disposed || socket !== current) return;
        refresh();
        window.dispatchEvent(new Event("server-message"));
      };
      current.onopen = () => {
        if (disposed || socket !== current) return;
        connecting = false;
        attempt = 0;
        clearTimeout(watchdog);
        refresh();
        window.dispatchEvent(new Event("message-connection-open"));
      };
      current.onclose = () => {
        if (disposed || socket !== current) return;
        clearTimeout(watchdog);
        connecting = false;
        socket = undefined;
        schedule();
      };
      watchdog = setTimeout(() => {
        if (socket !== current || current.readyState === 1) return;
        socket = undefined;
        connecting = false;
        current.close();
        schedule();
      }, 15_000);
    } catch {
      connecting = false;
      schedule();
    }
  }
  const resume = () => {
    if (navigator.onLine === false) return;
    clearTimeout(timer);
    timer = undefined;
    void connect();
  };
  const onVisible = () => {
    if (document.visibilityState !== "hidden") resume();
  };
  window.addEventListener("online", resume);
  document.addEventListener("visibilitychange", onVisible);
  void connect();
  return () => {
    disposed = true;
    clearTimeout(timer);
    clearTimeout(watchdog);
    window.removeEventListener("online", resume);
    document.removeEventListener("visibilitychange", onVisible);
    socket?.close();
  };
}
