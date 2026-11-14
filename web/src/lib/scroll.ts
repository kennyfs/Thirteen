// Keeping a chosen row of a list in sight without the list jumping at each step (Mahjong-TW's scroll.ts).

/** The nearest ancestor of `el` that scrolls up and down. */
function scroller(el: HTMLElement): HTMLElement | null {
  for (let p = el.parentElement; p; p = p.parentElement) {
    const y = getComputedStyle(p).overflowY;
    if ((y === 'auto' || y === 'scroll') && p.scrollHeight > p.clientHeight) return p;
  }
  return null;
}

/**
 * Keeps `row` between 20% and 80% of its list's height: the list scrolls (smoothly, unless the reader asks for less
 * motion) only when the row leaves that band, and then just enough to bring it back to the band's edge.
 */
export function keepInBand(row: HTMLElement, lo = 0.2, hi = 0.8) {
  const list = scroller(row);
  if (!list) return;
  const box = list.getBoundingClientRect();
  const r = row.getBoundingClientRect();
  const top = box.top + box.height * lo;
  const bottom = box.top + box.height * hi;
  // A row taller than the band: its top at the band's.
  const delta = r.top < top || r.height > bottom - top ? r.top - top : r.bottom > bottom ? r.bottom - bottom : 0;
  if (Math.abs(delta) < 1) return;
  const still = matchMedia('(prefers-reduced-motion: reduce)').matches;
  list.scrollBy({ top: delta, behavior: still ? 'auto' : 'smooth' });
}
