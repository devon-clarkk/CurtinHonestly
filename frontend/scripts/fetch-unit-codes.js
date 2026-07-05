/**
 * Fetches all unit codes from the backend API for prerender + sitemap generation.
 * Runs once per prod deploy during `npm run build` — skipped on dev/local builds.
 */
const fs = require('fs');
const path = require('path');
const { resolveSeoBuildConfig } = require('./seo-build-config');

const outputPath = path.resolve(__dirname, '../src/generated/unit-codes.json');
const sitemapMetaPath = path.resolve(__dirname, '../src/generated/unit-sitemap-meta.json');
const PAGE_SIZE = 500;
const isCi = process.env.GITHUB_ACTIONS === 'true';

async function fetchAllUnits(apiUrl) {
  const codes = [];
  const sitemapMeta = [];
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
      }
    }

    totalPages = data.totalPages ?? 1;
    page += 1;
    console.log(`Fetched page ${page}/${totalPages} (${codes.length} codes so far)`);
  }

  return { codes, sitemapMeta };
}

function writeEmptyOutputs(reason) {
  console.log(reason);
  fs.mkdirSync(path.dirname(outputPath), { recursive: true });
  fs.writeFileSync(outputPath, '[]\n');
  fs.writeFileSync(sitemapMetaPath, '[]\n');
}

async function main() {
  const { apiUrl, seoEnabled } = resolveSeoBuildConfig();

  if (!seoEnabled) {
    writeEmptyOutputs('SEO disabled for this build — skipping unit code fetch (no backend load).');
    return;
  }

  console.log(`SEO enabled — fetching unit codes from ${apiUrl} ...`);

  try {
    const { codes, sitemapMeta } = await fetchAllUnits(apiUrl);
    fs.mkdirSync(path.dirname(outputPath), { recursive: true });
    fs.writeFileSync(outputPath, JSON.stringify(codes, null, 2));
    fs.writeFileSync(sitemapMetaPath, JSON.stringify(sitemapMeta, null, 2));
    console.log(`Wrote ${codes.length} unit codes to ${outputPath}`);
    console.log(`Wrote sitemap metadata to ${sitemapMetaPath}`);
  } catch (error) {
    console.error('Failed to fetch unit codes:', error.message);

    if (isCi) {
      process.exit(1);
    }

    writeEmptyOutputs('Non-CI build: writing empty outputs after fetch failure.');
  }
}

main();
