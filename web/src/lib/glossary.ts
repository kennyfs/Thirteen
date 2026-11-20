/**
 * The glossary (術語表; the app's Glossary.kt), in Taiwan Chinese only: its words are glossary_t_<id> and
 * glossary_d_<id> (android/.../values/glossary.xml). The entries are in an order where each definition uses only the
 * terms before it. `zh`: the phrases that link to the entry in running text (the bare 墩 only through its longer
 * forms, as it sits inside other terms).
 */
import { currentLang } from './i18n.svelte';

export interface GlossaryEntry { id: string; zh: string[] }

export const GLOSSARY: GlossaryEntry[] = [
  { id: 'row', zh: ['頭墩', '中墩', '尾墩', '三墩'] },
  { id: 'rank', zh: ['牌型', '同花順', '鐵支', '葫蘆', '順子', '三條', '兩對', '散牌'] },
  { id: 'wheel', zh: ['A-2-3-4-5'] },
  { id: 'foul', zh: ['倒水'] },
  { id: 'strict', zh: ['嚴格墩'] },
  { id: 'scoop', zh: ['打槍'] },
  { id: 'home_run', zh: ['全壘打'] },
  { id: 'front_trips', zh: ['衝三'] },
  { id: 'special', zh: ['特殊墩'] },
  { id: 'dragon', zh: ['一條龍'] },
  { id: 'network', zh: ['神經網路'] },
  { id: 'q', zh: ['期望得分', '期望值'] },
  { id: 'best', zh: ['首選', '最佳擺法'] },
  { id: 'style', zh: ['攻擊型', '防守型'] },
  { id: 'rows', zh: ['搶墩'] },
  { id: 'indep', zh: ['獨立墩'] },
  { id: 'mc', zh: ['蒙地卡羅'] },
];

export const GLOSSARY_INDEX: Map<string, number> = new Map(GLOSSARY.map((e, i) => [e.id, i]));

/** Whether the page's language has the glossary. */
export const hasGlossary = () => currentLang() === 'zh';

const esc = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const PAIRS = GLOSSARY.flatMap((e) => e.zh.map((p) => [p, e.id] as [string, string])).sort((a, b) => b[0].length - a[0].length);
const MAP = new Map(PAIRS);
const RE = new RegExp(PAIRS.map(([p]) => esc(p)).join('|'), 'g');

export interface TextPart { text: string; termId?: string }

/**
 * `text` in plain runs and links to the glossary's terms, the longest phrase first, none overlapping; none in a
 * language without the glossary. With `beforeId` (a definition's own), only the terms before that entry.
 */
export function linkGlossary(text: string, beforeId?: string): TextPart[] {
  if (!text || !hasGlossary()) return [{ text }];
  const limit = beforeId !== undefined ? (GLOSSARY_INDEX.get(beforeId) ?? -1) : Infinity;
  const out: TextPart[] = [];
  let at = 0;
  for (const hit of text.matchAll(RE)) {
    const id = MAP.get(hit[0]);
    if (!id || GLOSSARY_INDEX.get(id)! >= limit) continue;
    if (hit.index > at) out.push({ text: text.slice(at, hit.index) });
    out.push({ text: hit[0], termId: id });
    at = hit.index + hit[0].length;
  }
  if (at < text.length) out.push({ text: text.slice(at) });
  return out.length ? out : [{ text }];
}
