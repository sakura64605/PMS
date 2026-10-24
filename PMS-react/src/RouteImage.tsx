import {useEffect, useState, type ImgHTMLAttributes} from 'react';
import {useRouteVisible} from './routeTransition';

/** Hidden route preparation must not start image downloads or wait for decoding. */
export function RouteImage({src, srcSet, loading = 'lazy', decoding = 'async', style, ...props}: ImgHTMLAttributes<HTMLImageElement>) {
  const visible = useRouteVisible();
  const [ready, setReady] = useState(false);
  useEffect(() => {
    if (!visible) { setReady(false); return; }
    let secondFrame: number | undefined;
    // Let the newly committed page paint before attaching image URLs.
    const firstFrame = requestAnimationFrame(() => {
      secondFrame = requestAnimationFrame(() => setReady(true));
    });
    return () => {
      cancelAnimationFrame(firstFrame);
      if (secondFrame !== undefined) cancelAnimationFrame(secondFrame);
    };
  }, [visible]);
  const load = visible && ready;
  return <img {...props} src={load ? src : undefined} srcSet={load ? srcSet : undefined}
    loading={loading} decoding={decoding}
    style={{...style, visibility: load ? style?.visibility : 'hidden'}} />;
}
