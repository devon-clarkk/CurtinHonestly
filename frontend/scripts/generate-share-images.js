/**
 * Renders every page's 1200x630 share card into public/assets/og/ before
 * `ng build`, which copies public/ verbatim into the browser output.
 *
 * One card per unit, per faculty hub, and per info page: 1,769 in all. Cards
 * are generated for the whole catalogue rather than for a chosen subset,
 * because that makes the rule total. SeoService can then name a unit's card
 * from its code alone, with no manifest of which units happen to have one, and
 * no way for the two sides to disagree about a given page.
 *
 * The renderer is @resvg/resvg-js: it rasterises SVG with fonts supplied as
 * files, which is what makes the output reproducible. System fonts are turned
 * off deliberately. With them on, the card would depend on what happens to be
 * installed on the machine running the build, and the same commit would
 * produce different bytes on CI and on a laptop.
 */
const fs = require('fs');
const path = require('path');
const { Resvg } = require('@resvg/resvg-js');
const { resolveSeoBuildConfig } = require('./seo-build-config');
const { loadFontMetrics } = require('./lib/font-metrics');
const { encodeIndexedPng } = require('./lib/indexed-png');
const {
  FONT_FILES,
  DISPLAY_FONT,
  formatCount,
  unitCardSvg,
  facultyCardSvg,
  infoCardSvg,
} = require('./lib/share-card');
const {
  SHARE_IMAGE_DIR,
  INFO_CARD_NAMES,
  unitShareImagePath,
  facultyShareImagePath,
  infoShareImagePath,
} = require('../src/app/utils/share-image');
const faculties = require('../src/app/faculties.json');

const publicDir = path.resolve(__dirname, '../public');
const outputDir = path.join(publicDir, SHARE_IMAGE_DIR);
const fontDir = path.join(publicDir, 'assets/fonts');
const shareMetaPath = path.resolve(__dirname, '../src/generated/unit-share-meta.json');

const { seoEnabled } = resolveSeoBuildConfig();

/**
 * Removes cards from a previous run before writing this one.
 *
 * Without this a unit that leaves the catalogue leaves its card behind, and the
 * orphans accumulate across local builds into deploy weight nothing references.
 */
function resetOutputDir() {
  fs.rmSync(outputDir, { recursive: true, force: true });
  fs.mkdirSync(path.join(outputDir, 'units'), { recursive: true });
  fs.mkdirSync(path.join(outputDir, 'faculty'), { recursive: true });
}

function loadMetrics() {
  return {
    displayBold: loadFontMetrics(path.join(fontDir, FONT_FILES.displayBold)),
    displayRegular: loadFontMetrics(path.join(fontDir, FONT_FILES.displayRegular)),
    name: loadFontMetrics(path.join(fontDir, FONT_FILES.name)),
  };
}

/**
 * The .ttf and .otf sources are used rather than the .woff2 the browser gets:
 * those are subset to the characters the site renders, and resvg reads the
 * uncompressed formats directly.
 */
function renderOptions() {
  return {
    font: {
      fontFiles: Object.values(FONT_FILES).map((file) => path.join(fontDir, file)),
      loadSystemFonts: false,
      defaultFontFamily: DISPLAY_FONT,
    },
  };
}

function main() {
  if (!seoEnabled) {
    // A dev build prerenders nothing worth sharing, and SeoService never claims
    // a card on that build either, so leaving the directory empty keeps the two
    // consistent rather than shipping cards no page references.
    fs.rmSync(outputDir, { recursive: true, force: true });
    console.log('SEO disabled for this build: skipping share card generation.');
    return;
  }

  if (!fs.existsSync(shareMetaPath)) {
    throw new Error(
      `${shareMetaPath} is missing. scripts/fetch-unit-codes.js writes it and must run first.`
    );
  }

  const units = JSON.parse(fs.readFileSync(shareMetaPath, 'utf8'));

  if (units.length === 0) {
    throw new Error(
      'The share card source holds no units. Every unit page would fall back to the logo, so ' +
        'this fails here rather than shipping a build with no cards. Check that ' +
        'fetch-unit-codes.js reached the API.'
    );
  }

  resetOutputDir();

  const metrics = loadMetrics();
  const options = renderOptions();
  const started = Date.now();
  let bytes = 0;
  let written = 0;
  let fullColour = 0;
  const skipped = [];

  function write(relativePath, svg) {
    const rendered = new Resvg(svg, options).render();
    // The palette encoding is lossless for these cards and about a third of the
    // size. A card that somehow could not be one keeps resvg's own output.
    const indexed = encodeIndexedPng(rendered.pixels, rendered.width, rendered.height);
    if (!indexed) {
      fullColour += 1;
    }
    const png = indexed ?? rendered.asPng();
    const file = path.join(publicDir, relativePath.replace(/^\//, ''));
    fs.writeFileSync(file, png);
    bytes += png.length;
    written += 1;
  }

  for (const unit of units) {
    const imagePath = unitShareImagePath(unit.code);
    if (!imagePath) {
      // Nothing in the live catalogue is shaped this way. If something ever is,
      // the page keeps the logo and `summary`, which is a plain small card
      // rather than a large one pointing at a file that was never written.
      skipped.push(unit.code);
      continue;
    }
    write(imagePath, unitCardSvg(unit, metrics));
  }

  const unitCountByFaculty = new Map();
  for (const unit of units) {
    unitCountByFaculty.set(unit.faculty, (unitCountByFaculty.get(unit.faculty) ?? 0) + 1);
  }

  for (const faculty of faculties) {
    const imagePath = facultyShareImagePath(faculty.slug);
    if (!imagePath) {
      throw new Error(`Faculty slug "${faculty.slug}" has no valid card path.`);
    }
    write(
      imagePath,
      facultyCardSvg(
        { name: faculty.name, unitCount: unitCountByFaculty.get(faculty.name) ?? 0 },
        metrics
      )
    );
  }

  const infoPages = {
    home: {
      heading: 'Curtin University Unit Reviews',
      lead: `Honest student reviews for ${formatCount(units.length)} units`,
      support: 'Ratings, workload, grades and would-take-again scores',
    },
    about: {
      heading: 'About CurtinHonestly',
      lead: 'Who runs this site, and how reviews are handled',
      support: 'Independent, and not affiliated with Curtin University',
    },
    contact: {
      heading: 'Contact CurtinHonestly',
      lead: 'Report a review, or correct a unit',
      support: 'Privacy requests, societies and media enquiries',
    },
  };

  for (const name of INFO_CARD_NAMES) {
    const imagePath = infoShareImagePath(name);
    if (!imagePath) {
      throw new Error(`Info card "${name}" has no valid card path.`);
    }
    write(imagePath, infoCardSvg(infoPages[name], metrics));
  }

  const seconds = ((Date.now() - started) / 1000).toFixed(1);
  const averageKb = (bytes / written / 1024).toFixed(1);
  const totalMb = (bytes / 1024 / 1024).toFixed(1);

  console.log(
    `Generated ${formatCount(written)} share cards in ${seconds}s ` +
      `(${averageKb} kB average, ${totalMb} MB total) into public/${SHARE_IMAGE_DIR}`
  );

  if (fullColour > 0) {
    console.log(`${fullColour} cards needed more than 256 colours and kept full-colour output.`);
  }

  if (skipped.length > 0) {
    console.warn(
      `${skipped.length} unit codes had no card generated and keep the logo: ` +
        skipped.slice(0, 10).join(', ')
    );
  }
}

main();
