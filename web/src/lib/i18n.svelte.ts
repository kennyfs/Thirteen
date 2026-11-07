// The page's words: the Android app's strings (scripts/strings.mjs), in the browser's language unless the settings
// choose one. Those of the web page alone are here (en, zh), in desk-strings.ts (a computer's pages: en, zh) and in
// strings/<lang>.ts for the other languages (both kinds).
import all from '../generated/strings.json';
import { DESK } from './desk-strings';
import ja from './strings/ja';
import zhHK from './strings/zh-HK';
import zhCN from './strings/zh-CN';
import vi from './strings/vi';
import id from './strings/id';

/** zh is Traditional Chinese as in Taiwan (the first language, and the code the settings kept before the others). */
export type Lang = 'zh' | 'en' | 'ja' | 'zh-HK' | 'zh-CN' | 'vi' | 'id';
export type Table = Record<string, string | string[]>;
/** The languages in the settings' order, each by its own name; `tag` is the page's lang attribute (its fonts). */
export const LANGS: { id: Lang; name: string; tag: string }[] = [
  { id: 'zh', name: '繁體中文（台灣）', tag: 'zh-Hant-TW' },
  { id: 'en', name: 'English (US)', tag: 'en-US' },
  { id: 'ja', name: '日本語', tag: 'ja' },
  { id: 'zh-HK', name: '繁體中文（香港）', tag: 'zh-Hant-HK' },
  { id: 'zh-CN', name: '简体中文（中国）', tag: 'zh-Hans-CN' },
  { id: 'vi', name: 'Tiếng Việt', tag: 'vi' },
  { id: 'id', name: 'Bahasa Indonesia', tag: 'id' },
];
const app = all as unknown as Record<Lang, Table>;
// Where the page says other than the app: what is kept is kept in this browser.
const WEB: Record<'en' | 'zh', Table> = {
  en: {
    records_info: 'Every hand you play is recorded in this browser: the cards, your rows, the rules and the time you took. Nothing else, and nothing is sent anywhere. Sending the file (JSONL) to the developer helps train better computer players.',
    // One, where the app's string says "hands".
    n_recorded_one: '1 hand recorded',
    chart_summary_one: '1 hand · total %2$s · average %3$s a hand',
    chart_desc_one: 'A chart of your total score after 1 hand, ending at %2$s',
    clear_records_confirm_one: 'Delete the 1 recorded hand? The running scores and the hand count start over too, with a new practice hand. Export first if you need it.',
    // A computer's page: the keys.
    keys_title: 'Keys',
    keys_arrange: '1 2 3 put picked cards in a row · ← → and Space pick · S sorts · Z undoes · Delete takes all back · Enter plays',
    keys_result: 'Enter next hand · A analysis · F save · R rearrange',
    key_hint: '%1$s (%2$s)',
    show_result: 'Result',
    col_hand: 'Hand',
    col_total: 'Total',
    language: 'Language',
  },
  zh: {
    records_info: '你打的每一局都記錄在這個瀏覽器裡：手牌、你的擺法、規則和花的時間，沒有其他資料，也不會傳到任何地方。把這個檔案（JSONL）寄給開發者，可以用來訓練更好的電腦對手。',
    keys_title: '快捷鍵',
    keys_arrange: '1 2 3 把選的牌放進頭／中／尾墩 · ← → 與空白鍵選牌 · S 排序 · Z 復原 · Delete 全部收回 · Enter 出牌',
    keys_result: 'Enter 下一局 · A 分析 · F 收藏 · R 重排',
    key_hint: '%1$s（%2$s）',
    show_result: '結果',
    col_hand: '本局',
    col_total: '累計',
    language: '語言',
  },
};
const own: Record<Lang, Table> = { zh: { ...WEB.zh, ...DESK.zh }, en: { ...WEB.en, ...DESK.en }, ja, 'zh-HK': zhHK, 'zh-CN': zhCN, vi, id };
const tables = Object.fromEntries(LANGS.map((l) => [l.id, { ...app[l.id], ...own[l.id] }])) as Record<Lang, Table>;

export const isLang = (v: string): v is Lang => LANGS.some((l) => l.id === v);

/** The first of the browser's languages the page has; Chinese by its script or region. */
export function browserLang(): Lang {
  for (const raw of navigator.languages ?? [navigator.language]) {
    const l = raw.toLowerCase();
    if (l.startsWith('zh')) {
      if (/^zh-(hant-)?(hk|mo)\b/.test(l)) return 'zh-HK';
      if (/^zh-(hans|cn|sg|my)\b/.test(l)) return 'zh-CN';
      return 'zh';
    }
    for (const lang of ['en', 'ja', 'vi', 'id'] as const) if (l === lang || l.startsWith(lang + '-')) return lang;
    if (l === 'in' || l.startsWith('in-')) return 'id';
  }
  return 'en';
}

// A rune, so the page's words change at once with the language.
const state = $state({ lang: browserLang() });
export const setLang = (l: Lang) => { state.lang = l; document.documentElement.lang = LANGS.find((x) => x.id === l)!.tag; };
export const currentLang = () => state.lang;

/** A string by name, its %1$s / %2$d filled with `args`; `name`_one instead when the first is 1 and there is one. */
export function t(name: string, ...args: (string | number)[]): string {
  const one = args[0] === 1 ? tables[state.lang][`${name}_one`] : undefined;
  const s = one ?? tables[state.lang][name] ?? tables.en[name];
  if (typeof s !== 'string') return name;
  return s.replace(/%(\d+)\$[sd]/g, (_, i) => String(args[Number(i) - 1] ?? ''));
}

/** A computer's string: `name`_desk where this language has one (a mouse clicks where a finger taps), else `name`. */
export function td(name: string, ...args: (string | number)[]): string {
  return typeof tables[state.lang][`${name}_desk`] === 'string' ? t(`${name}_desk`, ...args) : t(name, ...args);
}

/** A string array by name. */
export function ta(name: string): string[] {
  const s = tables[state.lang][name] ?? tables.en[name];
  return Array.isArray(s) ? s : [];
}
