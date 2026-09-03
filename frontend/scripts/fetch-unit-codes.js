/**
 * Fetches all unit codes from the backend API for prerender + sitemap generation.
 * Runs once per prod deploy during `npm run build` — skipped on dev/local builds.
 *
 * It also builds the reverse prerequisite graph, which is the one fact on a unit
 * page that no handbook page states: which units this one unlocks. That has to
 * be derived by inverting the whole catalogue, so it is done here, once per
 * deploy, rather than by the page at runtime.
 */
const fs = require('fs');
const path = require('path');
const { resolveSeoBuildConfig } = require('./seo-build-config');
const { invertPrerequisiteGraph } = require('../src/app/utils/prerequisite-graph');

const outputPath = path.resolve(__dirname, '../src/generated/unit-codes.json');
const sitemapMetaPath = path.resolve(__dirname, '../src/generated/unit-sitemap-meta.json');
const requiredForPath = path.resolve(__dirname, '../src/generated/required-for.json');
const shareMetaPath = path.resolve(__dirname, '../src/generated/unit-share-meta.json');
const PAGE_SIZE = 500;
const isCi = process.env.GITHUB_ACTIONS === 'true';

/**
 * One detail request per unit, so the pool size is what decides whether this
 * step costs seconds or minutes. Measured against prod: 16 in flight fetches
 * all 1,761 units in about 39 seconds, next to the several minutes `ng build`
 * already spends prerendering the same 1,761 pages.
 */
const DETAIL_CONCURRENCY = 16;

async function fetchAllUnits(apiUrl) {
  const codes = [];
  const sitemapMeta = [];
  const shareMeta = [];
  let page = 0;
  let totalPages = 1;

  while (page < totalPages) {
    const url = `${apiUrl}/units?page=${page}&size=${PAGE_SIZE}&sortBy=code`;
    const response = await fetch(url);

    if (!response.ok) {
      throw new Error(`GET ${url} failed: ${response.status} ${response.statusText}`);
    }

    const data = await response.json();
    const content = data.content ?? [];

    for (const unit of content) {
      if (unit.code) {
        codes.push(unit.code);
        sitemapMeta.push({
          code: unit.code,
          lastmod: unit.latestReviewAt ? unit.latestReviewAt.slice(0, 10) : null,
        });
        // Everything a share card draws, taken from the listing this loop
        // already reads. The card needs no request of its own: the summary
        // carries the name, the faculty and the review position already.
        shareMeta.push({
          code: unit.code,
          name: unit.name ?? '',
          faculty: unit.faculty ?? '',
          rating: unit.averageRating ?? 0,
          reviews: unit.numberOfReviews ?? 0,
        });
      }
    }

    totalPages = data.totalPages ?? 1;
    page += 1;
    console.log(`Fetched page ${page}/${totalPages} (${codes.length} codes so far)`);
  }

  return { codes, sitemapMeta, shareMeta };
}

async function fetchPrerequisiteCodes(apiUrl, code) {
  const url = `${apiUrl}/units/${encodeURIComponent(code)}`;

  // One retry. A single dropped response would silently cost this unit every
  // edge it contributes, and a missing edge looks exactly like a real dead end.
  for (let attempt = 0; attempt < 2; attempt++) {
    try {
      const response = await fetch(url);
      if (!response.ok) {
        continue;
      }
      const unit = await response.json();
      const prerequisiteCodes = [];
      for (const group of unit.prerequisiteGroups ?? []) {
        for (const option of group.options ?? []) {
          prerequisiteCodes.push(option.code);
        }
      }
      return { code: unit.code ?? code, prerequisiteCodes };
    } catch {
      // Falls through to the retry, then to the caller's failure count.
    }
  }

  return null;
}

/** Fetches every unit's prerequisites through a fixed-size pool of workers. */
async function fetchPrerequisiteGraph(apiUrl, codes) {
  const units = [];
  const failed = [];
  let next = 0;
  const started = Date.now();

  async function worker() {
    while (next < codes.length) {
      const code = codes[next++];
      const unit = await fetchPrerequisiteCodes(apiUrl, code);
      if (unit) {
        units.push(unit);
      } else {
        failed.push(code);
      }
    }
  }

  await Promise.all(Array.from({ length: DETAIL_CONCURRENCY }, worker));

  const seconds = ((Date.now() - started) / 1000).toFixed(1);
  console.log(`Fetched prerequisites for ${units.length}/${codes.length} units in ${seconds}s`);

  if (failed.length > 0) {
    console.warn(`Could not read prerequisites for ${failed.length} units: ${failed.slice(0, 10).join(', ')}`);
  }

  return invertPrerequisiteGraph(units, codes);
}

function writeEmptyOutputs(reason) {
  console.log(reason);
  fs.mkdirSync(path.dirname(outputPath), { recursive: true });
  fs.writeFileSync(outputPath, '[]\n');
  fs.writeFileSync(sitemapMetaPath, '[]\n');
  fs.writeFileSync(shareMetaPath, '[]\n');
  // The unit page imports this one, so it has to exist even when nothing was
  // fetched. Empty means "no graph was built", which the page reads as a reason
  // to say nothing rather than to claim every unit is a dead end.
  fs.writeFileSync(requiredForPath, '{}\n');
}

async function main() {
  const { apiUrl, seoEnabled } = resolveSeoBuildConfig();

  if (!seoEnabled) {
    writeEmptyOutputs('SEO disabled for this build — skipping unit code fetch (no backend load).');
    return;
  }

  console.log(`SEO enabled — fetching unit codes from ${apiUrl} ...`);

  try {
    const { codes, sitemapMeta, shareMeta } = await fetchAllUnits(apiUrl);
    const requiredFor = await fetchPrerequisiteGraph(apiUrl, codes);

    fs.mkdirSync(path.dirname(outputPath), { recursive: true });
    fs.writeFileSync(outputPath, JSON.stringify(codes, null, 2));
    fs.writeFileSync(sitemapMetaPath, JSON.stringify(sitemapMeta, null, 2));
    fs.writeFileSync(shareMetaPath, JSON.stringify(shareMeta, null, 2));
    fs.writeFileSync(requiredForPath, JSON.stringify(requiredFor, null, 2));

    const edges = Object.values(requiredFor).reduce((total, list) => total + list.length, 0);
    const reviewed = shareMeta.filter((unit) => unit.reviews > 0).length;
    console.log(`Wrote ${codes.length} unit codes to ${outputPath}`);
    console.log(`Wrote sitemap metadata to ${sitemapMetaPath}`);
    console.log(`Wrote share card data to ${shareMetaPath} (${reviewed} units carry a rating)`);
    console.log(
      `Wrote the reverse prerequisite graph to ${requiredForPath} ` +
        `(${Object.keys(requiredFor).length} units unlock something, ${edges} links)`
    );
  } catch (error) {
    console.error('Failed to fetch unit codes:', error.message);

    if (isCi) {
      process.exit(1);
    }

    writeEmptyOutputs('Non-CI build: writing empty outputs after fetch failure.');
  }
}

main();
