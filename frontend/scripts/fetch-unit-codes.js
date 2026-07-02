/**
 * Fetches all unit codes from the backend API for prerender + sitemap generation.
 * Runs once per prod deploy during `npm run build` — skipped on dev/local builds.
 */
const fs = require('fs');
const path = require('path');
const { resolveSeoBuildConfig } = require('./seo-build-config');

const outputPath = path.resolve(__dirname, '../src/generated/unit-codes.json');
const PAGE_SIZE = 500;
const isCi = process.env.GITHUB_ACTIONS === 'true';

async function fetchAllUnitCodes(apiUrl) {
  const codes = [];
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
      }
    }

    totalPages = data.totalPages ?? 1;
    page += 1;
    console.log(`Fetched page ${page}/${totalPages} (${codes.length} codes so far)`);
  }

  return codes;
}

function writeEmptyCodes(reason) {
  console.log(reason);
  fs.mkdirSync(path.dirname(outputPath), { recursive: true });
  fs.writeFileSync(outputPath, '[]\n');
}

async function main() {
  const { apiUrl, seoEnabled } = resolveSeoBuildConfig();

  if (!seoEnabled) {
    writeEmptyCodes('SEO disabled for this build — skipping unit code fetch (no backend load).');
    return;
  }

  console.log(`SEO enabled — fetching unit codes from ${apiUrl} ...`);

  try {
    const codes = await fetchAllUnitCodes(apiUrl);
    fs.mkdirSync(path.dirname(outputPath), { recursive: true });
    fs.writeFileSync(outputPath, JSON.stringify(codes, null, 2));
    console.log(`Wrote ${codes.length} unit codes to ${outputPath}`);
  } catch (error) {
    console.error('Failed to fetch unit codes:', error.message);

    if (isCi) {
      process.exit(1);
    }

    writeEmptyCodes('Non-CI build: writing empty unit-codes.json after fetch failure.');
  }
}

main();
