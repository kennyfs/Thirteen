# Thirteen on the web

The app's practice, analysis, records, settings and online battles (not nearby ones) as a static web page. The engine is the repository's
C++ (`cpp/`, through `GuiEngine`) built as WebAssembly with a reduced ONNX Runtime, and it runs in a Web Worker in the
browser, so nothing is sent anywhere. The front end is Svelte 5 and TypeScript, built with Vite. It works on a phone (a
bar of screens at the bottom) and on a desktop (a rail at the side), with the same layouts and card sizes as the app
(`src/lib/sizing.ts` ports `Sizing.kt`).

| Path | What |
|---|---|
| `engine/` | `engine.cpp` (`engine_handle`: a request line in, an answer line out) and its CMake build for emscripten. |
| `build-ort-wasm.sh` | Builds the reduced ONNX Runtime (the app's version and operators, `android/ort-ops.config`) as a WebAssembly static library. |
| `build-engine.sh` | Builds the engine into `public/engine/` and copies the built-in network into `public/models/`. |
| `scripts/strings.mjs` | The app's strings (`android/.../res/values*/strings.xml`) to `src/generated/strings.json`; run by `npm run dev` and `npm run build`. |
| `scripts/shots.py` | Screenshots in headless Firefox (playwright) at phone and desktop sizes. |
| `scripts/desktest.py`, `scripts/tutest.py` | A computer's mouse and keys; the welcome card, tutorial, tour, last hand, sweeps and the cursor keys (playwright). |
| `src/lib/` | The state (`app.svelte.ts` ports `AppViewModel.kt`), arranging, rules, cards, the engine's client and worker, storage. |
| `src/components/` | The screens and their parts. |
| `tests/engine-native.mjs` | Checks the WebAssembly engine's answers against the native `thirteen gui`. |

## Building

Needs Node.js 22+, and for the engine cmake, ninja, python3 and [emsdk](https://emscripten.org/) 6.0.13.

```bash
cd web
npm ci
# Once (an hour or so): ONNX Runtime for WebAssembly, into build/ort-wasm.
EMSDK=/path/to/emsdk ./build-ort-wasm.sh
# The engine, into public/engine (and the network into public/models).
source /path/to/emsdk/emsdk_env.sh && ./build-engine.sh
npm run build      # the site, into dist/
npm run preview    # serves dist/ at http://localhost:4173/
```

In Docker, `server/Dockerfile`'s target `site` builds all of this and serves it with Caddy, beside the battle server
(docs/battle.md, Running a server).

`npm run dev` serves the page with hot reload instead. `dist/` is a static site with relative paths: any web server
serves it, from any directory. The server should send `.wasm` as `application/wasm`.

`npm test -- path/to/thirteen` compares the engine with a native build of `thirteen` (`cpp/`).

## What is kept

Everything stays in the browser: the settings, the running scores, the records and saved hands in `localStorage`
(the records as JSONL, the app's format, labelled `web`), and networks loaded from `.onnx` files in IndexedDB. The
records page saves the records as a file.

## Screenshots

```bash
npm run preview &
python scripts/shots.py http://localhost:4173/ /tmp/shots            # all sizes
python scripts/shots.py http://localhost:4173/ /tmp/shots phone,desktop
```

The page opened with `?test` exposes `window.thirteen = {app, engine}`, which the script uses to finish a hand.
