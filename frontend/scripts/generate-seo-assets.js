/**
 * Generates robots.txt and sitemap.xml into public/ before ng build.
 * Prod: Allow indexing + full sitemap. Dev/local: block crawlers.
 */
const fs = require('fs');
const path = require('path');
const { resolveSeoBuildConfig } = require('./seo-build-config');

const unitCodesPath = path.resolve(__dirname, '../src/generated/unit-codes.json');
const sitemapMetaPath = path.resolve(__dirname, '../src/generated/unit-sitemap-meta.json');
const publicDir = path.resolve(__dirname, '../public');

const { siteUrl, seoEnabled } = resolveSeoBuildConfig();

let unitCodes = [];
if (fs.existsSync(unitCodesPath)) {
  unitCodes = JSON.parse(fs.readFileSync(unitCodesPath, 'utf8'));
}

let sitemapMeta = [];
if (fs.existsSync(sitemapMetaPath)) {
  sitemapMeta = JSON.parse(fs.readFileSync(sitemapMetaPath, 'utf8'));
}

const sitemapMetaByCode = Object.fromEntries(sitemapMeta.map((e) => [e.code, e]));

fs.mkdirSync(publicDir, { recursive: true });

if (!seoEnabled) {
  const robotsTxt = `User-agent: *
Disallow: /
`;

  fs.writeFileSync(path.join(publicDir, 'robots.txt'), robotsTxt);
  fs.writeFileSync(
    path.join(publicDir, 'sitemap.xml'),
    `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n</urlset>\n`
  );
  console.log(`SEO disabled — generated blocking robots.txt (no sitemap URLs) for ${siteUrl}`);
  return;
}

const today = new Date().toISOString().slice(0, 10);

const robotsTxt = `User-agent: *
Allow: /

Sitemap: ${siteUrl}/sitemap.xml
`;

const urlEntries = [
  `  <url>
    <loc>${siteUrl}/</loc>
    <lastmod>${today}</lastmod>
    <changefreq>weekly</changefreq>
    <priority>1.0</priority>
  </url>`,
  ...unitCodes.map((code) => {
    const lastmod = sitemapMetaByCode[code]?.lastmod || today;
    return `  <url>
    <loc>${siteUrl}/units/${encodeURIComponent(code)}</loc>
    <lastmod>${lastmod}</lastmod>
    <changefreq>weekly</changefreq>
    <priority>0.8</priority>
  </url>`;
  }),
];

const sitemapXml = `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
${urlEntries.join('\n')}
</urlset>
`;

fs.writeFileSync(path.join(publicDir, 'robots.txt'), robotsTxt);
fs.writeFileSync(path.join(publicDir, 'sitemap.xml'), sitemapXml);

console.log(`SEO enabled — generated robots.txt and sitemap.xml for ${siteUrl} (${unitCodes.length} unit URLs)`);
