/** Short-lived, memory-only snapshots. Session is part of every key. */
type Snapshot = { path: string; data: any; savedAt: number; stale: boolean };
export class QueryCache {
  private entries = new Map<string, Snapshot>();
  readonly freshFor: number;
  readonly keepFor: number;
  readonly limit: number;
  constructor(freshFor = 30_000, keepFor = 300_000, limit = 80) {
    this.freshFor = freshFor; this.keepFor = keepFor; this.limit = limit;
  }
  get(key: string, now = Date.now()) {
    const entry = this.entries.get(key);
    if (!entry) return undefined;
    if (now - entry.savedAt > this.keepFor) { this.entries.delete(key); return undefined; }
    this.entries.delete(key); this.entries.set(key, entry);
    return entry;
  }
  fresh(entry: Snapshot, now = Date.now()) { return !entry.stale && now - entry.savedAt < this.freshFor; }
  put(key: string, path: string, data: unknown, now = Date.now()) {
    this.entries.delete(key);
    this.entries.set(key, {path, data, savedAt: now, stale: false});
    while (this.entries.size > this.limit) this.entries.delete(this.entries.keys().next().value!);
  }
  invalidate(prefixes: string[]) {
    for (const entry of this.entries.values()) if (prefixes.some(p => entry.path.split('?')[0] === p || entry.path.startsWith(p + '/') || entry.path.startsWith(p + '?'))) entry.stale = true;
  }
  clear() { this.entries.clear(); }
}
export const queryCache = new QueryCache();
export const queryKey = (session: string, path: string) => JSON.stringify([session, path]);
export function invalidateMutation(path: string) {
  const root = '/' + path.split('?')[0].split('/')[1];
  const related: Record<string, string[]> = {
    '/like': ['/pet', '/activity', '/daily', '/comment', '/feed', '/search'],
    '/comment': ['/comment', '/pet', '/activity', '/daily', '/feed'],
    '/follow': ['/user', '/following', '/follower', '/feed'],
    '/admin': ['/admin', '/pet', '/activity', '/daily', '/user', '/feed', '/search'],
    '/pet': ['/pet', '/feed', '/search', '/admin'],
    '/activity': ['/activity', '/feed', '/search', '/admin'],
    '/daily': ['/daily', '/feed', '/search', '/admin'],
    '/notice': ['/notice', '/message'],
    '/avatar': ['/avatar', '/user'],
  };
  queryCache.invalidate(related[root] || [root]);
}
