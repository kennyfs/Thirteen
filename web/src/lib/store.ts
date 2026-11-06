// What the page keeps between visits, in this browser only: the settings and the running totals (localStorage), the
// records and the saved hands (localStorage, one JSON object per line, the app's record format), and the networks
// loaded from files (IndexedDB, as they are megabytes).
import { cardsString, type Rows } from './cards';

const PREFIX = 'thirteen.';

export function get(key: string, fallback: string): string {
  try { return localStorage.getItem(PREFIX + key) ?? fallback; } catch { return fallback; }
}
export function set(key: string, value: string) {
  try { localStorage.setItem(PREFIX + key, value); } catch { /* storage full or off */ }
}
export const getNum = (key: string, fallback: number) => {
  const x = Number(get(key, String(fallback)));
  return Number.isFinite(x) ? x : fallback;
};

export type Rec = Record<string, any>;

/** A list of records under `key`, oldest first. */
export class RecordStore {
  constructor(private key: string) {}

  all(): Rec[] {
    return get(this.key, '').split('\n').filter((l) => l.trim()).flatMap((l) => { try { return [JSON.parse(l)]; } catch { return []; } });
  }

  count(): number { return this.all().length; }

  private write(recs: Rec[]) { set(this.key, recs.map((r) => JSON.stringify(r) + '\n').join('')); }

  /** The record of a confirmed hand, as the app writes it (label "web", unless `extra` says otherwise). */
  add(hand: number[], rows: Rows, rules: string, timeMs: number, result: number, extra: Rec = {}): Rec {
    const rec: Rec = {
      version: 1, id: crypto.randomUUID(), hand: cardsString(hand), front: cardsString(rows[0]), middle: cardsString(rows[1]),
      back: cardsString(rows[2]), c: 0, rules, time_ms: timeMs, timestamp: new Date().toISOString(), label: 'web', result, ...extra,
    };
    this.write([...this.all(), rec]);
    return rec;
  }

  clear() { set(this.key, ''); }

  ids(): Set<string> { return new Set(this.all().map((r) => r.id)); }

  /** Saves the record, or drops it if it is saved; returns whether it is saved now. */
  toggle(rec: Rec): boolean {
    const all = this.all();
    const saved = !all.some((r) => r.id === rec.id);
    this.write(saved ? [...all, rec] : all.filter((r) => r.id !== rec.id));
    return saved;
  }

  /** The records as a file: JSONL. */
  text(): string { return get(this.key, ''); }
}

// ---- Networks ----

function db(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const r = indexedDB.open('thirteen', 1);
    r.onupgradeneeded = () => r.result.createObjectStore('models');
    r.onsuccess = () => resolve(r.result);
    r.onerror = () => reject(r.error);
  });
}

function tx<T>(mode: IDBTransactionMode, f: (s: IDBObjectStore) => IDBRequest<T>): Promise<T> {
  return db().then((d) => new Promise<T>((resolve, reject) => {
    const r = f(d.transaction('models', mode).objectStore('models'));
    r.onsuccess = () => resolve(r.result);
    r.onerror = () => reject(r.error);
  }));
}

/** The names of the loaded networks. */
export const modelNames = () => tx('readonly', (s) => s.getAllKeys()).then((k) => (k as string[]).sort());
export const modelData = (name: string) => tx<ArrayBuffer>('readonly', (s) => s.get(name));
export const putModel = (name: string, data: ArrayBuffer) => tx('readwrite', (s) => s.put(data, name));
export const deleteModel = (name: string) => tx('readwrite', (s) => s.delete(name));
