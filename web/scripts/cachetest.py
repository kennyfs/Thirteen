#!/usr/bin/env python3
"""The engine's and the network's caching (src/lib/stored.ts), in headless Firefox and Chromium (playwright).

    python scripts/cachetest.py URL first|again

`first`: a new browser profile loads the page: the hashed engine and network come from /assets, Cache Storage then
holds the WebAssembly and the network; loaded again with the HTTP cache off (playwright's routing turns it off), neither
is downloaded, and a hand and an analysis work. The profiles are kept (in $CACHETEST_DIR, by default
/tmp/thirteen-cachetest) for `again`, after the page is built anew with another engine: the new files load, and Cache
Storage holds them and no longer the old ones.
"""
import json
import os
import sys
from pathlib import Path

from playwright.sync_api import sync_playwright

url, mode = sys.argv[1], sys.argv[2]
root = Path(os.environ.get('CACHETEST_DIR', '/tmp/thirteen-cachetest'))
errors = []


def check(name, ok, got=''):
    print(('ok  ' if ok else 'FAIL') + f' {name} {got}')
    if not ok:
        errors.append(name)


PLAY = """async () => {
  const { app, engine } = window.thirteen;
  const rows = await engine.arrange('indep', app.play.hand, app.gameRules);
  app.play.reset(app.play.hand, app.gameRules, rows);
  app.confirm();
  const hand = (await engine.deal())[0];
  const a = await engine.analyze(hand, 'family', engine.BUILT_IN, []);
  return a.cands.length;
}"""
STORED = """async () => {
  try { return (await (await caches.open('thirteen-engine')).keys()).map((r) => r.url.replace(/.*\\//, '')); }
  catch (e) { return 'no Cache Storage: ' + e; }
}"""
BIG = ('.wasm', '.onnx')


def load(ctx, name):
    """Opens the page in `ctx`, plays a hand and analyzes one; the engine's and network's downloads, and what Cache
    Storage holds after."""
    got = []
    ctx.on('request', lambda r: got.append(r.url.split('/')[-1]))
    page = ctx.new_page()
    page.on('pageerror', lambda e: errors.append(f'{name}: pageerror: {e}'))
    page.goto(url + '?test')
    page.wait_for_function('window.thirteen && window.thirteen.app.ready', timeout=60000)
    cands = page.evaluate(PLAY)
    page.wait_for_function('window.thirteen.app.result != null', timeout=60000)
    page.wait_for_timeout(500)  # the stores are not awaited
    stored = page.evaluate(STORED)
    page.close()
    return [f for f in got if f.endswith(BIG) or f.startswith('engine-')], stored, cands


with sync_playwright() as p:
    for kind in (p.firefox, p.chromium):
        name = kind.name
        ctx = kind.launch_persistent_context(str(root / name), headless=True)
        # Routing turns the HTTP cache off: every file comes from the server, or from Cache Storage.
        ctx.route('**/*', lambda r: r.continue_())
        if mode == 'first':
            files, stored, cands = load(ctx, name)
            print(name, 'first load', files, 'stored', stored)
            check(f'{name}: the hashed engine and network from /assets', sum(f.endswith(BIG) for f in files) == 2 and any(f.startswith('engine-') and f.endswith('.js') for f in files), files)
            check(f'{name}: Cache Storage holds the wasm and the network', isinstance(stored, list) and sorted(f[-4:] for f in stored) == ['onnx', 'wasm'], stored)
            check(f'{name}: a hand and an analysis', cands > 0, cands)
            (root / f'{name}.json').write_text(json.dumps(stored))
            files, stored2, cands = load(ctx, name)
            print(name, 'reload', files, 'stored', stored2)
            check(f'{name}: reloaded without the HTTP cache, neither downloaded', not any(f.endswith(BIG) for f in files), files)
            check(f'{name}: and it works', cands > 0, cands)
        else:
            old = json.loads((root / f'{name}.json').read_text())
            files, stored, cands = load(ctx, name)
            print(name, 'new build', files, 'stored', stored, 'before', old)
            check(f'{name}: the new engine downloaded', any(f.endswith('.wasm') and f not in old for f in files), files)
            check(f'{name}: Cache Storage holds the new files, not the old', isinstance(stored, list) and len(stored) == 2 and not (set(stored) & set(old) - {f for f in stored if f.endswith('.onnx')}), stored)
            check(f'{name}: it works', cands > 0, cands)
        ctx.close()

print('errors', errors)
sys.exit(1 if errors else 0)
