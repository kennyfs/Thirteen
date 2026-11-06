// The page's words: the Android app's strings (scripts/strings.mjs), in the browser's language (Traditional Chinese
// for any Chinese, else English) unless the settings choose one.
import all from '../generated/strings.json';

export type Lang = 'en' | 'zh';
type Table = Record<string, string | string[]>;
const app = all as unknown as Record<Lang, Table>;
// Where the page says other than the app: what is kept is kept in this browser.
const WEB: Record<Lang, Table> = {
  en: {
    records_info: 'Every hand you play is recorded in this browser: the cards, your rows, the rules and the time you took. Nothing else, and nothing is sent anywhere. Sending the file (JSONL) to the developer helps train better computer players.',
  },
  zh: {
    records_info: '你打的每一局都記錄在這個瀏覽器裡：手牌、你的擺法、規則和花的時間，沒有其他資料，也不會傳到任何地方。把這個檔案（JSONL）寄給開發者，可以用來訓練更好的電腦對手。',
  },
};
const tables: Record<Lang, Table> = { en: { ...app.en, ...WEB.en }, zh: { ...app.zh, ...WEB.zh } };

export function browserLang(): Lang {
  return (navigator.languages ?? [navigator.language]).some((l) => l.toLowerCase().startsWith('zh')) ? 'zh' : 'en';
}

// A rune, so the page's words change at once with the language.
const state = $state({ lang: browserLang() });
export const setLang = (l: Lang) => { state.lang = l; document.documentElement.lang = l === 'zh' ? 'zh-Hant-TW' : 'en'; };
export const currentLang = () => state.lang;

/** A string by name, its %1$s / %2$d filled with `args`. */
export function t(name: string, ...args: (string | number)[]): string {
  const s = tables[state.lang][name] ?? tables.en[name];
  if (typeof s !== 'string') return name;
  return s.replace(/%(\d+)\$[sd]/g, (_, i) => String(args[Number(i) - 1] ?? ''));
}

/** A string array by name. */
export function ta(name: string): string[] {
  const s = tables[state.lang][name] ?? tables.en[name];
  return Array.isArray(s) ? s : [];
}
