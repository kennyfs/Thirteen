"""Screenshots of the page in headless Firefox (playwright), at phone and desktop sizes, light and dark.

  python scripts/shots.py URL OUT_DIR [ONLY]   e.g. http://localhost:4173/ /tmp/shots phone   (ONLY 'mobile': phone and tablet)

The page is opened with ?test, which exposes window.thirteen = {app, engine} to set up a finished hand.
"""
import sys
from pathlib import Path

from playwright.sync_api import sync_playwright

url, out = sys.argv[1], Path(sys.argv[2])
only = sys.argv[3].split(',') if len(sys.argv) > 3 else None
out.mkdir(parents=True, exist_ok=True)

# Name: (width, height, dark, lang). A Pixel 9 in Firefox is about 412 x 840 under the browser's bars.
SIZES = {
    'phone': (412, 840, False, 'zh-TW'),
    'phone-en': (412, 840, False, 'en-US'),
    'phone-land': (915, 380, False, 'zh-TW'),
    'small': (360, 640, False, 'zh-TW'),
    'tablet': (800, 1180, False, 'zh-TW'),
    'desktop': (1440, 900, False, 'zh-TW'),
    'desktop-dark': (1440, 900, True, 'en-US'),
    'laptop': (1280, 720, False, 'zh-TW'),
    'fhd': (1920, 1080, False, 'zh-TW'),
    'qhd': (2560, 1440, False, 'zh-TW'),
    # The other languages (by name: shots.py URL OUT phone-ja,laptop-ja,...): Japanese at every kind of size.
    'phone-ja': (412, 840, False, 'ja-JP'),
    'phone-ja-land': (915, 380, False, 'ja-JP'),
    'small-ja': (360, 640, False, 'ja-JP'),
    'tablet-ja': (800, 1180, False, 'ja-JP'),
    'laptop-ja': (1280, 720, False, 'ja-JP'),
    'desktop-ja': (1440, 900, False, 'ja-JP'),
    'phone-hk': (412, 840, False, 'zh-HK'),
    'laptop-hk': (1280, 720, False, 'zh-HK'),
    'phone-cn': (412, 840, False, 'zh-CN'),
    'phone-vi': (412, 840, False, 'vi-VN'),
    'laptop-vi': (1280, 720, False, 'vi-VN'),
    'phone-id': (412, 840, False, 'id-ID'),
}
# Phone and tablet: these must not change with the computer's layouts (scripts/shotdiff.py compares two runs).
MOBILE = ['phone', 'phone-en', 'phone-land', 'small', 'tablet']

# The same deal, time and records every run, so that two runs can be compared pixel by pixel.
SEEDED = """(() => {
  let s = 12345;
  Math.random = () => { s = (s * 1103515245 + 12345) % 2147483648; return s / 2147483648; };
})();"""


FINISH = """async () => {
  const { app, engine } = window.thirteen;
  const rows = await engine.arrange('indep', app.play.hand, app.gameRules);
  app.play.reset(app.play.hand, app.gameRules, rows);
  app.confirm();
}"""

import random
_deck = list(range(52))
random.Random(7).shuffle(_deck)
HANDS = [_deck[i * 13:(i + 1) * 13] for i in range(4)]


# The visible elements whose own text overflows a box that clips it (an ellipsis, or hidden overflow).
CUT = """() => {
  const out = new Set();
  for (const el of document.querySelectorAll('body *')) {
    if (![...el.childNodes].some((n) => n.nodeType === 3 && n.textContent.trim())) continue;
    const r = el.getBoundingClientRect();
    if (!r.width || !r.height || r.bottom < 0 || r.top > innerHeight || r.right < 0 || r.left > innerWidth) continue;
    const cs = getComputedStyle(el);
    if (cs.visibility === 'hidden' || cs.display === 'inline') continue;
    const wide = el.scrollWidth > el.clientWidth + 1 && (cs.overflowX === 'hidden' || cs.overflowX === 'clip');
    const tall = el.scrollHeight > el.clientHeight + 1 && (cs.overflowY === 'hidden' || cs.overflowY === 'clip');
    if (wide || tall) out.add(el.textContent.trim().replace(/\\s+/g, ' ').slice(0, 100));
  }
  return [...out];
}"""


def wait_ready(page):
    page.wait_for_function('window.thirteen && window.thirteen.app.ready', timeout=60000)
    page.wait_for_timeout(300)

if only == ['mobile']:
    only = MOBILE
# Without names, the zh-TW and en sizes only.
if not only:
    only = [n for n, v in SIZES.items() if v[3] in ('zh-TW', 'en-US')]

with sync_playwright() as p:
    browser = p.firefox.launch()
    for name, (w, h, dark, lang) in SIZES.items():
        if only and name not in only:
            continue
        ctx = browser.new_context(viewport={'width': w, 'height': h}, locale=lang, color_scheme='dark' if dark else 'light')
        ctx.add_init_script(SEEDED)
        page = ctx.new_page()
        page.clock.set_fixed_time('2026-11-06T12:00:00')
        logs = []
        page.on('console', lambda m: logs.append(f'{m.type}: {m.text}'))
        page.on('pageerror', lambda e: logs.append(f'pageerror: {e}'))
        page.goto(url + '?test')
        wait_ready(page)
        # The same four hands every run (the engine deals at random, in its worker).
        page.evaluate('async (h) => { await window.thirteen.app.deal(h); }', HANDS)
        page.wait_for_timeout(300)
        def shot(tag, page=page, name=name):
            page.screenshot(path=str(out / f'{name}-{tag}.png'))
            # Text the layout cuts off or ends in "…", for a look (some is meant to be): NAME-TAG-cut.txt.
            cut = page.evaluate(CUT)
            if cut:
                (out / f'{name}-{tag}-cut.txt').write_text('\n'.join(cut) + '\n')
        shot('1-arrange')
        page.evaluate('() => { window.thirteen.app.play.sortBySuit = true; }')
        page.wait_for_timeout(200)
        shot('1b-arrange-suit')
        page.evaluate('() => { window.thirteen.app.play.sortBySuit = false; }')
        # Some cards placed and two picked (a computer's table shows the rows' states).
        page.evaluate('''() => { const vm = window.thirteen.app.play; const h = vm.unplaced;
          vm.move(h.slice(0, 5), 2); vm.select(vm.unplaced.slice(0, 2), true); }''')
        page.wait_for_timeout(300)
        shot('1c-partial')
        page.evaluate('() => { const vm = window.thirteen.app.play; vm.reset(vm.hand, vm.rules); }')
        page.evaluate(FINISH)
        page.wait_for_function('window.thirteen.app.result !== null', timeout=60000)
        page.wait_for_timeout(300)
        shot('2-result')
        page.evaluate('() => window.thirteen.app.computeGameAnalysis()')
        page.wait_for_function('window.thirteen.app.gameAnalysis !== null', timeout=60000)
        page.wait_for_timeout(300)
        shot('2b-result-summary')
        page.evaluate('() => window.thirteen.app.showGameAnalysis()')
        page.wait_for_function('window.thirteen.app.gameAnalysis !== null', timeout=60000)
        page.wait_for_timeout(300)
        shot('3-result-analysis')
        page.evaluate("() => { location.hash = '#/analyze'; window.thirteen.app.analyzeLastHand(); }")
        page.wait_for_timeout(300)
        shot('4-analyze-pick')
        page.evaluate('() => { const a = window.thirteen.app; [...a.anaHand].slice(0, 4).forEach((c) => a.toggleAnalyzeCard(c)); }')
        page.wait_for_timeout(200)
        shot('4b-analyze-partial')
        page.evaluate('() => window.thirteen.app.analyzeLastHand()')
        page.evaluate('() => window.thirteen.app.analyze()')
        page.wait_for_function('window.thirteen.app.anaPage === 1', timeout=60000)
        page.wait_for_timeout(300)
        shot('5-candidates')
        page.keyboard.press('ArrowDown')
        page.keyboard.press('ArrowDown')
        page.wait_for_timeout(300)
        shot('5b-candidates-next')
        page.evaluate("() => { location.hash = '#/records'; }")
        page.wait_for_timeout(300)
        shot('6-records')
        # Many hands (a computer's table and chart), from the one played: other cards, results and times.
        page.evaluate('''() => { const a = window.thirteen.app; const r0 = a.records.all()[0];
          let s = 7; const rnd = () => { s = (s * 1103515245 + 12345) % 2147483648; return s / 2147483648; };
          const R = '23456789TJQKA', S = 'cdhs', lines = [];
          for (let i = 0; i < 60; i++) {
            const deck = [...Array(52).keys()].sort(() => rnd() - 0.5).slice(0, 13).sort((x, y) => y - x);
            const str = (cs) => cs.map((c) => R[c >> 2] + S[c & 3]).join(' ');
            const rec = { ...r0, id: 'fake' + i, hand: str(deck), back: str(deck.slice(0, 5)), middle: str(deck.slice(5, 10)), front: str(deck.slice(10)),
              result: Math.round((rnd() - 0.45) * 12), time_ms: Math.round(5000 + rnd() * 90000),
              timestamp: new Date(Date.parse('2026-10-20T08:00:00Z') + i * 6 * 3600e3).toISOString() };
            lines.push(JSON.stringify(rec));
          }
          localStorage.setItem('thirteen.records', lines.join('\\n') + '\\n' + localStorage.getItem('thirteen.records'));
          a.recordCount = a.records.count(); }''')
        page.wait_for_timeout(800)
        shot('6b-records-many')
        page.evaluate("() => document.querySelectorAll('.records tbody tr')[3]?.click()")
        page.wait_for_timeout(500)
        shot('6c-records-picked')
        page.evaluate("() => { location.hash = '#/settings'; }")
        page.wait_for_timeout(200)
        shot('7-settings')
        page.evaluate("() => { location.hash = '#/settings/rules'; }")
        page.wait_for_timeout(200)
        shot('8-rules')
        page.evaluate("() => { location.hash = '#/battle'; }")
        page.wait_for_timeout(200)
        shot('9-battle')
        (out / f'{name}-console.txt').write_text('\n'.join(logs))
        ctx.close()
        print(name, 'done', len(logs), 'console lines')
    browser.close()
