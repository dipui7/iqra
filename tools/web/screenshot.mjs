// Opens the web app in headless Chromium, waits for it to draw, and saves screenshots at a
// phone size and a laptop size, plus what the page logged. Used by CI as a smoke test.
//   node tools/web/screenshot.mjs http://localhost:8080/app/ out-dir
//
// It also prints, for each screenshot, a small JPEG preview as base64 between
// "PREVIEW <name> BEGIN/END" lines, so the result can be seen from the build log alone.
import { chromium } from 'playwright';
import fs from 'fs';

const [url, out = 'web-screens'] = process.argv.slice(2);
fs.mkdirSync(out, { recursive: true });
const log = [];
let notFound = 0;
const browser = await chromium.launch();

async function shoot(page, name) {
  await page.screenshot({ path: `${out}/${name}.png` });
  const preview = await page.screenshot({ type: 'jpeg', quality: 45, scale: 'css' });
  console.log(`PREVIEW ${name} BEGIN`);
  const b64 = preview.toString('base64');
  for (let i = 0; i < b64.length; i += 3000) console.log(b64.slice(i, i + 3000));
  console.log(`PREVIEW ${name} END`);
}

for (const [name, viewport] of [['phone', { width: 390, height: 844 }], ['laptop', { width: 1280, height: 800 }]]) {
  const page = await browser.newPage({ viewport, deviceScaleFactor: 1 });
  page.on('console', m => {
    if (m.type() === 'error' && m.text().includes('404')) { notFound++; return; }
    log.push(`[${name}] ${m.type()}: ${m.text()}`);
  });
  page.on('pageerror', e => log.push(`[${name}] PAGE ERROR: ${e.message}`));
  const started = Date.now();
  await page.goto(url, { waitUntil: 'load' });
  // The loading message is removed once the app starts; then give it time to load the Quran text and draw.
  const ok = await page.waitForFunction(() => !document.getElementById('loading'), null, { timeout: 60000 })
    .then(() => true).catch(() => false);
  log.push(`[${name}] ${ok ? `app started after ${((Date.now() - started) / 1000).toFixed(1)} s` : 'APP DID NOT START'}`);
  await page.waitForTimeout(8000);
  await shoot(page, `${name}-home`);
  await page.close();
}
await browser.close();
log.push(`404 responses (missing optional files): ${notFound}`);
fs.writeFileSync(`${out}/console.txt`, log.join('\n') + '\n');
console.log('SUMMARY BEGIN\n' + log.join('\n') + '\nSUMMARY END');
