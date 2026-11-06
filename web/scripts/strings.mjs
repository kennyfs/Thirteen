// The page's words are the Android app's: this reads android/app/src/main/res/values*/strings.xml into
// src/generated/strings.json ({"en": {...}, "zh": {...}}, a name to a string or a string array), so the two say the same.
// Formats stay as Android writes them (%1$s, %2$d); src/lib/i18n.ts fills them in.
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const res = join(root, '..', 'android', 'app', 'src', 'main', 'res');

// As Android reads a value: entities, then backslash escapes; an unescaped " only quotes (it keeps the spaces inside
// it) and is dropped; outside quotes, runs of white space are one space.
function unescape(s) {
  s = s.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&amp;/g, '&');
  let out = '';
  let quoted = false;
  for (let i = 0; i < s.length; i++) {
    const c = s[i];
    if (c === '\\' && i + 1 < s.length) {
      const n = s[++i];
      if (n === 'u' && /^[0-9a-fA-F]{4}$/.test(s.slice(i + 1, i + 5))) { out += String.fromCharCode(parseInt(s.slice(i + 1, i + 5), 16)); i += 4; }
      else out += n === 'n' ? '\n' : n === 't' ? '\t' : n;
    } else if (c === '"') quoted = !quoted;
    else if (!quoted && /\s/.test(c)) { if (!out.endsWith(' ')) out += ' '; }
    else out += c;
  }
  return out;
}

function read(file) {
  const xml = readFileSync(file, 'utf8').replace(/<!--[\s\S]*?-->/g, '');
  const out = {};
  for (const m of xml.matchAll(/<string\s+name="([^"]+)"[^>]*>([\s\S]*?)<\/string>/g)) out[m[1]] = unescape(m[2].trim());
  for (const m of xml.matchAll(/<string-array\s+name="([^"]+)"[^>]*>([\s\S]*?)<\/string-array>/g))
    out[m[1]] = [...m[2].matchAll(/<item>([\s\S]*?)<\/item>/g)].map((i) => unescape(i[1].trim()));
  return out;
}

const en = read(join(res, 'values', 'strings.xml'));
const zh = { ...en, ...read(join(res, 'values-zh-rTW', 'strings.xml')) };
mkdirSync(join(root, 'src', 'generated'), { recursive: true });
writeFileSync(join(root, 'src', 'generated', 'strings.json'), JSON.stringify({ en, zh }) + '\n');
console.log(`strings: ${Object.keys(en).length} names`);
