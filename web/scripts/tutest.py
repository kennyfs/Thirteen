"""Checks the welcome card, the tutorial (its pages, the hand to try judged by the AI), the tour (each step, and leaving
it midway), the previous hand's result and analysis (the button, a computer's L), a sweep over the analysis's cards,
and a computer's cursor keys and Shift + 1 2 3; on a phone's size and a computer's. Screenshots of each, and the errors.

  python scripts/tutest.py URL OUT_DIR   e.g. http://localhost:4173/ /tmp/tut
"""
import sys
from pathlib import Path

from playwright.sync_api import sync_playwright

url, out = sys.argv[1], Path(sys.argv[2])
out.mkdir(parents=True, exist_ok=True)
errors = []
A = 'window.thirteen.app'


def check(name, ok, got=''):
    print(('ok  ' if ok else 'FAIL') + f' {name} {got}')
    if not ok:
        errors.append(name)


FINISH = """async () => {
  const { app, engine } = window.thirteen;
  const rows = await engine.arrange('indep', app.play.hand, app.gameRules);
  app.play.reset(app.play.hand, app.gameRules, rows);
  app.confirm();
}"""
# The tutorial's hand: the instinctive rows, then the AI's best.
GREEDY = [[45, 19, 2], [46, 38, 22, 18, 6], None]
BEST = [[51, 49, 45], [46, 38, 22, 6, 2], None]


def open_page(browser, w, h, query='?test&welcome', lang='zh-TW'):
    ctx = browser.new_context(viewport={'width': w, 'height': h}, locale=lang)
    page = ctx.new_page()
    page.on('pageerror', lambda e: errors.append(f'pageerror: {e}'))
    page.on('console', lambda m: m.type == 'error' and errors.append(f'console: {m.text}'))
    page.goto(url + query)
    page.wait_for_function('window.thirteen && window.thirteen.app.ready', timeout=60000)
    page.wait_for_timeout(400)
    return page


def set_rows(page, rows):
    for r, cs in enumerate(rows):
        if cs is None:
            continue
        for c in cs:
            page.locator(f'.tray [data-card="{c}"]').first.click()
        page.locator(f'[data-row="{r}"]').first.click()
        page.wait_for_timeout(150)


def tutorial(page, tag):
    for i in range(7):
        page.wait_for_timeout(250)
        page.screenshot(path=str(out / f'{tag}-tut{i + 1}.png'))
        if i == 4:
            set_rows(page, GREEDY)
            page.wait_for_timeout(300)
            page.screenshot(path=str(out / f'{tag}-tut5-set.png'))
            page.get_by_role('button', name='讓 AI 評分').first.click()
            page.wait_for_selector('text=比最佳少', timeout=30000)
            page.wait_for_timeout(300)
            page.screenshot(path=str(out / f'{tag}-tut5-greedy.png'), full_page=True)
            check(f'{tag}: the instinctive rows are behind', page.locator('text=比最佳少').count() > 0)
            page.get_by_role('button', name='重擺').click()
            page.wait_for_timeout(400)
            set_rows(page, BEST)
            page.get_by_role('button', name='讓 AI 評分').first.click()
            page.wait_for_selector('text=就是 AI 的最佳擺法', timeout=30000)
            page.wait_for_timeout(300)
            page.screenshot(path=str(out / f'{tag}-tut5-best.png'))
            check(f'{tag}: the AI\'s best is found best', True)
        if i < 6:
            page.locator('.tutbar button.filled').click()


def run_tour(page, tag, leave_at=None):
    n = 0
    while page.locator('.tour').count():
        n += 1
        page.wait_for_timeout(450)
        page.screenshot(path=str(out / f'{tag}-tour{n}.png'))
        if n == leave_at:
            page.keyboard.press('Escape')
            page.wait_for_timeout(200)
            return n
        btn = page.locator('.tour .actions button.filled')
        btn.last.click()
        if n > 12:
            break
    return n


with sync_playwright() as p:
    browser = p.firefox.launch()

    # ---- A phone ----
    page = open_page(browser, 412, 840)
    check('phone: the welcome card', page.locator('dialog[open] >> text=歡迎來玩十三支').count() == 1)
    page.screenshot(path=str(out / 'phone-welcome.png'))
    page.get_by_role('button', name='看教學').click()
    page.wait_for_timeout(300)
    check('phone: the tutorial at #/play/tutorial', page.evaluate('location.hash') == '#/play/tutorial', page.evaluate('location.hash'))
    tutorial(page, 'phone')
    page.locator('.tutbar button.filled').click()  # Start playing
    page.wait_for_timeout(300)
    check('phone: on to practice', page.evaluate('location.hash') == '#/play', page.evaluate('location.hash'))
    check('phone: the welcome card once', page.evaluate("localStorage.getItem('thirteen.welcome.seen') ?? Object.keys(localStorage).filter(k => k.includes('welcome')).join()") != None)
    page.reload()
    page.wait_for_function('window.thirteen && window.thirteen.app.ready', timeout=60000)
    page.wait_for_timeout(300)
    check('phone: no welcome card again', page.locator('dialog[open]').count() == 0)

    # The tour from the settings, left at step 3 with Escape: practice's hand untouched.
    before = page.evaluate(f'JSON.stringify([{A}.play.hand, {A}.play.rows, {A}.session])')
    page.locator('nav button').nth(4).click()
    page.get_by_text('功能導覽').click()
    n = run_tour(page, 'phone-esc', leave_at=3)
    check('phone: Escape ends the tour', page.locator('.tour').count() == 0, n)
    check('phone: the tour changed nothing', page.evaluate(f'JSON.stringify([{A}.play.hand, {A}.play.rows, {A}.session])') == before)
    # The whole tour, then its last step's tutorial button would follow; Done here.
    page.locator('nav button').nth(4).click()
    page.get_by_text('功能導覽').click()
    n = run_tour(page, 'phone')
    check('phone: the tour has 8 steps and ends', n == 8 and page.locator('.tour').count() == 0, n)
    # The browser's Back ends it too.
    page.locator('nav button').nth(4).click()
    page.get_by_text('功能導覽').click()
    page.wait_for_timeout(900)
    page.go_back()
    page.wait_for_timeout(300)
    check('phone: Back ends the tour', page.locator('.tour').count() == 0)

    # The previous hand.
    page.locator('nav button').nth(0).click()
    page.wait_for_timeout(300)
    page.evaluate(FINISH)
    page.wait_for_function(f'{A}.result !== null', timeout=30000)
    page.wait_for_timeout(300)
    page.get_by_role('button', name='下一局').first.click()
    page.wait_for_timeout(500)
    page.locator('.tray [data-card]').first.click()
    picked = page.evaluate(f'[...{A}.play.selected]')
    last = page.get_by_role('button', name='上一局')
    check('phone: a Last hand button', last.count() == 1)
    page.screenshot(path=str(out / 'phone-last-button.png'))
    last.click()
    page.wait_for_timeout(300)
    check('phone: the last hand\'s result', page.evaluate('location.hash') == '#/play/last' and page.locator('.chip.static').count() == 1)
    page.screenshot(path=str(out / 'phone-last-result.png'))
    page.get_by_role('button', name='分析我的牌').first.click()
    page.wait_for_selector('.cand, [class*="cand"]', timeout=30000)
    page.wait_for_timeout(500)
    page.screenshot(path=str(out / 'phone-last-analysis.png'))
    check('phone: its analysis', page.evaluate('location.hash') == '#/play/lastanalysis')
    page.go_back()
    page.wait_for_timeout(300)
    check('phone: Back to the hand in play, as it was', page.evaluate('location.hash') == '#/play' and page.evaluate(f'[...{A}.play.selected]') == picked)

    # A sweep over the analysis's cards.
    page.locator('nav button').nth(2).click()
    page.wait_for_timeout(400)
    cards = page.locator('.suits [data-card]')
    b0, b4 = cards.nth(0).bounding_box(), cards.nth(4).bounding_box()
    page.mouse.move(b0['x'] + b0['width'] / 2, b0['y'] + b0['height'] / 2)
    page.mouse.down()
    page.mouse.move(b4['x'] + b4['width'] / 2, b4['y'] + b4['height'] / 2, steps=12)
    page.mouse.up()
    page.wait_for_timeout(200)
    check('phone: a sweep picks five on the analysis', page.evaluate(f'{A}.anaHand.size') == 5, page.evaluate(f'{A}.anaHand.size'))
    page.screenshot(path=str(out / 'phone-analyze-sweep.png'))
    page.context.close()

    # ---- English, a phone: the tutorial's text pages ----
    page = open_page(browser, 412, 840, lang='en-US')
    page.get_by_role('button', name='Learn to play').click()
    for i in range(4):
        page.wait_for_timeout(250)
        page.screenshot(path=str(out / f'phone-en-tut{i + 1}.png'), full_page=True)
        page.locator('.tutbar button.filled').click()
    page.locator('.tutbar button.filled').click()
    page.wait_for_timeout(250)
    page.screenshot(path=str(out / 'phone-en-tut6.png'))
    page.context.close()

    # ---- A computer ----
    page = open_page(browser, 1440, 900)
    page.screenshot(path=str(out / 'desk-welcome.png'))
    page.get_by_role('button', name='導覽功能').click()
    n = run_tour(page, 'desk')
    check('desk: the tour has 8 steps', n == 8, n)
    page.locator('nav button').nth(4).click()
    page.get_by_text('怎麼玩').first.click()
    tutorial(page, 'desk')
    page.locator('.tutbar button.filled').click()
    page.wait_for_timeout(300)

    # ?: the keys shown, then put away.
    kbds = lambda: page.locator('kbd').count()
    check('desk: only ? among the keys at first', kbds() == 1, kbds())
    page.keyboard.press('?')
    page.wait_for_timeout(200)
    check('desk: ? shows the keys', kbds() > 10, kbds())
    page.screenshot(path=str(out / 'desk-keys-shown.png'))
    page.keyboard.press('?')
    page.wait_for_timeout(200)
    check('desk: ? again puts them away', kbds() == 1, kbds())
    page.screenshot(path=str(out / 'desk-keys-hidden.png'))

    # L: the previous hand's result, its analysis, back.
    page.evaluate(FINISH)
    page.wait_for_function(f'{A}.result !== null', timeout=30000)
    page.wait_for_timeout(300)
    page.keyboard.press('Enter')
    page.wait_for_timeout(500)
    page.keyboard.press('KeyL')
    page.wait_for_timeout(300)
    check('desk: L shows the last hand', page.evaluate('location.hash') == '#/play/last')
    page.wait_for_timeout(1500)
    page.screenshot(path=str(out / 'desk-last-result.png'))
    page.keyboard.press('KeyA')
    page.wait_for_timeout(1200)
    check('desk: A its analysis', page.evaluate('location.hash') == '#/play/lastanalysis')
    page.screenshot(path=str(out / 'desk-last-analysis.png'))
    page.keyboard.press('Escape')
    page.wait_for_timeout(300)
    check('desk: Esc back to the hand in play', page.evaluate('location.hash') == '#/play' and page.evaluate(f'{A}.result') is None)
    page.keyboard.press('KeyL')
    page.wait_for_timeout(200)
    page.keyboard.press('KeyL')
    page.wait_for_timeout(200)
    page.keyboard.press('KeyL')
    page.wait_for_timeout(300)
    check('desk: L L L comes round to the hand', page.evaluate('location.hash') == '#/play', page.evaluate('location.hash'))

    # Shift + 1: a row back. The cursor keys.
    page.evaluate(f"{A}.chooseDeskKeys('cursor')")
    page.wait_for_timeout(200)
    for k in ['KeyD', 'KeyD', 'KeyW', 'KeyD', 'KeyD']:
        page.keyboard.press(k)
    page.wait_for_timeout(100)
    check('cursor: W and two moves pick three', page.evaluate(f'{A}.play.selected.size') == 3, page.evaluate(f'[...{A}.play.selected]'))
    page.screenshot(path=str(out / 'desk-cursor-painting.png'))
    page.keyboard.press('KeyS')
    page.keyboard.press('KeyD')
    check('cursor: S stops', page.evaluate(f'{A}.play.selected.size') == 3)
    page.keyboard.press('Digit1')
    page.wait_for_timeout(200)
    check('cursor: 1 into the front', page.evaluate(f'{A}.play.rows.map((r) => r.length)') == [3, 0, 0])
    page.keyboard.press('KeyA')
    page.keyboard.press('KeyW')
    page.keyboard.press('KeyA')
    page.wait_for_timeout(100)
    page.screenshot(path=str(out / 'desk-cursor-closed.png'))
    check('cursor: after a play, W and a move pick two', page.evaluate(f'{A}.play.selected.size') == 2)
    page.keyboard.press('Escape')
    page.keyboard.press('Shift+Digit1')
    page.wait_for_timeout(200)
    check('Shift + 1 takes the front back', page.evaluate(f'{A}.play.rows.map((r) => r.length)') == [0, 0, 0])
    page.screenshot(path=str(out / 'desk-cursor.png'))
    # The cursor on two lines: ↓ goes to the line under.
    page.evaluate(f'{A}.chooseDeskLines(7)')
    page.wait_for_timeout(300)
    page.keyboard.press('KeyA')
    for k in ['KeyA'] * 12 + ['ArrowDown', 'KeyW']:
        page.keyboard.press(k)
    page.wait_for_timeout(100)
    want = page.evaluate(f'{A}.play.unplaced[7]')
    check('cursor on 7 + 6: ↓ to the second line', page.evaluate(f'[...{A}.play.selected]') == [want], page.evaluate(f'[...{A}.play.selected]'))
    page.screenshot(path=str(out / 'desk-cursor-76.png'))
    page.keyboard.press('Escape')
    page.evaluate(f"{A}.chooseDeskKeys('pick')")
    page.evaluate(f'{A}.chooseDeskLines(13)')

    # A sweep over the analysis's 52.
    page.locator('nav button').nth(2).click()
    page.wait_for_timeout(400)
    page.keyboard.press('Delete')
    cards = page.locator('.deck [data-card]')
    b0, b5 = cards.nth(0).bounding_box(), cards.nth(5).bounding_box()
    page.mouse.move(b0['x'] + 10, b0['y'] + 10)
    page.mouse.down()
    page.mouse.move(b5['x'] + 10, b5['y'] + 10, steps=12)
    page.mouse.up()
    page.wait_for_timeout(200)
    check('desk: a sweep picks six on the analysis', page.evaluate(f'{A}.anaHand.size') == 6, page.evaluate(f'{A}.anaHand.size'))
    page.mouse.move(b5['x'] + 10, b5['y'] + 10)
    page.mouse.down()
    page.mouse.move(b0['x'] + 10, b0['y'] + 10, steps=12)
    page.mouse.up()
    page.wait_for_timeout(200)
    check('desk: a sweep from a picked card unpicks', page.evaluate(f'{A}.anaHand.size') == 0, page.evaluate(f'{A}.anaHand.size'))
    page.context.close()

    browser.close()

print('errors', errors)
