const { chromium } = require('playwright');
(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1280, height: 720 }, deviceScaleFactor: 1 });
  await page.goto('file://' + __dirname + '/scene.html');
  const fps = 24, seconds = 10;
  for (let i = 0; i < fps * seconds; i++) {
    await page.evaluate((t) => window.renderAt(t), i / fps);
    await page.locator('#c').screenshot({ path: `${__dirname}/frames/f${String(i).padStart(4, '0')}.png` });
  }
  await browser.close();
})();
