/**
 * Generates robots.txt and sitemap.xml into public/ before ng build.
 * Prod: Allow indexing + full sitemap. Dev/local: block crawlers.
 */
const fs = require('fs');
const path = require('path');
const { resolveSeoBuildConfig } = require('./seo-build-config');

const indexHtmlPath = path.resolve(__dirname, '../src/index.html');
const faculties = require('../src/app/faculties.json');
const unitCodesPath = path.resolve(__dirname, '../src/generated/unit-codes.json');
const sitemapMetaPath = path.resolve(__dirname, '../src/generated/unit-sitemap-meta.json');
const publicDir = path.resolve(__dirname, '../public');

const { siteUrl, seoEnabled } = resolveSeoBuildConfig();

/**
 * Azure Static Web Apps answers every URL with no file behind it by rewriting to
 * the shell and returning 200 (navigationFallback, pointed at the builder's
 * index.csr.html). The shell is therefore what crawlers receive for the
 * client-rendered routes and for any address they invent, so it has to deny
 * indexing by default; SeoService opts the prerendered home and unit pages back
 * in. If this source shell ever says `index, follow` again the site silently
 * offers unbounded near-duplicates of the home page, and nothing else in the
 * build would notice, so notice here, before anything is deployed.
 * scripts/verify-seo-output.js makes the matching check on the built output.
 */
function assertShellDeniesIndexing() {
  const html = fs.readFileSync(indexHtmlPath, 'utf8');
  const robots = html.match(/<meta\s+name="robots"\s+content="([^"]*)"\s*\/?>/i);

  if (!robots) {
    throw new Error(`${indexHtmlPath} has no <meta name="robots">. The shell must deny indexing by default.`);
  }

  const directives = robots[1]
    .split(',')
    .map((d) => d.trim().toLowerCase());

  if (!directives.includes('noindex')) {
    throw new Error(
      `${indexHtmlPath} sets robots to "${robots[1]}". The static shell is served for every ` +
        'unmatched URL and must be noindex; SeoService opts the prerendered pages back in.'
    );
  }
}

assertShellDeniesIndexing();

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
  console.log(`SEO disabled: generated blocking robots.txt (no sitemap URLs) for ${siteUrl}`);
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
  // The faculty hubs. Ranked above unit pages because each one links a few
  // hundred of them, so they are the pages a crawler should reach first.
  ...faculties.map(
    (faculty) => `  <url>
    <loc>${siteUrl}/faculty/${faculty.slug}</loc>
    <lastmod>${today}</lastmod>
    <changefreq>weekly</changefreq>
    <priority>0.9</priority>
  </url>`
  ),
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

console.log(
  `SEO enabled: generated robots.txt and sitemap.xml for ${siteUrl} ` +
    `(${faculties.length} faculty hubs, ${unitCodes.length} unit URLs)`
);
