// The page's state: practice against three computer players (always c = 0) under the chosen rules, the analysis of
// single hands, the records and the settings. A port of the app's AppViewModel.kt (no battles).
import { Arranger } from './arranger.svelte';
import { parseCards, sameArrangement, shuffled, type Rows } from './cards';
import * as engine from './engine';
import type { Analysis, Score } from './engine';
import { t, ta, setLang, browserLang, type Lang } from './i18n.svelte';
import * as R from './rules';
import type { RuleSpec, Values } from './rules';
import * as store from './store';
import type { Rec } from './store';

export interface Model { name: string; path: string; builtIn: boolean }
export type Kind = 'NET' | 'MC' | 'INDEP';
export const KINDS: Kind[] = ['NET', 'MC', 'INDEP'];
export interface Seat { kind: Kind; model: string }
export type ColorStyle = 'TABLE' | 'MINIMAL';
export type DarkMode = 'SYSTEM' | 'LIGHT' | 'DARK';
export type TextSize = 'SMALL' | 'NORMAL' | 'LARGE';
export const TEXT_SCALE: Record<TextSize, number> = { SMALL: 0.88, NORMAL: 1, LARGE: 1.15 };
export type Screen = 'PLAY' | 'ANALYZE' | 'RECORDS' | 'SETTINGS';

const BUILT_IN_MODEL: Model = { name: 'run2_net10', path: engine.BUILT_IN, builtIn: true };
const LOADED = '/models/loaded/';

export class App {
  error = $state<string | null>(null);
  screen = $state<Screen>('PLAY');
  /** A page opened in the settings: the rules, the opponents or the networks. */
  settingsPage = $state<'RULES' | 'OPPONENTS' | 'NETWORKS' | null>(null);

  // ---- Look ----
  colorStyle = $state<ColorStyle>(store.get('look.colors', 'TABLE') as ColorStyle);
  darkMode = $state<DarkMode>(store.get('look.dark', 'SYSTEM') as DarkMode);
  textSize = $state<TextSize>(store.get('look.text', 'NORMAL') as TextSize);
  lang = $state<Lang>(store.get('lang', '') as Lang || browserLang());

  chooseColorStyle(v: ColorStyle) { this.colorStyle = v; store.set('look.colors', v); }
  chooseDarkMode(v: DarkMode) { this.darkMode = v; store.set('look.dark', v); }
  chooseTextSize(v: TextSize) { this.textSize = v; store.set('look.text', v); }
  chooseLang(v: Lang) { this.lang = v; setLang(v); store.set('lang', v); }

  private catching(p: Promise<unknown>) { p.catch((e) => { this.error = e?.message ?? String(e); }); }

  // ---- Rules ----
  ruleSpec = $state<RuleSpec>(R.parseSpec(store.get('rules', 'family')));
  ruleValues = $state<Values>({});
  presetValues = $state<Record<string, Values>>({});
  legacyModels = $state<Set<string>>(new Set());

  choosePreset(preset: string) { this.applyRules({ preset, overrides: {} }); }

  setRule(name: string, value: number) {
    const preset = this.presetValues[this.ruleSpec.preset];
    const overrides = { ...this.ruleSpec.overrides };
    if (preset?.[name] === value) delete overrides[name]; else overrides[name] = value;
    this.applyRules({ preset: this.ruleSpec.preset, overrides });
  }

  private applyRules(spec: RuleSpec) {
    this.catching(engine.ruleValues(R.specString(spec)).then((v) => {
      this.ruleValues = v;
      this.ruleSpec = spec;
      store.set('rules', R.specString(spec));
    }));
  }

  /** The rules page was left: under changed rules practice deals a new hand at once. */
  rulesClosed() {
    this.catching((async () => {
      if (this.gameRules && (await engine.rulesFull(R.specString(this.ruleSpec))) !== this.gameRules) await this.deal();
    })());
  }

  rulesName(v: Values): string {
    const p = R.presetOf(v, this.presetValues);
    return p ? ta('preset_names')[R.PRESETS.indexOf(p)] : t('custom');
  }

  /** Whether a legacy network would play these rules as if they were family. */
  legacyMismatch(v: Values): boolean {
    const family = this.presetValues.family;
    return !!family && Object.keys(v).length > 0 && !R.same(v, family);
  }

  private async loadRules() {
    const presets: Record<string, Values> = {};
    for (const p of R.PRESETS) presets[p] = await engine.ruleValues(p);
    this.presetValues = presets;
    try {
      this.ruleValues = await engine.ruleValues(R.specString(this.ruleSpec));
    } catch {
      this.ruleSpec = { preset: this.ruleSpec.preset, overrides: {} };
      store.set('rules', R.specString(this.ruleSpec));
      this.ruleValues = await engine.ruleValues(R.specString(this.ruleSpec));
    }
  }

  private async loadLegacy() {
    const out = new Set<string>();
    for (const m of this.models) if (await engine.isLegacy(m.path).catch(() => false)) out.add(m.path);
    this.legacyModels = out;
  }

  // ---- Play ----
  play = new Arranger((m) => { this.error = m; }, (row, free) => t('row_room', ta('row_names')[row], free));
  seats = $state<Seat[]>([]);
  waiting = $state(false);
  /** The finished hand: everyone's arrangement (0 is the player) and the score. */
  result = $state<{ rows: Rows[]; score: Score } | null>(null);
  /** The finished hand's pages: 0 the result, 1 the analysis of the player's hand. */
  resultTab = $state(0);
  gameAnalysis = $state<Analysis | null>(null);
  session = $state(this.readSession());
  gameRules = $state('');
  gameValues = $state<Values>({});
  /** The hand is one played again: neither in the totals nor in the records. */
  replaying = $state(false);
  records = new store.RecordStore('records');
  favorites = new store.RecordStore('favorites');
  recordCount = $state(this.records.count());
  favoriteIds = $state(this.favorites.ids());
  lastRecord = $state<Rec | null>(null);
  ready = $state(false);

  private hand: number[] = [];
  private others: number[][] = [];
  private opponents: Promise<Rows[]> | null = null;
  private dealtAt = 0;

  constructor() {
    setLang(this.lang);
    this.catching((async () => {
      await this.loadModels();
      await this.loadRules();
      await this.deal();
      this.ready = true;
      await this.loadLegacy();
    })());
  }

  private readSession(): { hands: number; totals: number[] } {
    return { hands: store.getNum('session.hands', 0), totals: [0, 1, 2, 3].map((i) => store.getNum(`session.total${i}`, 0)) };
  }

  /** Deals a new hand (`fixed`: the four hands, the player's first). */
  async deal(fixed?: number[][], again = false) {
    this.replaying = again;
    this.lastRecord = null;
    this.result = null;
    this.resultTab = 0;
    this.gameAnalysis = null;
    const hands = fixed ?? (await engine.deal());
    this.hand = hands[0];
    this.others = hands.slice(1);
    this.gameRules = await engine.rulesFull(R.specString(this.ruleSpec));
    this.gameValues = R.values(this.gameRules);
    this.play.reset(hands[0], this.gameRules);
    // Totals under other rules are on another scale: they start over.
    if (store.get('session.rules', '') !== this.gameRules) {
      if (store.get('session.rules', '')) this.writeSession(0, [0, 0, 0, 0]);
      store.set('session.rules', this.gameRules);
    }
    this.seats = [0, 1, 2].map((i) => this.seat(i));
    const specs = this.seats.map((s) => this.spec(s));
    const rules = this.gameRules;
    const others = this.others;
    this.opponents = Promise.all(specs.map((s, i) => engine.arrange(s, others[i], rules)));
    this.opponents.catch(() => {});
    this.dealtAt = Date.now();
  }

  dealNow() { this.catching(this.deal()); }

  private writeSession(hands: number, totals: number[]) {
    store.set('session.hands', String(hands));
    totals.forEach((x, i) => store.set(`session.total${i}`, String(x)));
    this.session = { hands, totals };
  }

  confirm() {
    this.catching((async () => {
      const ev = await this.play.eval();
      if (ev.valid !== true) { this.error = t(ev.valid === null ? 'fill_rows_first' : 'fouled'); return; }
      this.waiting = true;
      try {
        const mine = $state.snapshot(this.play.rows) as Rows;
        const all = [mine, ...(await this.opponents!)];
        const sc = await engine.score(all, this.gameRules);
        if (!this.replaying) {
          this.lastRecord = this.records.add(this.hand, mine, this.gameRules, Date.now() - this.dealtAt, sc.result[0]);
          this.recordCount = this.records.count();
          const s = this.session;
          this.writeSession(s.hands + 1, s.totals.map((x, i) => x + sc.result[i]));
        }
        this.result = { rows: all, score: sc };
      } finally {
        this.waiting = false;
      }
    })());
  }

  /** Shows the analysis of the player's hand, computing it the first time. */
  showGameAnalysis() {
    this.resultTab = 1;
    const r = this.result;
    if (!r || this.gameAnalysis) return;
    this.analyzing = true;
    this.catching(engine.analyze([...this.hand].sort((a, b) => a - b), this.gameRules, this.model(this.anaModel).path, [r.rows[0]])
      .then((a) => { if (this.result === r) this.gameAnalysis = a; })
      .finally(() => { this.analyzing = false; }));
  }

  /** The totals and the hand count start over, and so does practice: a new hand, the first. */
  resetSession() {
    this.writeSession(0, [0, 0, 0, 0]);
    this.dealNow();
  }

  /** Deletes the records; the totals and practice start over too. */
  clearRecords() {
    this.records.clear();
    this.recordCount = 0;
    this.resetSession();
  }

  toggleFavorite(rec: Rec) {
    this.favorites.toggle(rec);
    this.favoriteIds = this.favorites.ids();
  }

  /** The same 13 cards again: the finished hand with the same opponents' cards, or a record's with the rest anew. */
  replay(cards?: number[]) {
    const mine = cards ?? this.hand;
    const rest = cards ? chunk(shuffled([...Array(52).keys()].filter((c) => !mine.includes(c))), 13) : this.others;
    this.catching(this.deal([mine, ...rest], true));
    this.screen = 'PLAY';
  }

  // ---- Analyze ----
  anaHand = $state<Set<number>>(new Set());
  /** 0 the hand, 1 the candidates. */
  anaPage = $state(0);
  anaModel = $state(store.get('analyze.model', engine.BUILT_IN));
  anaMine = $state<Rows | null>(null);
  /** The rules of the hand analyzed when it comes from a game or record (full spec); else the rules page's. */
  anaRules = $state<string | null>(null);
  analysis = $state<Analysis | null>(null);
  analyzing = $state(false);

  get anaRuleValues(): Values { return this.anaRules ? R.values(this.anaRules) : this.ruleValues; }

  setAnalyzeHand(cards: Set<number>, mine: Rows | null = null, rules: string | null = null) {
    this.anaHand = cards;
    this.anaMine = mine;
    this.anaRules = rules;
    this.analysis = null;
    this.anaPage = 0;
  }

  toggleAnalyzeCard(c: number) {
    const s = new Set(this.anaHand);
    if (s.has(c)) s.delete(c); else if (s.size < 13) s.add(c); else return;
    this.setAnalyzeHand(s, null, this.anaRules);
  }

  useCurrentRules() { this.anaRules = null; this.analysis = null; }
  randomAnalyzeHand() { this.setAnalyzeHand(new Set(shuffled([...Array(52).keys()]).slice(0, 13))); }

  analyzeLastHand() {
    const r = this.result;
    if (r) this.setAnalyzeHand(new Set(r.rows[0].flat()), r.rows[0], this.gameRules);
  }

  analyzeRecord(rec: Rec) {
    this.setAnalyzeHand(new Set(parseCards(rec.hand)), ['front', 'middle', 'back'].map((k) => parseCards(rec[k])), rec.rules || 'family');
  }

  analyzeRecordOnScreen(rec: Rec) { this.analyzeRecord(rec); this.screen = 'ANALYZE'; }

  chooseAnalyzeModel(path: string) { this.anaModel = path; store.set('analyze.model', path); }

  analyze() {
    if (this.anaHand.size !== 13) { this.error = t('pick_13'); return; }
    this.analyzing = true;
    this.catching(engine.analyze([...this.anaHand].sort((a, b) => a - b), this.anaRules ?? R.specString(this.ruleSpec), this.model(this.anaModel).path,
      this.anaMine ? [$state.snapshot(this.anaMine) as Rows] : [])
      .then((a) => { this.analysis = a; this.anaPage = 1; })
      .finally(() => { this.analyzing = false; }));
  }

  // ---- Networks and opponents ----
  models = $state<Model[]>([BUILT_IN_MODEL]);

  model(path: string): Model { return this.models.find((m) => m.path === path) ?? this.models[0]; }

  private async loadModels() {
    const names = await store.modelNames().catch(() => [] as string[]);
    for (const n of names) {
      const data = await store.modelData(n);
      if (data) await engine.putFile(LOADED + n, data);
    }
    this.models = [BUILT_IN_MODEL, ...names.map((n) => ({ name: n.replace(/\.onnx$/, ''), path: LOADED + n, builtIn: false }))];
    // The seats and the analysis may name a loaded one, which is known only now.
    this.seatChoices = [0, 1, 2].map((i) => this.seat(i));
    this.anaModel = this.model(store.get('analyze.model', engine.BUILT_IN)).path;
  }

  /** Loads a network from a file; it is kept only if the engine can analyze a hand with it. */
  importModel(file: File) {
    this.catching((async () => {
      let name = file.name.replace(/[^A-Za-z0-9_.-]/g, '_');
      if (!name.endsWith('.onnx')) name += '.onnx';
      const data = await file.arrayBuffer();
      await engine.putFile(LOADED + name, data.slice(0));
      try {
        await engine.analyze([...Array(13).keys()].map((i) => i * 4), 'family', LOADED + name, []);
      } catch (e) {
        await engine.putFile(LOADED + name, null);
        throw new engine.EngineError(t('not_usable', (e as Error).message));
      }
      await store.putModel(name, data);
      this.models = [BUILT_IN_MODEL, ...(await store.modelNames()).map((n) => ({ name: n.replace(/\.onnx$/, ''), path: LOADED + n, builtIn: false }))];
      await this.loadLegacy();
    })());
  }

  deleteModel(m: Model) {
    if (m.builtIn) return;
    this.catching((async () => {
      const name = m.path.slice(LOADED.length);
      await store.deleteModel(name);
      await engine.putFile(m.path, null);
      this.models = this.models.filter((x) => x.path !== m.path);
      this.seatChoices = [0, 1, 2].map((i) => this.seat(i));
      if (this.anaModel === m.path) this.chooseAnalyzeModel(engine.BUILT_IN);
    })());
  }

  seat(i: number): Seat {
    const kind = (store.get(`seat${i}.kind`, 'NET') as Kind);
    return { kind: KINDS.includes(kind) ? kind : 'NET', model: this.model(store.get(`seat${i}.model`, '')).path };
  }

  seatChoices = $state<Seat[]>([0, 1, 2].map((i) => this.seat(i)));
  mcSamples = $state(store.getNum('mc.samples', 200));

  setSeat(i: number, s: Seat) {
    store.set(`seat${i}.kind`, s.kind);
    store.set(`seat${i}.model`, s.model);
    this.seatChoices = [0, 1, 2].map((k) => this.seat(k));
  }

  chooseMcSamples(n: number) { this.mcSamples = n; store.set('mc.samples', String(n)); }

  /** The engine's player spec (cpp/players/players.cpp) for a seat. */
  spec(s: Seat): string {
    if (s.kind === 'NET') return `net:model=${this.model(s.model).path}`;
    if (s.kind === 'MC') return `mc:samples=${this.mcSamples},opp=indep`;
    return 'indep';
  }

  seatName(s: Seat): string {
    if (s.kind === 'NET') return this.model(s.model).name;
    if (s.kind === 'MC') return `mc ${this.mcSamples}`;
    return 'indep';
  }
}

function chunk<T>(xs: T[], n: number): T[][] {
  const out: T[][] = [];
  for (let i = 0; i < xs.length; i += n) out.push(xs.slice(i, i + n));
  return out;
}

export { sameArrangement };
