// What the screens share about the window: whether it is wide (a rail at the side) or tall (a bar under), the room the
// screens have, `u` (what a 14px text takes now), and the card sizes worked out once for them (sizing.ts).
import { cardSizes, type CardSizes } from './sizing';

export const NAV_BAR = 80;
export const NAV_RAIL = 88;

export const ui = $state({ w: 0, h: 0, wide: false, u: 14, sizes: { play: 44, other: 26, record: 26 } as CardSizes });

export function measure(textScale: number) {
  const root = parseFloat(getComputedStyle(document.documentElement).fontSize) || 16 * textScale;
  // Less what the notch and the corners keep (the shell's padding, env(safe-area-inset-*)).
  const shell = document.querySelector('.shell');
  const pad = shell ? getComputedStyle(shell) : null;
  const px = (v: string | undefined) => parseFloat(v ?? '') || 0;
  const W = window.innerWidth - px(pad?.paddingLeft) - px(pad?.paddingRight);
  const H = window.innerHeight - px(pad?.paddingTop) - px(pad?.paddingBottom);
  // A rail whenever the window is wider than tall: there the height is what the cards need.
  const wide = W > H;
  const w = wide ? W - NAV_RAIL : W;
  const h = wide ? H : H - NAV_BAR;
  const u = 14 * (root / 16);
  ui.w = w;
  ui.h = h;
  ui.wide = wide;
  ui.u = u;
  ui.sizes = cardSizes(w, h, u);
}

/** Room for text: what a `px` text takes, as the app's textRoom. */
export const textRoom = (px: number) => ui.u * (px / 14);

/** "+3", "−1.5": no decimals for whole numbers. */
export function signed(x: number): string {
  const a = Math.abs(x);
  return (x >= 0 ? '+' : '−') + (Number.isInteger(a) ? a.toFixed(0) : a.toFixed(2));
}

/** A Q to two places, signed: "+0.57", "−1.02". */
export const fmtQ = (x: number) => (x >= 0 ? '+' : '−') + Math.abs(x).toFixed(2);

/** A record's time (UTC, ISO 8601) here: "2026-11-06 13:00". */
export function localTime(stamp: string): string {
  const d = new Date(stamp);
  if (Number.isNaN(d.getTime())) return stamp.slice(0, 16).replace('T', ' ');
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}
