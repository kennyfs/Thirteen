/**
 * The glossary (術語表; the app's Glossary.kt): its words are glossary_t_<id> and glossary_d_<id> in each language's
 * android/.../values(-xx)/glossary.xml, and glossary_p_<id> the phrases that link to an entry in running text (in Chinese
 * the bare 墩 only through its longer forms, as it sits inside other terms). The entries are in an order where each
 * definition uses only the terms before it. A language shows it where its bool show_glossary is true (bools.xml).
 */
import { currentLang, t, ta, type Lang } from './i18n.svelte';

export interface GlossaryEntry { id: string }

export const GLOSSARY: GlossaryEntry[] = ['row', 'rank', 'wheel', 'foul', 'strict', 'scoop', 'home_run', 'front_trips', 'special', 'dragon',
  'network', 'q', 'best', 'style', 'rows', 'indep', 'mc'].map((id) => ({ id }));

export const GLOSSARY_INDEX: Map<string, number> = new Map(GLOSSARY.map((e, i) => [e.id, i]));

/** Whether the page's language has the glossary. */
export const hasGlossary = () => t('show_glossary') === 'true';

const esc = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
// A phrase that begins or ends with a Latin letter or a digit matches only where no other one is next to it (whole
// words); Chinese and Japanese ones anywhere, as their words run together. Case is ignored.
const EDGE = /[\p{Script=Latin}\p{N}]/u;

interface Linker { re: RegExp | null; map: Map<string, string> }
const linkers = new Map<Lang, Linker>();

function linker(): Linker {
  const lang = currentLang();
  let l = linkers.get(lang);
  if (!l) {
    const pairs = GLOSSARY.flatMap((e) => ta(`glossary_p_${e.id}`).filter((p) => p.trim()).map((p) => [p, e.id] as [string, string]))
      .sort((a, b) => b[0].length - a[0].length);
    const re = pairs.length ? new RegExp(pairs.map(([p]) =>
      (EDGE.test(p[0]) ? '(?<![\\p{Script=Latin}\\p{N}])' : '') + esc(p) + (EDGE.test(p[p.length - 1]) ? '(?![\\p{Script=Latin}\\p{N}])' : '')).join('|'), 'giu') : null;
    l = { re, map: new Map(pairs.map(([p, id]) => [p.toLowerCase(), id])) };
    linkers.set(lang, l);
  }
  return l;
}

export interface TextPart { text: string; termId?: string }

/**
 * `text` in plain runs and links to the glossary's terms, the longest phrase first, none overlapping; none in a
 * language without the glossary. With `beforeId` (a definition's own), only the terms before that entry.
 */
export function linkGlossary(text: string, beforeId?: string): TextPart[] {
  if (!text || !hasGlossary()) return [{ text }];
  const { re, map } = linker();
  if (!re) return [{ text }];
  const limit = beforeId !== undefined ? (GLOSSARY_INDEX.get(beforeId) ?? -1) : Infinity;
  const out: TextPart[] = [];
  let at = 0;
  for (const hit of text.matchAll(re)) {
    const id = map.get(hit[0].toLowerCase());
    if (!id || GLOSSARY_INDEX.get(id)! >= limit) continue;
    if (hit.index > at) out.push({ text: text.slice(at, hit.index) });
    out.push({ text: hit[0], termId: id });
    at = hit.index + hit[0].length;
  }
  if (at < text.length) out.push({ text: text.slice(at) });
  return out.length ? out : [{ text }];
}
