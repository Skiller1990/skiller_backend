const fs = require('fs');
const puppeteer = require('puppeteer');

(async () => {
  try {
    const htmlPath = process.argv[2];
    if (!htmlPath) {
      console.error('Usage: node render-pdf.js <htmlFilePath>');
      process.exit(2);
    }
    const html = fs.readFileSync(htmlPath, 'utf8');
    const browser = await puppeteer.launch({ args: ['--no-sandbox','--disable-setuid-sandbox'] });
    const page = await browser.newPage();
    await page.setContent(html, { waitUntil: 'networkidle0' });
    const pdfBuffer = await page.pdf({ format: 'A4', landscape: true, printBackground: true, margin: { top: '15mm', bottom: '15mm', left: '15mm', right: '15mm' } });
    await browser.close();
    // Output base64 to stdout
    process.stdout.write(pdfBuffer.toString('base64'));
    process.exit(0);
  } catch (err) {
    console.error(err && err.stack || err);
    process.exit(1);
  }
})();
