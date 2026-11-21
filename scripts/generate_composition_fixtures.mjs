// Generate behavior fixtures from the stable iOS state machine, without inference.
import { writeFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';
const ios = process.argv[2], wanakana = process.argv[3];
if (!ios || !wanakana) throw Error('Pass the iOS reference checkout and unpacked WanaKana package');
const { JournalNativeSession } = await import(pathToFileURL(resolve(ios, 'TypingPlaygroundWeb/journal-native-session.mjs')));
const { toHiragana } = await import(pathToFileURL(resolve(wanakana, 'cjs/index.js')));
const b64 = value => Buffer.from(String(value)).toString('base64');
const attr = object => Object.entries(object).map(([key, value]) => `${key}="${b64(value)}"`).join(' ');
const part = (text, reading) => ({ text, reading });
const phrase = { text:'今日はいい天気です', reading:'きょうはいいてんきです',
  sections:[part('今日は','きょうは'),part('いい','いい'),part('天気','てんき'),part('です','です')],
  units:[part('今日','きょう'),part('は','は'),part('いい','いい'),part('天気','てんき'),part('です','です')] };
function candidateXml(candidate) {
  const parts = ['sections','units'].map(kind => `<${kind}>${candidate[kind].map(p => `<part ${attr(p)}/>`).join('')}</${kind}>`).join('');
  return `<candidate ${attr({text:candidate.text,reading:candidate.reading})}>${parts}</candidate>`;
}
function scenario(name, perform) {
  const session = new JournalNativeSession(toHiragana);
  const events = [];
  function execute(op) {
    let snapshot;
    const before = session.snapshot();
    switch (op.kind) {
      case 'edit': snapshot = session.edit({text:before.text,range:[op.start,op.length],replacement:op.replacement,selection:[op.sel,op.selLength],literal:op.literal}); break;
      case 'receive': snapshot = session.receive(op.reading,{candidates:op.candidates}); break;
      case 'move': snapshot = session.moveSelection([op.start,op.length]); break;
      case 'external': snapshot = session.external(op.text); break;
      default: snapshot = session[op.kind](op.value);
    }
    const {candidates,...attributes} = op;
    const expected = {text:snapshot.text,caret:snapshot.caret,composition:(snapshot.composition??[]).join(','),section:(snapshot.section??[]).join(','),reading:snapshot.reading,highlighted:snapshot.highlighted,boundaries:snapshot.boundaries.join(',')};
    events.push(`<event ${attr(attributes)}>${(candidates??[]).map(candidateXml).join('')}<expected ${attr(expected)}>${snapshot.choices.map(text=>`<choice ${attr({text})}/>`).join('')}</expected></event>`);
    return snapshot;
  }
  const append = (replacement,literal=false) => {const s=session.snapshot();return execute({kind:'edit',start:s.caret,length:0,replacement,sel:s.caret,selLength:0,literal});};
  const type = value => {for (const letter of value) append(letter);};
  perform({session,execute,append,type});
  return `<scenario ${attr({name})}>${events.join('\n')}</scenario>`;
}
const cases = [
  scenario('typing and explicit acceptance',({type,execute,append})=>{type('konnichiha');execute({kind:'commit'});append('\n');type('nihongo');execute({kind:'receive',reading:'にほんご',candidates:[{text:'日本語',reading:'にほんご',sections:[part('日本語','にほんご')],units:[part('日本語','にほんご')]}]});execute({kind:'choose',value:0});}),
  scenario('section resize and contextual remainder',({type,execute})=>{type('kyouhaiitenkidesu');execute({kind:'receive',reading:phrase.reading,candidates:[phrase]});execute({kind:'focusSection',value:1});execute({kind:'resize',value:-1});execute({kind:'choose',value:0});execute({kind:'receive',reading:phrase.reading,candidates:[phrase]});execute({kind:'choose',value:0});}),
  scenario('paste emoji and literal English',({type,append,execute})=>{type('hon');append('📖',true);append(' English!',true);execute({kind:'move',start:0,length:0});type('nihon');execute({kind:'commit'});}),
  scenario('editing inside a combined sound',({type,execute})=>{type('kyaku');execute({kind:'move',start:1,length:1});execute({kind:'edit',start:1,length:1,replacement:'i',sel:1,selLength:1,literal:false});execute({kind:'edit',start:1,length:1,replacement:'',sel:2,selLength:0,literal:false});execute({kind:'commit'});}),
  scenario('space enter and stale inference',({type,execute,append})=>{type('nihongo');execute({kind:'receive',reading:'にほんご',candidates:[{text:'日本語',reading:'にほんご',sections:[part('日本語','にほんご')],units:[part('日本語','にほんご')]}]});append(' ');append('\n');append('\n');type('gakkou');execute({kind:'receive',reading:'にほんご',candidates:[]});execute({kind:'move',start:0,length:0});execute({kind:'external',text:'別の日記😊'});})
];
writeFileSync('app/src/test/resources/typing/composition-golden.xml',`<fixtures>${cases.join('\n')}</fixtures>\n`);
