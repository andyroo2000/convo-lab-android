// Regenerate from the same MIT-licensed WanaKana 5.3.1 used by iOS.
// npm pack wanakana@5.3.1 --ignore-scripts; unpack; pass its package directory.
import { readFileSync, writeFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { resolve } from 'node:path';

const directory = process.argv[2];
if (!directory) throw Error('Pass the unpacked wanakana@5.3.1 package directory');
const source = readFileSync(resolve(directory, 'esm/index.js'), 'utf8');
const digest = createHash('sha256').update(source).digest('hex');
if (digest !== 'fa9cfd1f841e57094da55ab372d02246277ba2e2fab2c771715dc2824d4e785f') throw Error('Unexpected WanaKana source');
const module = await import('data:text/javascript;base64,' + Buffer.from(source + '\nexport { createRomajiToKanaMap as journalMap };').toString('base64'));
const custom = { nn:'ん', nna:'んな', nni:'んに', nnu:'んぬ', nne:'んね', nno:'んの', nnya:'んにゃ', nnyu:'んにゅ', nnyo:'んにょ' };
const encode = text => Buffer.from(text).toString('base64');
const prefixes = new Set();
function flatten(tree, prefix = '') {
  if (prefix) prefixes.add(prefix);
  const rows = Object.hasOwn(tree, '') ? [[encode(prefix), encode(tree[''])].join('\t')] : [];
  for (const key of Object.keys(tree).filter(key => key !== '').sort()) rows.push(...flatten(tree[key], prefix + key));
  return rows;
}
for (const ime of [false, true]) {
  const rows = flatten(module.journalMap(ime, false, custom));
  writeFileSync(`app/src/main/resources/typing/romaji-${ime ? 'ime' : 'final'}.tsv`, rows.join('\n') + '\n');
}
const phrases = ['konnichiha', 'kanpai', "shin'you", 'gakkou', 'nyuuryoku', 'NIHON', 'nn', 'nna', 'hon', 'nnyo',
  'kyouhaiitenkidesu', '日本😊', 'きょうは', 'abcxyz', 'nihongo.', 'n\n', '123', 'na ni', 'gya', 'dyu', 'tte', 'qwa'];
const inputs = new Set([...prefixes, ...phrases]);
for (const phrase of phrases) for (let end = 1; end < phrase.length; end++) inputs.add(phrase.slice(0, end));
const fixtures = [...inputs].flatMap(input => [false, true].map(final => {
  const expected = module.toHiragana(input, { IMEMode: !final && !input.endsWith('nn'), customKanaMapping: custom });
  return [encode(input), String(final), encode(expected)].join('\t');
}));
writeFileSync('app/src/test/resources/typing/romaji-golden.tsv', fixtures.join('\n') + '\n');
writeFileSync('app/src/main/assets/licenses/WanaKana-MIT.txt', readFileSync(resolve(directory, 'LICENSE')));
