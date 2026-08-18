import { Injectable, PLATFORM_ID, inject } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

interface CacheEntry<T> {
  storedAt: number;
  value: T;
}

/**
 * Short-lived browser cache for catalog responses.
 *
 * sessionStorage rather than an in-memory map on purpose: the point is that a
 * page *reload* does not refetch, and a reload throws away every JS object.
 * Scoped to the tab session, so it cannot go stale across days.
 *
 * Deliberately dumb — no revalidation, no ETags. Within the TTL the cached
 * answer is used as-is; past it the entry is ignored and refetched. Anything
 * that changes the catalog (posting or deleting a review) calls clear().
 */
@Injectable({ providedIn: 'root' })
export class UnitCacheService {
  private platformId = inject(PLATFORM_ID);

  /** Bump when the cached shape changes, so old entries are ignored not misread. */
  private readonly PREFIX = 'unit-cache:v1:';
  readonly TTL_MS = 60_000;
  /** A search box can generate a lot of distinct keys; keep the tail trimmed. */
  private readonly MAX_ENTRIES = 40;

  read<T>(key: string): T | null {
    const store = this.store();
    if (!store) {
      return null;
    }

    try {
      const raw = store.getItem(this.PREFIX + key);
      if (!raw) {
        return null;
      }
      const entry = JSON.parse(raw) as CacheEntry<T>;
      if (Date.now() - entry.storedAt > this.TTL_MS) {
        store.removeItem(this.PREFIX + key);
        return null;
      }
      return entry.value;
    } catch {
      // Unparseable entry from an older build — treat as a miss.
      return null;
    }
  }

  write<T>(key: string, value: T): void {
    const store = this.store();
    if (!store) {
      return;
    }

    const entry: CacheEntry<T> = { storedAt: Date.now(), value };
    try {
      this.pruneOldest(store);
      store.setItem(this.PREFIX + key, JSON.stringify(entry));
    } catch {
      // Out of quota, or storage is unavailable. Caching is an optimisation —
      // drop everything we own and carry on uncached rather than fail a render.
      this.clear();
    }
  }

  clear(): void {
    const store = this.store();
    if (!store) {
      return;
    }
    try {
      for (const key of this.ownKeys(store)) {
        store.removeItem(key);
      }
    } catch {
      // Nothing useful to do if storage is gone mid-flight.
    }
  }

  private pruneOldest(store: Storage): void {
    const keys = this.ownKeys(store);
    if (keys.length < this.MAX_ENTRIES) {
      return;
    }

    const byAge = keys
      .map(key => ({ key, storedAt: this.storedAt(store, key) }))
      .sort((a, b) => a.storedAt - b.storedAt);

    for (const { key } of byAge.slice(0, keys.length - this.MAX_ENTRIES + 1)) {
      store.removeItem(key);
    }
  }

  private storedAt(store: Storage, key: string): number {
    try {
      return (JSON.parse(store.getItem(key) ?? '{}') as CacheEntry<unknown>).storedAt ?? 0;
    } catch {
      return 0;
    }
  }

  private ownKeys(store: Storage): string[] {
    const keys: string[] = [];
    for (let i = 0; i < store.length; i++) {
      const key = store.key(i);
      if (key?.startsWith(this.PREFIX)) {
        keys.push(key);
      }
    }
    return keys;
  }

  /** Null during SSR, and in browsers where storage access throws (some privacy modes). */
  private store(): Storage | null {
    if (!isPlatformBrowser(this.platformId)) {
      return null;
    }
    try {
      return sessionStorage;
    } catch {
      return null;
    }
  }
}
