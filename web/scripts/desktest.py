"""Checks a computer's pages with the mouse and the keys: the practice table (a double click, Shift and a click, a right
click, a sweep, 1 2 3, Z, Delete, Enter; a key a card in 7 + 6 and in one line), the result (analyzed at once, A, F,
Escape), the analysis (R, Delete, the arrows, Space, Enter, ↓ ↑ in the candidates, the mouse between candidates, Escape),
the records (the kinds, a click on the chart) and a narrow window (the tablet's page, a mouse sweep picks). Prints what
it found, and the errors.

  python scripts/desktest.py URL OUT_DIR   e.g. http://localhost:4173/ /tmp/desk
"""
import sys
from pathlib import Path

from playwright.sync_api import sync_playwright

url, out = sys.argv[1], Path(sys.argv[2])
out.mkdir(parents=True, exist_ok=True)
errors = []
P = 'window.thirteen.app.play'


def check(name, ok, got=''):
    print(('ok  ' if ok else 'FAIL') + f' {name} {got}')
    if not ok:
        errors.append(name)


with sync_playwright() as p:
    browser = p.firefox.launch()
    ctx = browser.new_context(viewport={'width': 1440, 'height': 900}, locale='zh-TW')
    page = ctx.new_page()
    page.on('pageerror', lambda e: errors.append(f'pageerror: {e}'))
    page.on('console', lambda m: m.type == 'error' and errors.append(f'console: {m.text}'))
    page.goto(url + '?test')
    page.wait_for_function('window.thirteen && window.thirteen.app.ready', timeout=60000)
    page.wait_for_timeout(300)
    rows = lambda: page.evaluate(f'{P}.rows.map((r) => r.length)')
    hand = lambda: page.evaluate(f'{P}.unplaced')
    tray = lambda i: page.locator('.tray [data-card]').nth(i)

    # A double click: into the first row with room (the front).
    tray(0).dblclick()
    page.wait_for_timeout(200)
    check('double click into the front', rows() == [1, 0, 0], rows())
    # Shift and a click: the run from the last picked.
    tray(0).click()
    tray(3).click(modifiers=['Shift'])
    check('shift-click picks a run', page.evaluate(f'{P}.selected.size') == 4, page.evaluate(f'{P}.selected.size'))
    page.keyboard.press('2')
    page.wait_for_timeout(200)
    check('2 puts them in the middle', rows() == [1, 4, 0], rows())
    # A right click on the middle row: back to the hand.
    page.locator('[data-row="1"]').click(button='right', position={'x': 20, 'y': 20})
    page.wait_for_timeout(200)
    check('right click takes the row back', rows() == [1, 0, 0], rows())
    page.keyboard.press('z')
    page.wait_for_timeout(200)
    check('Z undoes', rows() == [1, 4, 0], rows())
    page.keyboard.press('Delete')
    page.wait_for_timeout(200)
    check('Delete takes all back', rows() == [0, 0, 0], rows())
    # A sweep over four cards picks them; a click on the back row puts them in.
    a, z = tray(2).bounding_box(), tray(5).bounding_box()
    page.mouse.move(a['x'] + 8, a['y'] + a['height'] / 2)
    page.mouse.down()
    page.mouse.move(z['x'] + 8, z['y'] + z['height'] / 2, steps=10)
    page.mouse.up()
    page.wait_for_timeout(200)
    check('a sweep picks four', page.evaluate(f'{P}.selected.size') == 4, page.evaluate(f'{P}.selected.size'))
    page.locator('[data-row="2"]').click(position={'x': 20, 'y': 20})
    page.wait_for_timeout(200)
    check('a click on the back puts them in', rows() == [0, 0, 4], rows())
    page.keyboard.press('Delete')
    page.wait_for_timeout(200)
    page.screenshot(path=str(out / '1-table.png'))
    # Plain rows (the lowest cards in front, the highest at the back), so that the best is likely another; the
    # engine's if they foul. Enter plays.
    page.evaluate(f"""async () => {{ const {{ app, engine }} = window.thirteen;
      const h = [...app.play.hand].sort((a, b) => a - b);
      app.play.reset(app.play.hand, app.gameRules, [h.slice(0, 3), h.slice(3, 8), h.slice(8)]);
      if (app.play.rowsEval?.valid !== true) {{
        const r = await engine.arrange('indep', app.play.hand, app.gameRules); app.play.reset(app.play.hand, app.gameRules, r); }} }}""")
    page.wait_for_function(f'{P}.rowsEval && {P}.rowsEval.valid === true')
    page.keyboard.press('Enter')
    page.wait_for_function('window.thirteen.app.result !== null', timeout=60000)
    check('Enter plays', True)
    page.wait_for_function('window.thirteen.app.gameAnalysis !== null', timeout=60000)
    page.wait_for_timeout(300)
    check('the result is analyzed at once', page.locator('.side .remarks').count() == 1 and page.evaluate('window.thirteen.app.resultTab') == 0)
    page.screenshot(path=str(out / '2-result.png'))
    page.keyboard.press('f')
    page.wait_for_timeout(200)
    check('F saves', page.evaluate('window.thirteen.app.favoriteIds.size') == 1)
    page.keyboard.press('a')
    page.wait_for_timeout(300)
    check('A: the candidates', page.evaluate('window.thirteen.app.resultTab') == 1)
    # The player's, when not the best, is on top and again in its place: ↓ goes along the whole list, ↑ back up.
    n = page.locator('.dc .cand').count()
    if page.locator('.dc .list hr').count() == 1:
        check('the player\'s twice in the list', page.locator('.dc .cand.mine').count() == 2)
        for _ in range(n + 2):
            page.keyboard.press('ArrowDown')
        page.wait_for_timeout(200)
        check('↓ reaches the last candidate', 'on' in page.locator('.dc .cand').last.get_attribute('class'))
        for _ in range(n + 2):
            page.keyboard.press('ArrowUp')
        page.wait_for_timeout(200)
        check('↑ back to the first', 'on' in page.locator('.dc .cand').first.get_attribute('class'))
    else:
        print('note: the player\'s was the best, nothing pinned')
    page.keyboard.press('ArrowDown')
    page.wait_for_timeout(200)
    # The mouse on the third candidate, then in the gap under it: the third still shown at the right.
    c = page.locator('.dc .cand').nth(3).bounding_box()
    page.mouse.move(c['x'] + 60, c['y'] + 10)
    page.wait_for_timeout(150)
    shown = page.locator('.dc .pair .side').last.inner_text()
    page.mouse.move(c['x'] + 60, c['y'] + c['height'] + 1)
    page.wait_for_timeout(150)
    check('the gap between candidates keeps the one shown', page.locator('.dc .pair .side').last.inner_text() == shown)
    page.screenshot(path=str(out / '3-candidates.png'))
    page.mouse.move(5, 5)
    page.keyboard.press('Escape')
    page.wait_for_timeout(300)
    check('Escape: back to the result', page.evaluate('window.thirteen.app.resultTab') == 0)

    # The analysis: R, Delete, the arrows and Space, Enter.
    page.evaluate("() => { location.hash = '#/analyze'; }")
    page.wait_for_timeout(300)
    page.keyboard.press('r')
    page.wait_for_timeout(200)
    check('R deals 13', page.evaluate('window.thirteen.app.anaHand.size') == 13)
    page.keyboard.press('Delete')
    page.wait_for_timeout(200)
    check('Delete clears', page.evaluate('window.thirteen.app.anaHand.size') == 0)
    page.locator('.deck [data-card]').first.focus()
    for k in ['Space', 'ArrowRight', 'Space', 'ArrowDown', 'Space']:
        page.keyboard.press(k)
    page.wait_for_timeout(200)
    check('arrows and Space pick', page.evaluate('window.thirteen.app.anaHand.size') == 3, page.evaluate('window.thirteen.app.anaHand.size'))
    page.locator('.picked [data-card]').first.click()
    page.wait_for_timeout(200)
    check('a click on a picked card puts it back', page.evaluate('window.thirteen.app.anaHand.size') == 2)
    page.keyboard.press('r')
    page.keyboard.press('Enter')
    page.wait_for_function('window.thirteen.app.anaPage === 1', timeout=60000)
    check('Enter analyzes', True)
    page.wait_for_timeout(300)
    page.keyboard.press('ArrowDown')
    page.wait_for_timeout(200)
    page.screenshot(path=str(out / '4-candidates.png'))
    page.keyboard.press('Escape')
    page.wait_for_timeout(300)
    check('Escape: back to the cards', page.evaluate('window.thirteen.app.anaPage') == 0)

    # The records: a click on the chart opens the hand's actions; the kinds.
    page.evaluate("() => { location.hash = '#/records'; }")
    page.wait_for_timeout(500)
    chart = page.locator('.chart svg').bounding_box()
    page.mouse.click(chart['x'] + chart['width'] - 20, chart['y'] + chart['height'] / 2)
    page.wait_for_timeout(300)
    check('a click on the chart opens the hand', page.locator('dialog[open] .action').count() == 2)
    page.screenshot(path=str(out / '5-records.png'))
    page.keyboard.press('Escape')
    page.wait_for_timeout(200)
    page.locator('.kinds button').nth(1).click()
    page.wait_for_timeout(200)
    check('the table games: none yet', page.locator('.records .main .tile').count() == 0, page.locator('.records .main .tile').count())
    page.screenshot(path=str(out / '5b-records-games.png'))

    # A key a card: 7 + 6, then one line.
    for mode, pick, row in [('keys76', ['a', 's', 'z'], '1'), ('keys13', ['q', 'w', ']'], '1')]:
        page.evaluate(f"() => {{ window.thirteen.app.chooseDeskKeys('{mode}'); location.hash = '#/play'; }}")
        page.wait_for_timeout(300)
        page.evaluate('window.thirteen.app.dealNow()')
        page.wait_for_function(f'{P}.unplaced.length === 13')
        page.wait_for_timeout(300)
        for k in pick:
            page.keyboard.press(k)
        check(f'{mode}: keys pick three', page.evaluate(f'{P}.selected.size') == 3, page.evaluate(f'{P}.selected.size'))
        page.keyboard.press(row)
        page.wait_for_timeout(200)
        check(f'{mode}: 1 into the front, the hand closed up', rows() == [3, 0, 0] and page.locator('.tray [data-card]').count() == 10, rows())
        first = page.evaluate(f'{P}.unplaced[0]')
        page.keyboard.press(pick[0])
        check(f'{mode}: the first key is the first card left', page.evaluate(f'[...{P}.selected]') == [first], page.evaluate(f'[...{P}.selected]'))
        page.screenshot(path=str(out / f'7-{mode}-closed.png'))
        page.keyboard.press('Escape')
        was = page.evaluate(f'{P}.sortBySuit')
        page.keyboard.press('Space')
        page.wait_for_timeout(100)
        page.keyboard.press('Backspace')
        page.wait_for_timeout(200)
        check(f'{mode}: Space sorts, Backspace undoes', page.evaluate(f'{P}.sortBySuit') != was and rows() == [0, 0, 0], rows())
        page.screenshot(path=str(out / f'7-{mode}.png'))
    page.evaluate("() => window.thirteen.app.chooseDeskKeys('pick')")

    # A narrow window on a computer: the tablet's pages.
    page.set_viewport_size({'width': 900, 'height': 900})
    page.evaluate("() => { location.hash = '#/play'; }")
    page.wait_for_timeout(500)
    check('a narrow window has the tablet page', page.locator('.desk').count() == 0 and page.locator('.page').count() > 0)
    page.screenshot(path=str(out / '6-narrow.png'))
    # There a mouse sweeps as a finger does: it picks the cards it passes (no dragging).
    page.evaluate('window.thirteen.app.dealNow()')
    page.wait_for_function(f'{P}.unplaced.length === 13')
    page.wait_for_timeout(300)
    a, z = tray(1).bounding_box(), tray(4).bounding_box()
    page.mouse.move(a['x'] + 8, a['y'] + a['height'] / 2)
    page.mouse.down()
    page.mouse.move(z['x'] + 8, z['y'] + z['height'] / 2, steps=10)
    page.mouse.up()
    page.wait_for_timeout(200)
    check('a narrow window: a mouse sweep picks four', page.evaluate(f'{P}.selected.size') == 4, page.evaluate(f'{P}.selected.size'))
    page.screenshot(path=str(out / '6b-narrow-sweep.png'))
    print('errors', errors)
    browser.close()
