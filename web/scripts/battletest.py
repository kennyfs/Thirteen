"""Plays battles in two browsers against a battle server and takes screenshots of each page.

Usage: python scripts/battletest.py PAGE_URL SERVER OUT [phone|desk]
  e.g. python scripts/battletest.py http://localhost:4173/ localhost:8090 shots/battle phone
"""
import sys
from playwright.sync_api import sync_playwright

url, server, out = sys.argv[1], sys.argv[2], sys.argv[3]
kind = sys.argv[4] if len(sys.argv) > 4 else 'phone'
VIEW = {'phone': dict(viewport={'width': 412, 'height': 915}, device_scale_factor=2.625, is_mobile=True, has_touch=True),
        'desk': dict(viewport={'width': 1440, 'height': 900})}[kind]
errors = []


def page(browser, name):
    ctx = browser.new_context(locale='zh-TW', **VIEW)
    pg = ctx.new_page()
    pg.on('pageerror', lambda e: errors.append(f'{name}: {e}'))
    pg.on('console', lambda m: m.type == 'error' and errors.append(f'{name}: {m.text}'))
    pg.goto(url + '?test#/battle')
    pg.wait_for_function('window.thirteen && window.thirteen.app.ready', timeout=60000)
    return pg


B = 'window.thirteen.app.battle'
shot_n = [0]


def shot(pg, name):
    shot_n[0] += 1
    pg.wait_for_timeout(400)
    pg.screenshot(path=f'{out}/{kind}-{shot_n[0]:02d}-{name}.png')


def wait(pg, cond, timeout=60000):
    pg.wait_for_function(cond.replace('B.', B + '.'), timeout=timeout)


def arrange(pg):
    """Sets the dealt hand as the independent arranger would."""
    wait(pg, 'B.deal !== null')
    pg.evaluate(f"""async () => {{ const {{ engine }} = window.thirteen; const b = {B};
      const rows = await engine.arrange('indep', b.deal.cards, b.room.rules); b.arranger.reset(b.deal.cards, b.room.rules, rows); }}""")
    wait(pg, 'B.arranger.rowsEval && B.arranger.rowsEval.valid === true')


def enter(pg, name):
    pg.fill('input[maxlength="16"]', name)
    pg.get_by_role('button', name='更改').click()
    pg.fill('input[placeholder^="例如"]', server)


with sync_playwright() as p:
    browser = p.firefox.launch()
    host = page(browser, 'host')
    guest = page(browser, 'guest')
    shot(host, 'entry')

    # ---- A puzzle of two hands, two players ----
    enter(host, '房主')
    host.get_by_role('button', name='開一場解題比賽').click()
    wait(host, 'B.room && B.room.phase === "LOBBY"')
    host.evaluate(f'{B}.changeSettings({{ ...{B}.room.settings, hands: 2, timeLimit: 60 }})')
    wait(host, 'B.room.settings.hands === 2')
    pin = host.evaluate(f'{B}.room.pin')
    print('pin', pin, 'nav hidden', host.evaluate('document.querySelector(".shell > nav").hidden'))
    enter(guest, '客人')
    guest.fill('input[inputmode="numeric"]', pin)
    if kind == 'phone':
        guest.get_by_role('button', name='加入', exact=True).click()  # (a computer's page joins at the sixth digit)
    wait(host, 'B.room.players.length === 2')
    shot(host, 'lobby-host')
    shot(guest, 'lobby-guest')
    host.get_by_role('button', name='開始解題比賽').click()
    wait(host, 'B.room.phase === "PLAYING"')
    shot(host, 'playing')
    arrange(host)
    shot(host, 'playing-set')
    host.get_by_role('button', name='交出擺法').click()
    wait(host, 'B.mine && B.mine.submitted')
    shot(host, 'waiting')
    # The guest reloads mid-hand: the seat comes back.
    guest.reload()
    guest.wait_for_function('window.thirteen && window.thirteen.app.ready', timeout=60000)
    wait(guest, 'B.room && B.room.phase === "PLAYING" && B.deal !== null')
    print('guest resumed', guest.evaluate('location.hash'), guest.evaluate(f'{B}.room.players.map(p => p.name + (p.connected ? "" : "(away)")).join(",")'))
    arrange(guest)
    guest.evaluate(f'{B}.submit()')
    wait(host, 'B.room.phase === "REVIEW" && B.outcomes.length === 1')
    shot(host, 'review')
    host.get_by_role('button', name='分析').click()
    wait(host, 'B.review.detailAnalysis !== null')
    shot(host, 'review-analysis')
    host.go_back()
    wait(host, 'B.review.detail === null')
    print('back from analysis', host.evaluate('location.hash'))
    host.get_by_role('button', name='下一手').click()
    guest.evaluate(f'{B}.ready()')
    wait(host, 'B.room.phase === "PLAYING" && B.room.hand === 1')
    for pg in (host, guest):
        arrange(pg)
        pg.evaluate(f'{B}.submit()')
    wait(host, 'B.over && B.outcomes.length === 2')
    shot(host, 'last')
    host.get_by_role('button', name='最終結果').click()
    shot(host, 'summary')
    print('nav after last', host.evaluate('document.querySelector(".shell > nav").hidden'))
    host.locator('.hand-line').first.click()
    shot(host, 'detail')
    host.locator('.result-table button.line, table.hands tr.pick').nth(1).click()
    wait(host, 'B.review.detailAnalysis !== null')
    shot(host, 'detail-analysis')
    host.go_back(); host.wait_for_timeout(300); host.go_back()
    host.wait_for_timeout(300)
    print('back twice', host.evaluate(f'[{B}.review.detail, location.hash]'))
    recs = host.evaluate("JSON.parse('[' + localStorage.getItem('thirteen.records').trim().split('\\n').join(',') + ']').filter(r => r.label === 'puzzle').length")
    grecs = guest.evaluate("JSON.parse('[' + localStorage.getItem('thirteen.records').trim().split('\\n').join(',') + ']').filter(r => r.label === 'puzzle').length")
    print('puzzle records host', recs, 'guest (reloaded)', grecs)
    host.get_by_role('button', name='完成').click()
    wait(host, 'B.room === null')

    # ---- A table game of two hands, one player and the networks ----
    host.get_by_role('button', name='開一桌同桌對局').click()
    wait(host, 'B.room && B.room.phase === "LOBBY"')
    host.evaluate(f'{B}.changeSettings({{ ...{B}.room.settings, hands: 2, timeLimit: 0 }})')
    wait(host, 'B.room.settings.hands === 2')
    shot(host, 'game-lobby')
    host.get_by_role('button', name='開始同桌對局').click()
    for hand in range(2):
        arrange(host)
        host.evaluate(f'{B}.submit()')
        wait(host, f'B.outcomes.length === {hand + 1} && B.review.tables[{hand}]')
        shot(host, f'game-review-{hand}')
        if hand == 0:
            host.get_by_role('button', name='分析').click()
            shot(host, 'game-analysis-mode')
            host.locator('.seat-table .line.pick, .seat-table .block').nth(1).click()
            wait(host, 'B.review.detailAnalysis !== null')
            shot(host, 'game-player-analysis')
            host.go_back()
            wait(host, 'B.review.detail === null')
            host.get_by_role('button', name='下一手').click()
    host.get_by_role('button', name='最終結果').click()
    shot(host, 'game-summary')

    # ---- Leaving a lobby, and Back in a room asks first ----
    host.get_by_role('button', name='完成').click()
    host.get_by_role('button', name='開一場解題比賽').click()
    wait(host, 'B.room && B.room.phase === "LOBBY"')
    host.get_by_role('button', name='開始解題比賽').click()
    wait(host, 'B.room.phase === "PLAYING"')
    host.go_back()
    host.wait_for_timeout(300)
    print('back in a game asks', host.locator('dialog[open]').count(), host.evaluate('location.hash'))
    shot(host, 'leave-dialog')
    host.get_by_role('button', name='離開', exact=True).click()
    wait(host, 'B.room === null')
    print('left', host.evaluate('[location.hash, document.querySelector(".shell > nav").hidden]'))
    print('errors', errors)
    browser.close()
