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
    // A computer's page: dragging, and the keys.
    hint_start_desk: 'Pick cards and tap a row, or drag cards into a row.',
    keys_title: 'Keys',
    keys_arrange: '1 2 3 put picked cards in a row · ← → and Space pick · S sorts · Z undoes · Delete takes all back · Enter plays',
    keys_result: 'Enter next hand · A analysis · F save · R rearrange',
    key_hint: '%1$s (%2$s)',
    show_result: 'Result',
    col_hand: 'Hand',
    col_total: 'Total',
    arrange_help_desk: '• Drag cards from your hand into a row, from one row to another, or back to the hand; dragging a picked card brings the other picked ones too.\n• Or click cards to pick them, then click a row (or press 1, 2 or 3).\n• Click a card in a row to take it back.\n• With all rows full and front ≤ middle ≤ back (no foul), press Play (Enter).\n• By rank / By suit (S) sorts your hand, to spot pairs, straights or flushes.',
  },
  zh: {
    records_info: '你打的每一局都記錄在這個瀏覽器裡：手牌、你的擺法、規則和花的時間，沒有其他資料，也不會傳到任何地方。把這個檔案（JSONL）寄給開發者，可以用來訓練更好的電腦對手。',
    hint_start_desk: '先選牌再點一墩，或直接把牌拖進一墩。',
    keys_title: '快捷鍵',
    keys_arrange: '1 2 3 把選的牌放進頭／中／尾墩 · ← → 與空白鍵選牌 · S 排序 · Z 復原 · Delete 全部收回 · Enter 出牌',
    keys_result: 'Enter 下一局 · A 分析 · F 收藏 · R 重排',
    key_hint: '%1$s（%2$s）',
    show_result: '結果',
    col_hand: '本局',
    col_total: '累計',
    arrange_help_desk: '• 把手牌拖進一墩，或在兩墩之間拖動，拖回手牌就收回；拖動已選的牌時，其他選了的牌會一起移動。\n• 也可以點選手牌，再點要放進的那一墩（或按 1、2、3）。\n• 點墩上的牌可收回。\n• 三墩放滿、而且頭墩 ≤ 中墩 ≤ 尾墩（沒有倒水），就可以按「出牌」（Enter）。\n• 「依點數／依花色」（S）切換手牌的排列方式，方便找對子、順子或同花。',
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
