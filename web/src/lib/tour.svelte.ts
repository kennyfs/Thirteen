// The tour of the page: a few steps, each a screen and the thing on it shown in a light (an element with that
// data-tour), with a word on it. It only goes from screen to screen: nothing is dealt, played or recorded, so leaving
// it at any step (Skip, Escape, Back) leaves everything as it was. Tour.svelte draws it.
import type { Screen } from './nav.svelte';
import * as store from './store';

export interface TourStep { screen: Screen; target: string | null; title: string; text: string }

export const STEPS: TourStep[] = [
  { screen: 'PLAY', target: 'hand', title: 'tour_play_title', text: 'tour_play' },
  { screen: 'PLAY', target: 'confirm', title: 'tour_confirm_title', text: 'tour_confirm' },
  { screen: 'PLAY', target: 'rules', title: 'tour_rules_title', text: 'tour_rules' },
  { screen: 'ANALYZE', target: 'nav-ANALYZE', title: 'tour_analyze_title', text: 'tour_analyze' },
  { screen: 'BATTLE', target: 'nav-BATTLE', title: 'tour_battle_title', text: 'tour_battle' },
  { screen: 'RECORDS', target: 'nav-RECORDS', title: 'tour_records_title', text: 'tour_records' },
  { screen: 'SETTINGS', target: 'nav-SETTINGS', title: 'tour_settings_title', text: 'tour_settings' },
  { screen: 'PLAY', target: null, title: 'tour_end_title', text: 'tour_end' },
];

class Tour {
  /** The step shown, -1 when the tour is not on. */
  step = $state(-1);
  get on() { return this.step >= 0; }
  start() { this.step = 0; }
  next() { if (this.step >= STEPS.length - 1) this.end(); else this.step++; }
  prev() { if (this.step > 0) this.step--; }
  end() { this.step = -1; }
}

export const tour = new Tour();

/** The welcome card is shown once, on the first visit (not to the screenshot scripts, ?test, unless ?welcome). */
export function welcomeDue(): boolean {
  const q = new URLSearchParams(location.search);
  if (q.has('test') && !q.has('welcome')) return false;
  return store.get('welcome.seen', '') === '';
}
export function welcomeDone() { store.set('welcome.seen', '1'); }
