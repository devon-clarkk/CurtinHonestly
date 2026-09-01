/**
 * Tell IndexNow which pages changed, after a production deploy.
 *
 * Google is not the only search index that matters here. ChatGPT search and
 * Microsoft Copilot answer from Bing, so being in Bing's index is what decides
 * whether this site is the source an assistant quotes when a student asks what
 * a unit is like. IndexNow is how Bing, Yandex, Seznam and Naver take that
 * submission directly instead of waiting to recrawl.
 *
 * Ownership is proved by hosting a file named for the key, containing the key,
 * at the site root. That file lives in public/ and ships with the build, which
 * is why this only runs after a deploy: the endpoint fetches the key file from
 * the live host before accepting anything, so submitting from a build that is
 * not yet published would simply fail.
 *
 * Run after the deploy step, never as part of `npm run build`:
 *   node scripts/submit-indexnow.js
 *
 * Skips silently unless SEO is enabled for the build, so dev deploys never
 * submit. Never fails the pipeline: a search engine declining a hint is not a
 * reason to mark a good deploy red.
 */
const fs = require('fs');
const path = require('path');
const { resolveSeoBuildConfig } = require('./seo-build-config');

const ENDPOINT = 'https://api.indexnow.org/IndexNow';
/** The documented ceiling for one submission. The sitemap is well under it. */
const MAX_URLS = 10000;
const SUBMIT_ATTEMPTS = 3;
const RETRY_DELAY_MS = 20000;

const publicDir = path.resolve(__dirname, '../public');

function findKey() {
  // The key file is named for the key it contains, so the filename is the
  // source of truth and there is nothing to keep in sync.
  const candidates = fs
    .readdirSync(publicDir)
    .filter((name) => /^[0-9a-f]{8,128}\.txt$/i.test(name));

  if (candidates.length === 0) {
    return null;
  }
  if (candidates.length > 1) {
    throw new Error(
      `Found ${candidates.length} IndexNow key files in public/: ${candidates.join(', ')}. ` +
        'Exactly one should exist; the others will fail verification.'
    );
  }

  const file = candidates[0];
  const key = file.replace(/\.txt$/i, '');
  const contents = fs.readFileSync(path.join(publicDir, file), 'utf8').trim();

  if (contents !== key) {
    throw new Error(
      `public/${file} must contain exactly its own key. IndexNow verifies by fetching ` +
        'that file and comparing, so a mismatch fails every submission.'
    );
  }

  return { key, file };
}

function sitemapUrls() {
  const sitemap = path.join(publicDir, 'sitemap.xml');
  if (!fs.existsSync(sitemap)) {
    return [];
  }
  const xml = fs.readFileSync(sitemap, 'utf8');
  return [...xml.matchAll(/<loc>([^<]+)<\/loc>/g)].map((m) => m[1].trim());
}

async function main() {
  const { siteUrl, seoEnabled } = resolveSeoBuildConfig();

  if (!seoEnabled) {
    console.log('IndexNow: SEO is off for this build, nothing submitted.');
    return;
  }

  const found = findKey();
  if (!found) {
    console.log('IndexNow: no key file in public/, nothing submitted.');
    return;
  }

  const urls = sitemapUrls();
  if (urls.length === 0) {
    console.log('IndexNow: sitemap is empty, nothing submitted.');
    return;
  }

  const host = new URL(siteUrl).host;
  const body = {
    host,
    key: found.key,
    keyLocation: `${siteUrl}/${found.file}`,
    urlList: urls.slice(0, MAX_URLS),
  };

  // The deploy this follows has only just finished, and the endpoint verifies
  // by fetching the key file from the live host. The first attempt can beat CDN
  // propagation and come back 403, which is a race rather than a real rejection,
  // so wait and try again before reporting anything.
  let response;
  for (let attempt = 1; attempt <= SUBMIT_ATTEMPTS; attempt += 1) {
    response = await fetch(ENDPOINT, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json; charset=utf-8' },
      body: JSON.stringify(body),
    });

    // 200 accepted, 202 accepted with the key still being validated.
    if (response.ok || response.status === 202) {
      console.log(
        `IndexNow: submitted ${body.urlList.length} URLs for ${host} ` +
          `(${response.status}, attempt ${attempt}).`
      );
      return;
    }

    // Only 403 is worth retrying: it is what an unpropagated key file looks
    // like. Anything else is a real refusal and will not improve by waiting.
    if (response.status !== 403 || attempt === SUBMIT_ATTEMPTS) {
      break;
    }

    console.log(
      `IndexNow: attempt ${attempt} returned 403, the key file may not have ` +
        `propagated yet. Retrying in ${RETRY_DELAY_MS / 1000}s.`
    );
    await new Promise((resolve) => setTimeout(resolve, RETRY_DELAY_MS));
  }

  console.warn(
    `IndexNow: ${host} submission returned ${response.status} ${response.statusText}. ` +
      'Deploy is unaffected; check that the key file is reachable at ' +
      body.keyLocation
  );
}

main().catch((error) => {
  // Deliberately not a failure. This is a hint to a third party, and the deploy
  // it follows already succeeded.
  console.warn(`IndexNow: submission skipped after an error: ${error.message}`);
});
