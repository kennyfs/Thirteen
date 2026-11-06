"""Screenshots of the page in headless Firefox (playwright), at phone and desktop sizes, light and dark.

  python scripts/shots.py URL OUT_DIR [ONLY]   e.g. http://localhost:4173/ /tmp/shots phone

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
}

FINISH = """async () => {
  const { app, engine } = window.thirteen;
  const rows = await engine.arrange('indep', app.play.hand, app.gameRules);
  app.play.reset(app.play.hand, app.gameRules, rows);
  app.confirm();
}"""

def wait_ready(page):
    page.wait_for_function('window.thirteen && window.thirteen.app.ready', timeout=60000)
    page.wait_for_timeout(300)

with sync_playwright() as p:
    browser = p.firefox.launch()
    for name, (w, h, dark, lang) in SIZES.items():
        if only and name not in only:
            continue
        ctx = browser.new_context(viewport={'width': w, 'height': h}, locale=lang, color_scheme='dark' if dark else 'light')
        page = ctx.new_page()
        logs = []
        page.on('console', lambda m: logs.append(f'{m.type}: {m.text}'))
        page.on('pageerror', lambda e: logs.append(f'pageerror: {e}'))
        page.goto(url + '?test')
        wait_ready(page)
        shot = lambda tag: page.screenshot(path=str(out / f'{name}-{tag}.png'))
        shot('1-arrange')
        page.evaluate('() => { window.thirteen.app.play.sortBySuit = true; }')
        page.wait_for_timeout(200)
        shot('1b-arrange-suit')
        page.evaluate('() => { window.thirteen.app.play.sortBySuit = false; }')
        page.evaluate(FINISH)
        page.wait_for_function('window.thirteen.app.result !== null', timeout=60000)
        page.wait_for_timeout(300)
        shot('2-result')
        page.evaluate('() => window.thirteen.app.showGameAnalysis()')
        page.wait_for_function('window.thirteen.app.gameAnalysis !== null', timeout=60000)
        page.wait_for_timeout(300)
        shot('3-result-analysis')
        page.evaluate("() => { location.hash = '#/analyze'; window.thirteen.app.analyzeLastHand(); }")
        page.wait_for_timeout(300)
        shot('4-analyze-pick')
        page.evaluate('() => window.thirteen.app.analyze()')
        page.wait_for_function('window.thirteen.app.anaPage === 1', timeout=60000)
        page.wait_for_timeout(300)
        shot('5-candidates')
        page.evaluate("() => { location.hash = '#/records'; }")
        page.wait_for_timeout(300)
        shot('6-records')
        page.evaluate("() => { location.hash = '#/settings'; }")
        page.wait_for_timeout(200)
        shot('7-settings')
        page.evaluate("() => { location.hash = '#/settings/rules'; }")
        page.wait_for_timeout(200)
        shot('8-rules')
        (out / f'{name}-console.txt').write_text('\n'.join(logs))
        ctx.close()
        print(name, 'done', len(logs), 'console lines')
    browser.close()
