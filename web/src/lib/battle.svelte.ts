// Battles online: a room on a server (docs/battle.md), reached over a WebSocket, with the app's players too. A port of
// the app's BattleViewModel.kt and BattleReview.kt without the nearby rooms (a browser cannot reach phones nearby).
// The room decides everything; this keeps what it has said and sends the player's choices.
import { Arranger } from './arranger.svelte';
import type { Rows } from './cards';
import * as engine from './engine';
import type { Analysis, Score } from './engine';
import { t, ta } from './i18n.svelte';
import type { RuleSpec } from './rules';
import * as store from './store';

// ---- The protocol (protocol/src/main/kotlin/.../Messages.kt): JSON, the type in "t" ----

export const PROTOCOL_VERSION = 2;
/** The project's public server. */
export const DEFAULT_SERVER = 'thirteen.1li.tw';
export const TIME_LIMITS = [0, 30, 60, 120, 180, 300];
export const HAND_CHOICES = [1, 3, 5, 10, 15, 20, 30];
export const NAME_LENGTH = 16;

export type Mode = 'PUZZLE' | 'GAME';
export type Phase = 'LOBBY' | 'PLAYING' | 'REVIEW' | 'OVER';
export interface RuleChoice { preset: string; overrides: Record<string, number> }
export interface RoomSettings { mode: Mode; hands: number; rules: RuleChoice; timeLimit: number }
export interface PlayerView { id: number; name: string; bot: boolean; connected: boolean; submitted: boolean; ready: boolean; total: number }
export interface RoomView {
  pin: string; settings: RoomSettings; rules: string; phase: Phase; hand: number; host: number; players: PlayerView[];
  remainingMs?: number | null; backup?: number; epoch?: number;
}
export interface Play { player: number; rows: Rows; timedOut?: boolean }
export interface TableScore { result: number[]; points: number[]; scoops: boolean[][]; homeRun: boolean[]; pairs: number[][] }
export interface HandOutcome { index: number; plays: Play[]; score?: TableScore | null; best?: Rows | null; losses?: number[] | null }

type ServerMsg =
  | { t: 'welcome'; version: number }
  | { t: 'joined'; room: string; you: number; token: string }
  | { t: 'state'; room: RoomView }
  | { t: 'deal'; hand: number; cards: number[]; remainingMs?: number | null; mine?: Rows | null }
  | { t: 'result'; outcome: HandOutcome }
  | { t: 'history'; outcomes: HandOutcome[] }
  | { t: 'error'; code: string; message?: string }
  | { t: 'backup' }
  | { t: 'closed'; reason?: string };
type ClientMsg = Record<string, unknown> & { t: string };

/** The WebSocket address for what the player typed: a name ("thirteen.example.com"), or a full ws(s):// or http(s):// one. */
export function serverUrl(input: string): string | null {
  let s = input.trim();
  if (!s || /\s/.test(s)) return null;
  if (s.startsWith('https://')) s = 'wss://' + s.slice(8);
  else if (s.startsWith('http://')) s = 'ws://' + s.slice(7);
  // A name alone: TLS, but not on this machine (a server run here, or reached through an SSH tunnel).
  else if (!s.startsWith('wss://') && !s.startsWith('ws://')) s = (/^(localhost|127\.0\.0\.1|\[::1\])(:|\/|$)/.test(s) ? 'ws://' : 'wss://') + s;
  const rest = s.slice(s.indexOf('://') + 3);
  const host = rest.split('/')[0].replace(/^\[/, '').split(/[\]:]/)[0];
  if (!host) return null;
  // Without TLS only on this machine.
  if (s.startsWith('ws://') && !['localhost', '127.0.0.1', '[::1]', '::1'].includes(host)) return null;
  return rest.includes('/') ? s : s + '/ws';
}

const isPuzzle = (r: RoomView) => r.settings.mode === 'PUZZLE';

/** Players by standing: the least loss first in a puzzle, the most points at a table. */
export function standings(r: RoomView): PlayerView[] {
  return [...r.players].sort((a, b) => (isPuzzle(r) ? a.total - b.total : b.total - a.total));
}

/** Rank by standing, equal totals sharing a rank. */
export function rankOf(r: RoomView, p: PlayerView): number {
  return 1 + r.players.filter((x) => (isPuzzle(r) ? x.total < p.total - 1e-9 : x.total > p.total + 1e-9)).length;
}

/**
 * A battle in the records (the app's BattleRecord): kept as it goes on, from its first closed hand, so that its results
 * can be looked at again from the records.
 */
export interface BattleRecord {
  id: string; startedAt: number; via: string; settings: RoomSettings; rules: string; me: number; players: PlayerView[];
  outcomes: HandOutcome[]; left?: boolean;
}

/** The room as a battle record's last state showed it, for the result pages. */
export function recordView(b: BattleRecord): RoomView {
  return { pin: '', settings: b.settings, rules: b.rules, phase: 'OVER', hand: b.outcomes[b.outcomes.length - 1]?.index ?? -1, host: -1, players: b.players };
}

/** The player's place in a battle record, or null. */
export function recordPlace(b: BattleRecord): number | null {
  const mine = b.players.find((p) => p.id === b.me);
  return mine ? rankOf(recordView(b), mine) : null;
}

export const fmtLoss = (x: number) => x.toFixed(3);

/** A hand's value for player `i` of it: a puzzle's loss ("0.144", nothing lost "0"), or a table's points. */
export function handNumber(o: HandOutcome, i: number): number | null {
  return o.losses?.[i] ?? o.score?.result[i] ?? null;
}

/**
 * A battle's closed hands as the result pages show them (a port of BattleReview.kt): the hands, what the pages need
 * worked out from them, and the hand and player opened for a closer look with their analysis (on this computer, with
 * the built-in network).
 */
export class Review {
  me = $state(-1);
  rules = '';
  names: Record<number, string> = {};
  outcomes = $state<HandOutcome[]>([]);
  /** Each play's row categories, by its rows (sorted within each row). */
  categories = $state<Record<string, number[]>>({});
  /** A table game's hands from the player's seat: the plays' order (theirs first) and the full score in it. */
  tables = $state<Record<number, { order: number[]; score: Score }>>({});
  /**
   * The hands whose page has all it needs (a table game's score from the player's seat, every play's row categories),
   * worked out or failed: a hand is shown once it is, all at once, rather than its plain list first, the table after.
   */
  ready = $state<Record<number, true>>({});
  /** The work for each hand, and the hand's content it was for (a hand replaced by another is worked out again). */
  private preparing = new Map<number, { job: Promise<void>; content: string }>();
  /** Counts the clears, so that work for a battle cleared away writes nothing into the next. */
  private generation = 0;
  detail = $state<number | null>(null);
  analyzed = $state<number | null>(null);
  detailAnalysis = $state<Analysis | null>(null);
  detailTags = $state<Record<number, string>>({});
  /** Opened from a hand's result while the room waits: Back goes back to it, and the next hand closes it. */
  fromReview = $state(false);

  constructor(private onError: (m: string) => void) {}

  /** Adds or replaces hands (by index); returns the ones new to it. */
  add(list: HandOutcome[]): HandOutcome[] {
    const known = new Map(this.outcomes.map((o) => [o.index, o]));
    const fresh = list.filter((o) => !known.has(o.index));
    for (const o of list) known.set(o.index, o);
    this.outcomes = [...known.values()].sort((a, b) => a.index - b.index);
    (async () => { for (const o of list) await this.prepare(o); })();
    return fresh;
  }

  /** Whether a hand's page has all it needs (see `ready`). */
  isReady(index: number): boolean { return !!this.ready[index]; }

  /** Works out what a hand's page needs, once a hand; then it is ready. Without the rules there is nothing to work out. */
  private prepare(o: HandOutcome): Promise<void> {
    const content = JSON.stringify([o.plays.map((p) => [p.player, p.rows]), o.best ?? null, !!o.score]);
    const known = this.preparing.get(o.index);
    if (known && known.content === content) return known.job;
    const rules = this.rules;
    const gen = this.generation;
    const job = (async () => {
      if (known) await known.job;
      if (rules && o.score && !this.tables[o.index]) {
        const mine = o.plays.findIndex((p) => p.player === this.me);
        const order = mine < 0 ? o.plays.map((_, i) => i) : [mine, ...o.plays.map((_, i) => i).filter((i) => i !== mine)];
        try {
          const score = await engine.score(order.map((i) => o.plays[i].rows), rules);
          if (gen !== this.generation) return;
          this.tables = { ...this.tables, [o.index]: { order, score } };
        } catch { /* the page shows the plain list */ }
      }
      if (rules) for (const rows of [...o.plays.map((p) => p.rows), ...(o.best ? [o.best] : [])]) {
        const key = keyOf(rows);
        if (this.categories[key]) continue;
        try {
          const { categories } = await engine.rows(rows, rules);
          if (gen !== this.generation) return;
          this.categories = { ...this.categories, [key]: categories };
        } catch { /* untinted */ }
      }
      if (gen === this.generation) this.ready = { ...this.ready, [o.index]: true };
    })();
    this.preparing.set(o.index, { job, content });
    return job;
  }

  categoriesOf(rows: Rows): number[] | null { return this.categories[keyOf(rows)] ?? null; }

  clear() {
    this.me = -1;
    this.rules = '';
    this.names = {};
    this.outcomes = [];
    this.categories = {};
    this.tables = {};
    this.ready = {};
    this.preparing.clear();
    this.generation++;
    this.closeDetail();
  }

  openDetail(index: number) {
    const o = this.outcomes.find((x) => x.index === index);
    if (!o) return;
    // Its turn now, not after the hands before it.
    void this.prepare(o);
    this.detail = index;
    this.fromReview = false;
    this.closeAnalysis();
  }

  analyzeFromResult(index: number, player: number) {
    this.openDetail(index);
    if (this.detail !== index) return;
    this.fromReview = true;
    this.openAnalysis(player);
  }

  /** Analyzes one player's hand of the opened hand, their play pinned, the others' plays of the same cards marked. */
  openAnalysis(player: number) {
    const index = this.detail;
    const o = this.outcomes.find((x) => x.index === index);
    const play = o?.plays.find((p) => p.player === player);
    if (index === null || !o || !play) return;
    const rules = this.rules;
    const names = this.names;
    this.analyzed = player;
    this.detailAnalysis = null;
    this.detailTags = {};
    (async () => {
      try {
        const hand = play.rows.flat().sort((a, b) => a - b);
        const set = new Set(hand);
        // In a puzzle everyone set these cards; at a table the others had other hands.
        const same = o.plays.filter((p) => p.rows.flat().length === 13 && p.rows.flat().every((c) => set.has(c)));
        const a = await engine.analyze(hand, rules, engine.BUILT_IN, same.map((p) => p.rows));
        const tags: Record<number, string[]> = {};
        for (const p of same) {
          if (p.player === player) continue;
          const keys = (await engine.rows(p.rows, rules)).keys;
          const i = a.cands.findIndex((c) => c.info.keys.every((k, r) => k === keys[r]));
          if (i >= 0) (tags[i] ??= []).push(names[p.player] ?? '?');
        }
        if (this.detail === index && this.analyzed === player) {
          this.detailAnalysis = a;
          this.detailTags = Object.fromEntries(Object.entries(tags).map(([k, v]) => [k, v.join(', ')]));
        }
      } catch (e) {
        this.onError((e as Error).message ?? String(e));
      }
    })();
  }

  closeAnalysis() {
    this.analyzed = null;
    this.detailAnalysis = null;
    this.detailTags = {};
  }

  closeDetail() {
    this.detail = null;
    this.fromReview = false;
    this.closeAnalysis();
  }
}

const keyOf = (rows: Rows) => JSON.stringify(rows.map((r) => [...r].sort((a, b) => a - b)));

export class Battle {
  name = $state(store.get('battle.name', ''));
  /** The name of a player who gives none: "Player" and a number of their own. */
  readonly defaultName: string;
  server = $state(store.get('battle.server', '') || DEFAULT_SERVER);
  pin = $state('');
  /** Connecting or waiting for the room's answer. */
  busy = $state(false);

  room = $state<RoomView | null>(null);
  /** When `room` arrived, for its countdown. */
  roomAt = $state(0);
  me = $state(-1);
  deal = $state<{ hand: number; cards: number[] } | null>(null);
  review: Review;
  submitting = $state(false);
  /** The room is gone but its results stay to look at. */
  finished = $state(false);
  /** The last hand's table has been shown (it comes before the final standings). */
  lastSeen = $state(false);
  arranger: Arranger;

  private ws: WebSocket | null = null;
  private joined: { room: string; you: number; token: string } | null = null;
  /** Sent once the room says welcome: create, join or resume. */
  private pending: ClientMsg | null = null;
  private reconnecting = false;
  private battleId: string | null = null;
  private battleStart = 0;
  /** A battle of the records shown (openRecord), by its id: no room behind it. */
  opened = $state<string | null>(null);
  openedAt = $state(0);

  constructor(private onError: (m: string) => void, private ruleSpec: () => RuleSpec, private onRecord: () => void) {
    let d = store.get('battle.default_name', '');
    if (!d) { d = 'Player' + (10000 + Math.floor(Math.random() * 90000)); store.set('battle.default_name', d); }
    this.defaultName = d;
    this.review = new Review(onError);
    this.arranger = new Arranger(onError, (row, free) => t('row_room', ta('row_names')[row], free));
    // A reload in a room: its seat is taken back.
    const saved = loadSeat();
    if (saved) {
      this.joined = { room: saved.room, you: saved.you, token: saved.token };
      this.battleId = saved.battle;
      this.resumeUrl = saved.url;
      this.pending = { t: 'resume', room: saved.room, token: saved.token };
      this.open(saved.url);
    }
  }

  /** The address of the room's server, as connected to. */
  private resumeUrl: string | null = null;

  get outcomes() { return this.review.outcomes; }
  get playerName() { return this.name.trim() || this.defaultName; }
  /** In a room still going (the lobby too). */
  get inRoom() { return this.room !== null && !this.finished && this.room.phase !== 'OVER'; }
  /** The other screens are out of reach (no bar or rail): in the room, and until the last hand's result is seen. */
  get immersive() {
    const r = this.room;
    return this.inRoom || (r !== null && !this.lastSeen && this.outcomes.length > 0 && (this.finished || r.phase === 'OVER'));
  }
  get over() { return this.room !== null && (this.finished || this.room.phase === 'OVER'); }
  get mine(): PlayerView | null { return this.room?.players.find((p) => p.id === this.me) ?? null; }

  /** New rooms start with the rules page's rules and the last hands and time chosen. */
  defaults(mode: Mode): RoomSettings {
    return { mode, hands: store.getNum('battle.hands', 10), rules: this.rulesFromPage(), timeLimit: store.getNum('battle.time', 0) };
  }

  rulesFromPage(): RuleChoice {
    const s = this.ruleSpec();
    return { preset: s.preset, overrides: { ...s.overrides } };
  }

  // ---- Into a room ----

  create(mode: Mode) { this.start({ t: 'create', settings: this.defaults(mode) }); }

  join() {
    if (this.busy || this.inRoom) return;
    if (!/^\d{6}$/.test(this.pin)) return this.onError(t('battle_pin_bad'));
    this.start({ t: 'join', pin: this.pin });
  }

  private start(msg: ClientMsg) {
    store.set('battle.name', this.name);
    store.set('battle.server', this.server === DEFAULT_SERVER ? '' : this.server);
    this.clear();
    const url = serverUrl(this.server);
    if (!url) return this.onError(t('battle_server_bad'));
    this.resumeUrl = url;
    this.pending = msg;
    this.open(url);
  }

  private open(url: string) {
    this.busy = true;
    let ws: WebSocket;
    try { ws = new WebSocket(url); } catch (e) { this.busy = false; return this.onError(t('battle_no_connection', (e as Error).message)); }
    this.ws = ws;
    ws.onopen = () => this.sendOn(ws, { t: 'hello', version: PROTOCOL_VERSION, name: this.playerName });
    ws.onmessage = (e) => {
      let msg: ServerMsg;
      try { msg = JSON.parse(String(e.data)); } catch { return; }
      this.onMessage(ws, msg);
    };
    ws.onclose = (e) => this.onDown(ws, e.reason || (e.code === 1006 ? 'no answer' : String(e.code)));
  }

  private sendOn(ws: WebSocket | null, msg: ClientMsg) {
    if (ws && ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(msg));
  }

  private send(msg: ClientMsg) { this.sendOn(this.ws, msg); }

  // ---- In the room ----

  changeSettings(s: RoomSettings) {
    store.set('battle.hands', String(s.hands));
    store.set('battle.time', String(s.timeLimit));
    this.send({ t: 'settings', settings: s });
  }

  startGame() { this.send({ t: 'start' }); }

  async submit() {
    const d = this.deal;
    if (!d) return;
    const ev = await this.arranger.eval();
    if (ev.valid !== true) return this.onError(t(ev.valid === null ? 'fill_rows_first' : 'fouled'));
    this.submitting = true;
    this.send({ t: 'submit', hand: d.hand, rows: $state.snapshot(this.arranger.rows) });
  }

  ready() { if (this.room) this.send({ t: 'ready', hand: this.room.hand }); }

  /** Leaves the room (for good) and the results. */
  leave() {
    this.saveBattle(true);
    this.send({ t: 'leave' });
    const ws = this.ws;
    this.ws = null;
    // The Leave goes out before the close.
    setTimeout(() => ws?.close(), 300);
    this.clear();
  }

  /** Back from the results of a finished room. */
  dismiss() { this.clear(); }

  analyzeHand(index: number, player: number) { this.review.analyzeFromResult(index, player); }

  /** The last hand's table was seen; the final standings come next. */
  toSummary() { this.lastSeen = true; }

  /** Shows a battle of the records: its standings, hands and analyses, as when it ended. */
  openRecord(rec: BattleRecord): boolean {
    if (this.inRoom || this.busy) return false;
    this.clear();
    this.room = recordView(rec);
    this.me = rec.me;
    this.review.me = rec.me;
    this.review.rules = rec.rules;
    this.review.names = Object.fromEntries(rec.players.map((p) => [p.id, p.name]));
    this.review.add(rec.outcomes);
    this.finished = true;
    this.lastSeen = true;
    this.opened = rec.id;
    this.openedAt = rec.startedAt;
    return true;
  }

  private clear() {
    this.opened = null;
    saveSeat(null);
    this.reconnecting = false;
    const ws = this.ws;
    this.ws = null;
    ws?.close();
    this.joined = null;
    this.pending = null;
    this.room = null;
    this.me = -1;
    this.deal = null;
    this.review.clear();
    this.battleId = null;
    this.lastSeen = false;
    this.submitting = false;
    this.finished = false;
    this.busy = false;
  }

  // ---- What the room says ----

  private onMessage(ws: WebSocket, msg: ServerMsg) {
    if (ws !== this.ws) return;
    switch (msg.t) {
      case 'welcome':
        if (this.pending) this.sendOn(ws, this.pending);
        break;
      case 'joined':
        this.joined = { room: msg.room, you: msg.you, token: msg.token };
        this.me = msg.you;
        this.review.me = msg.you;
        this.busy = false;
        this.reconnecting = false;
        // A resume tells everything again.
        this.pending = { t: 'resume', room: msg.room, token: msg.token };
        saveSeat({ url: this.resumeUrl ?? '', room: msg.room, you: msg.you, token: msg.token, battle: this.battleId });
        break;
      case 'state': {
        const previous = this.room;
        this.room = msg.room;
        this.review.rules = msg.room.rules;
        this.review.names = Object.fromEntries(msg.room.players.map((p) => [p.id, p.name]));
        if (msg.room.phase !== 'LOBBY' && !this.battleId) {
          this.battleId = crypto.randomUUID();
          this.battleStart = Date.now();
          if (this.joined) saveSeat({ url: this.resumeUrl ?? '', ...this.joined, battle: this.battleId });
        }
        if (msg.room.phase === 'OVER') saveSeat(null);
        this.roomAt = Date.now();
        if (msg.room.players.find((p) => p.id === this.me)?.submitted) this.submitting = false;
        if (previous?.hand !== msg.room.hand && msg.room.phase === 'PLAYING') {
          this.submitting = false;
          if (this.review.fromReview) this.review.closeDetail();
        }
        break;
      }
      case 'deal':
        this.deal = { hand: msg.hand, cards: msg.cards };
        this.submitting = false;
        this.arranger.reset(msg.cards, this.room?.rules ?? 'family', msg.mine ?? [[], [], []]);
        break;
      case 'result':
        this.addOutcomes([msg.outcome]);
        break;
      case 'history':
        this.addOutcomes(msg.outcomes);
        break;
      case 'error': {
        this.busy = false;
        this.submitting = false;
        // A seat that cannot be taken back: the room is gone (or it went on without the player).
        if (this.joined && msg.code === 'NO_ROOM' && this.pending?.t === 'resume' && (this.reconnecting || !this.room)) {
          this.reconnecting = false;
          this.busy = false;
          if (!this.room) { this.clear(); return this.onError(t('battle_closed')); }
          return this.roomGone(t('battle_closed'));
        }
        this.onError(errorText(msg.code, msg.message ?? ''));
        if (!this.joined && ['VERSION', 'NO_ROOM', 'ROOM_FULL', 'STARTED', 'TOO_MANY', 'BUSY', 'RATE'].includes(msg.code)) {
          this.ws = null;
          ws.close();
        }
        break;
      }
      case 'closed':
        if (msg.reason === 'resumed elsewhere' || msg.reason === 'left') return;
        this.roomGone(this.room?.phase === 'OVER' ? null : t('battle_closed'));
        break;
    }
  }

  private onDown(ws: WebSocket, reason: string) {
    if (ws !== this.ws) return;
    this.ws = null;
    const j = this.joined;
    if (!j || this.room?.phase === 'OVER') {
      if (!j) {
        this.busy = false;
        this.onError(t('battle_no_connection', reason));
      }
      return this.roomGone(null);
    }
    // A dropped connection in a game: the seat is kept, so try to take it back.
    if (this.reconnecting) return;
    this.reconnecting = true;
    this.busy = true;
    const url = this.resumeUrl ?? serverUrl(this.server);
    (async () => {
      for (let attempt = 0; attempt < 6 && url; attempt++) {
        await sleep(1000 * 2 ** attempt);
        if (!this.reconnecting) return;
        this.pending = { t: 'resume', room: j.room, token: j.token };
        this.open(url);
        const mine = this.ws;
        await sleep(8000);
        if (!this.reconnecting) return;
        if (this.ws === mine) { this.ws = null; mine?.close(); }
      }
      this.reconnecting = false;
      this.busy = false;
      this.roomGone(t('battle_lost'));
    })();
  }

  /** The room is gone; what it showed stays when anything was played. */
  private roomGone(message: string | null) {
    saveSeat(null);
    if (message) this.onError(message);
    const ws = this.ws;
    this.ws = null;
    ws?.close();
    if (this.outcomes.length === 0) this.clear();
    else this.finished = true;
  }

  private addOutcomes(list: HandOutcome[]) {
    for (const o of this.review.add(list)) this.record(o);
    this.saveBattle();
  }

  /** The battle as it stands, to the records, once it has a closed hand. */
  private saveBattle(left = false) {
    const r = this.room;
    if (!this.battleId || !r || this.outcomes.length === 0) return;
    const battles = new store.RecordStore('battles');
    // After a reload the start is the one kept.
    if (!this.battleStart) this.battleStart = battles.all().find((b) => b.id === this.battleId)?.startedAt ?? Date.now();
    const rec: BattleRecord = {
      id: this.battleId, startedAt: this.battleStart, via: 'online', settings: $state.snapshot(r.settings), rules: r.rules,
      me: this.me, players: $state.snapshot(r.players), outcomes: $state.snapshot(this.outcomes), left,
    };
    battles.put(rec);
    this.onRecord();
  }

  /** The player's play of a closed hand goes to the records, labelled "battle" (a table game) or "puzzle". */
  private record(o: HandOutcome) {
    const r = this.room;
    if (!r) return;
    const i = o.plays.findIndex((p) => p.player === this.me);
    if (i < 0) return;
    const result = o.score ? o.score.result[i] : -(o.losses?.[i] ?? 0);
    const records = new store.RecordStore('records');
    // Told again after a reload.
    if (records.all().some((x) => x.battle === this.battleId && x.battle_hand === o.index)) return;
    records.add(o.plays[i].rows.flat(), o.plays[i].rows, r.rules, 0, result,
      { label: isPuzzle(r) ? 'puzzle' : 'battle', battle: this.battleId, battle_hand: o.index });
    this.onRecord();
  }
}

/** The seat in a room, kept in this tab (sessionStorage) for a reload. */
interface Seat { url: string; room: string; you: number; token: string; battle: string | null }
const SEAT_KEY = 'thirteen.battle.seat';

function saveSeat(s: Seat | null) {
  try { if (s) sessionStorage.setItem(SEAT_KEY, JSON.stringify(s)); else sessionStorage.removeItem(SEAT_KEY); } catch { /* off */ }
}

function loadSeat(): Seat | null {
  try {
    const s = JSON.parse(sessionStorage.getItem(SEAT_KEY) ?? 'null');
    return s && typeof s.url === 'string' && s.url && typeof s.room === 'string' && typeof s.token === 'string' ? s : null;
  } catch { return null; }
}

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

function errorText(code: string, message: string): string {
  switch (code) {
    case 'VERSION': return t('battle_err_version');
    case 'NO_ROOM': return t('battle_err_no_room');
    case 'ROOM_FULL': return t('battle_err_full');
    case 'STARTED': return t('battle_err_started');
    case 'TOO_MANY': case 'RATE': return t('battle_err_rate');
    case 'BUSY': return t('battle_err_busy');
    case 'BAD_SETTINGS': return t('battle_err_settings');
    case 'BAD_ROWS': return t(message === 'fouled' ? 'fouled' : 'battle_err_rows');
    default: return t('battle_err_not_allowed');
  }
}
