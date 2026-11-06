// Where the page is, kept in the address after '#' ("#/play/rules"): it works from any static server and any folder,
// survives a reload, and the browser's Back (and a phone's back gesture) walks the same way as the page's own Back.
//
// The path is a screen and, under it, a page opened from it: the rules from practice, the analysis or the settings,
// the analysis of the finished hand, the candidates, the settings' pages. Changing screens replaces the entry (as the
// app, Back does not walk the screens one has looked at); opening a page under one adds an entry, and Back takes it off.

export type Screen = 'PLAY' | 'ANALYZE' | 'RECORDS' | 'SETTINGS';
export const SCREENS: Screen[] = ['PLAY', 'ANALYZE', 'RECORDS', 'SETTINGS'];

interface Route { screen: Screen; sub: string | null }

function parse(hash: string): Route {
  const [s, sub] = hash.replace(/^#\/?/, '').split('/');
  const screen = SCREENS.find((x) => x.toLowerCase() === s) ?? 'PLAY';
  return { screen, sub: sub || null };
}

const path = (r: Route) => `#/${r.screen.toLowerCase()}${r.sub ? '/' + r.sub : ''}`;

class Nav {
  route = $state<Route>(parse(location.hash));

  constructor() {
    // On loading, the screen as the first entry and a page under it as the second, so that Back still works; but not
    // a page showing what is gone with the reload (a finished hand's analysis, the candidates).
    const { screen, sub } = this.route;
    history.replaceState({ depth: 0 }, '', path({ screen, sub: null }));
    if (sub && !['analysis', 'candidates'].includes(sub)) history.pushState({ depth: 1 }, '', path({ screen, sub }));
    this.route = parse(location.hash);
    addEventListener('popstate', () => { this.route = parse(location.hash); });
  }

  get screen(): Screen { return this.route.screen; }
  get sub(): string | null { return this.route.sub; }

  /** Another screen, at its top. */
  go(screen: Screen) {
    const r = { screen, sub: null };
    history.replaceState({ depth: 0 }, '', path(r));
    this.route = r;
  }

  /** A page under the screen `screen` (this one by default): Back comes back here. */
  open(sub: string, screen: Screen = this.route.screen) {
    const r = { screen, sub };
    if (screen === this.route.screen && sub === this.route.sub) return;
    if (screen !== this.route.screen) this.go(screen);
    // From one page to another under the same screen, Back still goes to the screen.
    if (this.route.sub) history.replaceState(history.state, '', path(r));
    else history.pushState({ depth: 1 }, '', path(r));
    this.route = r;
  }

  /** Back from a page to its screen: the browser's Back when the page was opened here, else to the screen's top. */
  back() {
    if (!this.route.sub) return;
    if ((history.state as { depth?: number } | null)?.depth === 1) history.back();
    else this.go(this.route.screen);
  }

  /** Leaves the page `sub` if it is the one shown (what it showed is gone). */
  leave(sub: string) { if (this.route.sub === sub) this.back(); }
}

export const nav = new Nav();
