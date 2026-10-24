import {Suspense, useCallback, useRef, type ReactNode} from 'react';
import {AtomicRoutes} from './routeTransition';

const isPrivate = (path: string) => path.split('?')[0] === '/private-message';

/** Keep the actual background page mounted while the private-message dialog is open. */
export function PrivateMessageRoutes({path, enabled = true, onCommit, children, renderPrivate}: {
  path: string; enabled?: boolean; onCommit?: (path: string) => void;
  children: (path: string) => ReactNode; renderPrivate: (close: () => void) => ReactNode;
}) {
  const entry = useRef({path, background: isPrivate(path) ? '/pets' : path, openedFromPage: false});
  if (entry.current.path !== path) {
    if (isPrivate(path) && !isPrivate(entry.current.path)) {
      entry.current.background = entry.current.path;
      entry.current.openedFromPage = true;
    } else if (!isPrivate(path)) {
      entry.current.background = path;
      entry.current.openedFromPage = false;
    }
    entry.current.path = path;
  }
  const close = useCallback(() => {
    if (entry.current.openedFromPage && window.history.length > 1) {
      window.history.back();
    } else {
      window.history.replaceState({}, '', entry.current.background);
      window.dispatchEvent(new PopStateEvent('popstate'));
    }
  }, []);
  const modal = enabled && isPrivate(path);
  return <>
    <AtomicRoutes path={modal ? entry.current.background : path} onCommit={onCommit}>{children}</AtomicRoutes>
    {modal && <Suspense fallback={null}>{renderPrivate(close)}</Suspense>}
  </>;
}
