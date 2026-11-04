// Opens the web app in headless Chromium, waits for it to draw, and saves screenshots at a
// phone size and a laptop size, plus anything the page logged. Used by CI as a smoke test.
//   node tools/web/screenshot.mjs http://localhost:8080/app/ out-dir
import { chromium } from 'playwright';
import fs from 'fs';

const [url, out = 'web-screens'] = process.argv.slice(2);
fs.mkdirSync(out, { recursive: true });
const log = [];
const browser = await chromium.launch();
for (const [name, viewport] of [['phone', { width: 390, height: 844 }], ['laptop', { width: 1280, height: 800 }]]) {
  const page = await browser.newPage({ viewport, deviceScaleFactor: 2 });
  page.on('console', m => log.push(`[${name}] ${m.type()}: ${m.text()}`));
  page.on('pageerror', e => log.push(`[${name}] pageerror: ${e.message}`));
  await page.goto(url, { waitUntil: 'load' });
  // The loading message is removed once the app starts; then give it time to load the Quran text and draw.
  await page.waitForFunction(() => !document.getElementById('loading'), null, { timeout: 60000 }).catch(() => log.push(`[${name}] app did not start`));
  await page.waitForTimeout(8000);
  await page.screenshot({ path: `${out}/${name}-home.png` });
  // Tap the middle of the screen's first lesson area to get past the home screen, then screenshot again.
  await page.mouse.click(viewport.width / 2, viewport.height * 0.62);
  await page.waitForTimeout(3000);
  await page.screenshot({ path: `${out}/${name}-after-tap.png` });
  await page.close();
}
await browser.close();
fs.writeFileSync(`${out}/console.txt`, log.join('\n') + '\n');
console.log(log.join('\n'));
