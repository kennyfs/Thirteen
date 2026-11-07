// What the screens share about the window: whether it is wide (a rail at the side) or tall (a bar under), whether it
// is a computer's (`desk`: a mouse, and room for a side panel), the room the screens have, `u` (what a 14px text takes
// now), and the card sizes worked out once for them (sizing.ts).
import { cardSizes, deskCardSizes, type CardSizes } from './sizing';

export const NAV_BAR = 80;
export const NAV_RAIL = 88;

// `showKeys`: a computer's pages show their keys (on the buttons, the rows, in a list) only after ? (KeyHint).
export const ui = $state({ w: 0, h: 0, wide: false, desk: false, u: 14, sizes: { play: 44, other: 26, record: 26 } as CardSizes, showKeys: false });

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
  // No bar or rail in a battle's room (App.svelte).
  const bare = !!document.querySelector('.shell > nav[hidden]');
  const w = wide && !bare ? W - NAV_RAIL : W;
  const h = wide || bare ? H : H - NAV_BAR;
  const u = 14 * (root / 16);
  ui.w = w;
  ui.h = h;
  ui.wide = wide;
  ui.u = u;
  // A computer's (its own pages, Desk*.svelte): a mouse (or a touchpad) that hovers, and a window of 1024 x 600 or more.
  // A phone's or a tablet's pages otherwise, a computer's window made narrow too.
  ui.desk = matchMedia('(hover: hover) and (pointer: fine)').matches && wide && W >= 1024 && H >= 600;
  ui.sizes = ui.desk ? deskCardSizes(w, h, u) : cardSizes(w, h, u);
}

/** Room for text: what a `px` text takes, as the app's textRoom. */
export const textRoom = (px: number) => ui.u * (px / 14);

let probe: HTMLSpanElement | null = null;
const widths = new Map<string, number>();
/** How wide `text` is at a `px` size (as the app's dp: it follows the page's text size) in the page's font, for a
 *  layout that goes by its names' lengths (the probabilities' tiles, which differ a lot between languages). Measured
 *  as the page lays it out (a canvas's measureText is some 8% short in Firefox), once a language, size and text. */
export function textWidth(text: string, px: number, weight = 400): number {
  const size = textRoom(px);
  const key = `${document.documentElement.lang}|${weight}|${size}|${text}`;
  let v = widths.get(key);
  if (v === undefined) {
    if (!probe) {
      probe = document.createElement('span');
      probe.setAttribute('aria-hidden', 'true');
      probe.style.cssText = 'position: absolute; left: -9999px; top: 0; visibility: hidden; white-space: nowrap;';
      document.body.appendChild(probe);
    }
    probe.style.fontSize = `${size}px`;
    probe.style.fontWeight = String(weight);
    probe.textContent = text;
    v = probe.getBoundingClientRect().width;
    widths.set(key, v);
  }
  return v;
}

/** The least a name may shrink to keep to its room (the app's SHRINK_MIN), and the size it takes in a room: its own
 *  `rem`, or less down to that, past which it ends in "…" (`.ellipsis`). */
export const SHRINK_MIN = 0.8;
export function fitRem(text: string, px: number, room: number): string {
  const s = Math.max(SHRINK_MIN, Math.min(1, room / Math.max(1, textWidth(text, px))));
  return `${((px / 16) * s).toFixed(4)}rem`;
}

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
