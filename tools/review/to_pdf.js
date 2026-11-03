// Print docs/teacher-review.html to docs/teacher-review.pdf with headless Chromium (Playwright).
const { chromium } = require('playwright');
const path = require('path');
(async () => {
  const docs = path.join(__dirname, '..', '..', 'docs');
  const browser = await chromium.launch();
  const page = await browser.newPage();
  await page.goto('file://' + path.join(docs, 'teacher-review.html'));
  await page.evaluate(() => document.fonts.ready);
  await page.pdf({ path: path.join(docs, 'teacher-review.pdf'), format: 'A4', printBackground: true,
    margin: { top: '14mm', bottom: '14mm', left: '12mm', right: '12mm' } });
  await browser.close();
  console.log('wrote docs/teacher-review.pdf');
})();
