"""Checks a computer's pages with the mouse and the keys: the practice table (a double click, Shift and a click, a right
click, dragging, 1 2 3, Z, Delete, Enter), the result (A twice, F, Escape), the analysis (R, Delete, the arrows, Space,
Enter, ↓ in the candidates, Escape) and the records (a click on the chart, ↓). Prints what it found, and the errors.

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
    # Dragging a card into the back row.
    box = tray(5).bounding_box()
    target = page.locator('[data-row="2"]').bounding_box()
    page.mouse.move(box['x'] + box['width'] / 2, box['y'] + box['height'] / 2)
    page.mouse.down()
    page.mouse.move(target['x'] + target['width'] * 0.6, target['y'] + target['height'] / 2, steps=8)
    page.mouse.up()
    page.wait_for_timeout(200)
    check('drag into the back', rows() == [0, 0, 1], rows())
    page.screenshot(path=str(out / '1-table.png'))
    # The engine's rows, and Enter plays.
    page.evaluate(f"""async () => {{ const {{ app, engine }} = window.thirteen;
      const r = await engine.arrange('indep', app.play.hand, app.gameRules); app.play.reset(app.play.hand, app.gameRules, r); }}""")
    page.wait_for_function(f'{P}.rowsEval && {P}.rowsEval.valid === true')
    page.keyboard.press('Enter')
    page.wait_for_function('window.thirteen.app.result !== null', timeout=60000)
    check('Enter plays', True)
    page.keyboard.press('a')
    page.wait_for_function('window.thirteen.app.gameAnalysis !== null', timeout=60000)
    page.wait_for_timeout(300)
    check('A analyzes in the panel, on the result', page.evaluate('window.thirteen.app.resultTab') == 0)
    page.screenshot(path=str(out / '2-result.png'))
    page.keyboard.press('f')
    page.wait_for_timeout(200)
    check('F saves', page.evaluate('window.thirteen.app.favoriteIds.size') == 1)
    page.keyboard.press('a')
    page.wait_for_timeout(300)
    check('A again: the candidates', page.evaluate('window.thirteen.app.resultTab') == 1)
    page.keyboard.press('ArrowDown')
    page.wait_for_timeout(200)
    page.screenshot(path=str(out / '3-candidates.png'))
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

    # The records: a click on the chart picks a hand, ↓ the next.
    page.evaluate("() => { location.hash = '#/records'; }")
    page.wait_for_timeout(500)
    chart = page.locator('.chart svg').bounding_box()
    page.mouse.click(chart['x'] + chart['width'] - 20, chart['y'] + chart['height'] / 2)
    page.wait_for_timeout(300)
    check('a click on the chart picks a hand', page.locator('.records tr.on').count() == 1)
    page.screenshot(path=str(out / '5-records.png'))

    # A narrow window on a computer: the tablet's pages.
    page.set_viewport_size({'width': 900, 'height': 900})
    page.evaluate("() => { location.hash = '#/play'; }")
    page.wait_for_timeout(500)
    check('a narrow window has the tablet page', page.locator('.desk').count() == 0 and page.locator('.page').count() > 0)
    page.screenshot(path=str(out / '6-narrow.png'))
    print('errors', errors)
    browser.close()
