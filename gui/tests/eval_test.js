// Checks gui/thirteen-eval.js against the C++ engine: node gui/tests/eval_test.js cpp/build/thirteen
"use strict";
const { execFileSync } = require("child_process");
const E = require("../thirteen-eval.js");

const binary = process.argv[2];
if (!binary) throw new Error("usage: node eval_test.js PATH_TO_THIRTEEN");

let seed = 12345;
function rand(n) { seed = (seed * 1103515245 + 12345) % 2147483648; return seed % n; }
function hand(n) {
  const deck = [...Array(52).keys()];
  for (let i = 51; i > 0; i--) { const j = rand(i + 1); [deck[i], deck[j]] = [deck[j], deck[i]]; }
  return deck.slice(0, n);
}

const hands = [];
for (let i = 0; i < 20000; i++) hands.push(hand(i % 2 ? 5 : 3));
// Straights, flushes and full houses are rare in random hands; add some.
for (const s of ["As 2d 3c 4h 5s", "Ts Js Qs Ks As", "2h 3h 4h 5h 6h", "9c 9d 9h 2s 2c", "Kc Kd Ks Kh 3d",
  "Qs Kd Ac 2h 3s", "Ah 9h 7h 4h 2h", "8s 8h Kd", "2s 2h 2d"]) hands.push(s.split(" ").map(E.parseCard));
const input = hands.map((h) => h.map(E.cardToString).join(" ")).join("\n") + "\n";
const expected = execFileSync(binary, ["evalkeys"], { input }).toString().trim().split("\n").map(Number);
let bad = 0;
hands.forEach((h, i) => {
  const key = h.length === 5 ? E.eval5(h) : E.eval3(h);
  if (key !== expected[i]) {
    if (bad < 5) console.log("Mismatch", h.map(E.cardToString).join(" "), key, expected[i]);
    bad++;
  }
});
console.log(bad === 0 ? `OK: ${hands.length} hands match` : `${bad} mismatches`);
process.exit(bad === 0 ? 0 : 1);
