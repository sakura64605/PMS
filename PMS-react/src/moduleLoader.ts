/** Share in-flight code downloads; a failed warm-up must remain retryable. */
export function moduleLoader<T>(load: () => Promise<T>) {
  let pending: Promise<T> | undefined;
  return () => pending ??= load().catch(error => { pending = undefined; throw error; });
}
