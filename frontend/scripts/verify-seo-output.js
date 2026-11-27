/**
 * Post-build check on the three files that decide what search engines index.
 *
 * Azure Static Web Apps answers any URL with no file behind it by rewriting to
 * `navigationFallback.rewrite` and returning 200. Angular's prerender writes the
 * home page to index.html, so pointing the fallback there hands every invented
 * URL a fully rendered, indexable copy of the home page. It points at
 * index.csr.html instead: the bare shell, which denies indexing. Client routing
 * still resolves every route, and nothing unmatched is indexable.
 *
 * index.csr.html is emitted implicitly by the Angular builder rather than being
 * a file anyone maintains, and the fallback silently breaks the site if it ever
 * stops appearing. Nothing else in the pipeline would notice, so check here.
 */
const fs = require('fs');
const path = require('path');
const { resolveSeoBuildConfig } = require('./seo-build-config');
const faculties = require('../src/app/faculties.json');
const { FALLBACK_IMAGE_PATH } = require('../src/app/utils/share-image');
const { isUnitIndexable } = require('../src/app/utils/unit-indexability');

const outputDir = path.resolve(__dirname, '../dist/frontend/browser');
const requiredForPath = path.resolve(__dirname, '../src/generated/required-for.json');
const sitemapMetaPath = path.resolve(__dirname, '../src/generated/unit-sitemap-meta.json');

// The smallest faculty holds 79 units, so any hub below this lost its list
// rather than simply being small.
const MIN_HUB_UNIT_LINKS = 50;

// The live catalogue yields 753 units that something depends on. A run that
// lands far under that lost most of the detail fetch, and the failure is
// silent in exactly the way the hub link count is: every page still renders,
// and each one confidently tells a student the unit leads nowhere.
const MIN_UNITS_WITH_DEPENDENTS = 400;
const { seoEnabled } = resolveSeoBuildConfig();

function read(relative) {
  const file = path.join(outputDir, relative);
  if (!fs.existsSync(file)) {
    throw new Error(`Build output is missing ${relative} (looked in ${outputDir}).`);
  }
  return fs.readFileSync(file, 'utf8');
}

function robotsOf(html, label) {
  const match = html.match(/<meta\s+name="robots"\s+content="([^"]*)"\s*\/?>/i);
  if (!match) {
    throw new Error(`${label} has no <meta name="robots">.`);
  }
  return match[1].toLowerCase();
}

const config = JSON.parse(read('staticwebapp.config.json'));
const fallback = config.navigationFallback?.rewrite;

if (!fallback) {
  throw new Error('staticwebapp.config.json sets no navigationFallback.rewrite.');
}

const fallbackHtml = read(fallback.replace(/^\//, ''));

if (!robotsOf(fallbackHtml, `The navigation fallback (${fallback})`).includes('noindex')) {
  throw new Error(
    `The navigation fallback (${fallback}) is indexable. Static Web Apps serves it with a 200 for ` +
      'every unmatched URL, so an indexable fallback offers search engines an unbounded supply of ' +
      'near-duplicate pages. Point navigationFallback.rewrite at /index.csr.html.'
  );
}

/**
 * A page may claim summary_large_image only where this build wrote the file it
 * names. The claim is about the other end of a URL, and nothing else in the
 * pipeline follows it: a card pointing at a 404 renders as a blank rectangle in
 * Discord and Slack, which is worse than the square logo it replaced, and the
 * only place it shows up is someone else's chat window.
 */
function assertShareCardResolves(html, label) {
  const image = html.match(/<meta property="og:image" content="([^"]*)"/i);
  const card = html.match(/<meta name="twitter:card" content="([^"]*)"/i);

  if (!image || !card) {
    throw new Error(`${label} is missing og:image or twitter:card.`);
  }

  const claimsLarge = card[1] === 'summary_large_image';
  const isFallback = image[1].endsWith(FALLBACK_IMAGE_PATH);

  if (claimsLarge && isFallback) {
    throw new Error(
      `${label} claims summary_large_image while pointing at the square logo. ` +
        'A large card around a small mark reads as broken.'
    );
  }

  if (!claimsLarge && !isFallback) {
    throw new Error(
      `${label} generated a share card at ${image[1]} but asks for a summary card, ` +
        'so the image it made will be cropped to a square.'
    );
  }

  if (claimsLarge) {
    const file = path.join(outputDir, new URL(image[1]).pathname.replace(/^\//, ''));
    if (!fs.existsSync(file)) {
      throw new Error(
        `${label} names a share card that this build did not write: ${image[1]}. ` +
          'That URL 404s wherever the page is shared.'
      );
    }
  }

  return claimsLarge;
}

function assertIndexable(html, label) {
  const robots = robotsOf(html, label);
  if (!robots.includes('index') || robots.includes('noindex')) {
    throw new Error(`${label} says robots "${robots}". A production build must leave it indexable.`);
  }
}

// Dev builds turn SEO off wholesale and prerender nothing worth indexing, so
// only production makes these claims.
if (seoEnabled) {
  // The home page: the one prerendered route the fallback is easiest to confuse
  // with, since Angular writes it to the filename the fallback used to name.
  assertIndexable(read('index.html'), 'The prerendered home page (index.html)');
  assertShareCardResolves(read('index.html'), 'The prerendered home page (index.html)');

  // A unit page. These are the entire SEO surface, over a thousand against the
  // one home page, and the unit suite cannot cover them: updateUnitPage is
  // gated on environment.seoEnabled, which is off in the checked-in
  // environment.ts, so a test calling it takes the noIndex branch and passes
  // whatever the method does. This is the only place the opt-in gets checked.
  const unitPages = fs.existsSync(path.join(outputDir, 'units'))
    ? fs.readdirSync(path.join(outputDir, 'units'), { withFileTypes: true }).filter((e) => e.isDirectory())
    : [];

  if (unitPages.length === 0) {
    throw new Error(
      'A production build prerendered no unit pages. Check that fetch-unit-codes.js reached the API.'
    );
  }

  // Which unit pages are indexable is decided by review count, in
  // unit-indexability.js, and stated twice: by the robots tag on each page and
  // by the sitemap's list. Hold every prerendered unit page and the sitemap to
  // that one rule, so the two can never tell a search engine different things.
  const sitemapMeta = fs.existsSync(sitemapMetaPath)
    ? JSON.parse(fs.readFileSync(sitemapMetaPath, 'utf8'))
    : [];
  const reviewsByCode = Object.fromEntries(sitemapMeta.map((e) => [e.code, e.reviews]));
  const prerenderedCodes = new Set(unitPages.map((e) => e.name));
  const reviewedCodes = [...prerenderedCodes].filter((code) => isUnitIndexable(reviewsByCode[code]));

  if (reviewedCodes.length === 0) {
    throw new Error(
      'No prerendered unit carries a review, so no unit page would be indexable. The units ' +
        'listing came back without review counts: check that fetch-unit-codes.js reached the API.'
    );
  }

  for (const code of prerenderedCodes) {
    const page = `units/${code}/index.html`;
    const html = read(page);
    const robots = robotsOf(html, `The prerendered unit page (${page})`);

    if (isUnitIndexable(reviewsByCode[code])) {
      assertIndexable(html, `The reviewed unit page (${page})`);
    } else if (!robots.includes('noindex') || robots.includes('nofollow')) {
      throw new Error(
        `The unit page ${page} has no reviews and says robots "${robots}". It must say ` +
          '"noindex, follow": out of the index, with its hub and prerequisite links still followed.'
      );
    }
  }

  const sample = `units/${reviewedCodes[0]}/index.html`;
  assertShareCardResolves(read(sample), `The prerendered unit page (${sample})`);

  const sitemapUnitCodes = [...read('sitemap.xml').matchAll(/<loc>[^<]*\/units\/([^<]+)<\/loc>/g)].map(
    (m) => decodeURIComponent(m[1])
  );
  const unlisted = reviewedCodes.filter((code) => !sitemapUnitCodes.includes(code));
  const unearned = sitemapUnitCodes.filter((code) => !isUnitIndexable(reviewsByCode[code]));

  if (unlisted.length > 0 || unearned.length > 0) {
    throw new Error(
      `The sitemap and the unit pages disagree about what is indexable. Reviewed units missing ` +
        `from the sitemap: ${unlisted.slice(0, 10).join(', ') || 'none'}. Sitemap entries for units ` +
        `without reviews: ${unearned.slice(0, 10).join(', ') || 'none'}.`
    );
  }

  // The reverse prerequisite graph. It is the only content on the 1,729 units
  // with no reviews, and fetch-unit-codes.js writes an empty map rather than
  // failing the build when the detail fetch does not come back.
  const requiredFor = fs.existsSync(requiredForPath)
    ? JSON.parse(fs.readFileSync(requiredForPath, 'utf8'))
    : {};
  const unitsWithDependents = Object.keys(requiredFor).length;

  if (unitsWithDependents < MIN_UNITS_WITH_DEPENDENTS) {
    throw new Error(
      `The reverse prerequisite graph holds ${unitsWithDependents} units that something depends on. ` +
        `Fewer than ${MIN_UNITS_WITH_DEPENDENTS} means the per-unit prerequisite fetch mostly failed, ` +
        'and every unit page would state that nothing requires it. Check that fetch-unit-codes.js ' +
        'reached the API.'
    );
  }

  // The faculty hubs are the only pages that link the whole catalogue, and the
  // link count is the part that can fail quietly: a hub that rendered its
  // heading but lost its list looks like a normal page to every other check
  // here, while leaving several hundred units with no internal link at all.
  let hubLinks = 0;

  for (const faculty of faculties) {
    const page = `faculty/${faculty.slug}/index.html`;
    const html = read(page);
    assertIndexable(html, `The ${faculty.name} hub (${page})`);
    assertShareCardResolves(html, `The ${faculty.name} hub (${page})`);

    const links = new Set(html.match(/href="\/units\/[^"]+"/g) || []);
    if (links.size < MIN_HUB_UNIT_LINKS) {
      throw new Error(
        `The ${faculty.name} hub (${page}) links ${links.size} units. A hub that lists fewer than ` +
          `${MIN_HUB_UNIT_LINKS} did not get its catalogue: check that the units request reached the API ` +
          'and was not truncated by a page size.'
      );
    }
    hubLinks += links.size;
  }

  console.log(
    `SEO output verified: fallback ${fallback} is noindex; home page, ${reviewedCodes.length} reviewed ` +
      `unit pages of ${unitPages.length} and ${faculties.length} faculty hubs indexable, matching the ` +
      `sitemap; hubs link ${hubLinks} units; ` +
      `${unitsWithDependents} units name what depends on them.`
  );
} else {
  console.log(`SEO output verified: fallback ${fallback} is noindex. SEO is off for this build.`);
}
