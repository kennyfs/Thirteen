// A sweep over cards (a finger's swipe, or the mouse with its button held), as the table's hand has: the first card
// passed decides whether the sweep picks or unpicks, and every card passed after it is set the same way. A tap or a
// click without moving stays the card's own click. For a Svelte `use:` on the element holding the cards (each with
// data-card, as Card.svelte's buttons).

export interface SweepOptions {
  isOn: (card: number) => boolean;
  set: (card: number, on: boolean) => void;
}

export function sweep(node: HTMLElement, opts: SweepOptions) {
  let o = opts;
  let s: { x: number; y: number; moved: boolean; on: boolean; touched: Set<number>; id: number } | null = null;
  let suppress = false;

  const cardAt = (x: number, y: number) => {
    const el = document.elementFromPoint(x, y)?.closest('[data-card]') as HTMLElement | null;
    return el && node.contains(el) ? Number(el.dataset.card) : null;
  };
  const touch = (x: number, y: number) => {
    if (!s) return;
    const c = cardAt(x, y);
    if (c === null || s.touched.has(c)) return;
    if (s.touched.size === 0) s.on = !o.isOn(c);
    s.touched.add(c);
    o.set(c, s.on);
  };
  const down = (e: PointerEvent) => {
    if (e.button !== 0 || e.shiftKey) return;
    if (!(e.target as HTMLElement).closest?.('[data-card]')) return;
    s = { x: e.clientX, y: e.clientY, moved: false, on: true, touched: new Set(), id: e.pointerId };
  };
  const move = (e: PointerEvent) => {
    if (!s || e.pointerId !== s.id) return;
    if (!s.moved && Math.hypot(e.clientX - s.x, e.clientY - s.y) < 8) return;
    if (!s.moved) {
      s.moved = true;
      node.setPointerCapture(e.pointerId);
      touch(s.x, s.y);
    }
    // Along the path in small steps, so a quick sweep misses none.
    const steps = Math.min(200, Math.max(1, Math.round(Math.hypot(e.clientX - s.x, e.clientY - s.y) / 4)));
    for (let k = 1; k <= steps; k++) touch(s.x + ((e.clientX - s.x) * k) / steps, s.y + ((e.clientY - s.y) * k) / steps);
    s.x = e.clientX;
    s.y = e.clientY;
  };
  const up = (e: PointerEvent) => {
    if (!s || e.pointerId !== s.id) return;
    if (s.moved) {
      suppress = true;
      setTimeout(() => (suppress = false), 0);
    }
    s = null;
  };
  // The click that ends a sweep is not a card's click.
  const click = (e: MouseEvent) => { if (suppress) { e.stopPropagation(); e.preventDefault(); } };

  node.addEventListener('pointerdown', down);
  node.addEventListener('pointermove', move);
  node.addEventListener('pointerup', up);
  node.addEventListener('pointercancel', up);
  node.addEventListener('click', click, true);
  node.style.touchAction = 'none';
  return {
    update(next: SweepOptions) { o = next; },
    destroy() {
      node.removeEventListener('pointerdown', down);
      node.removeEventListener('pointermove', move);
      node.removeEventListener('pointerup', up);
      node.removeEventListener('pointercancel', up);
      node.removeEventListener('click', click, true);
    },
  };
}
