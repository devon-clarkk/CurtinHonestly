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

const outputDir = path.resolve(__dirname, '../dist/frontend/browser');
const requiredForPath = path.resolve(__dirname, '../src/generated/required-for.json');

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

  const sample = `units/${unitPages[0].name}/index.html`;
  assertIndexable(read(sample), `The prerendered unit page (${sample})`);

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
    `SEO output verified: fallback ${fallback} is noindex; home page, ${unitPages.length} unit pages ` +
      `and ${faculties.length} faculty hubs indexable; hubs link ${hubLinks} units; ` +
      `${unitsWithDependents} units name what depends on them.`
  );
} else {
  console.log(`SEO output verified: fallback ${fallback} is noindex. SEO is off for this build.`);
}
