/**
 * Where a page's share card lives, and which Twitter card type may claim it.
 *
 * Two runtimes need the same answer and cannot share TypeScript, so this is
 * plain JavaScript with a hand-written declaration file, matching
 * prerequisite-graph.js: scripts/generate-share-images.js writes the files
 * under Node before `ng build` exists, and SeoService names them in the meta
 * tags. A disagreement between the two is invisible until a card 404s in
 * someone's Discord, so both sides read their path from here.
 *
 * The site root that these paths hang off is public/, which the Angular
 * builder copies verbatim into the browser output.
 */

/** Site-root-relative directory holding every generated card. */
const SHARE_IMAGE_DIR = 'assets/og';

/**
 * The logo. It is square, so it is only ever correct alongside `summary`.
 * A `summary_large_image` card pointing here renders as a small mark stranded
 * in a 1200x630 frame, which is worse than the plain card it replaced.
 */
const FALLBACK_IMAGE_PATH = '/assets/images/logo.png';

/** The three hand-tuned pages. Each name is also the card's file name. */
const INFO_CARD_NAMES = ['home', 'about', 'contact'];

/**
 * Every unit code in the catalogue is four letters then four digits, checked
 * against all 1,761 live codes. Anything else has no card generated for it, so
 * it must not be named as one: returning null here is what routes an
 * unrecognised code to the logo and `summary` instead of a broken large card.
 */
const UNIT_CODE_PATTERN = /^[A-Z]{4}\d{4}$/;

/** A faculty slug is lower-case words joined by single hyphens. */
const FACULTY_SLUG_PATTERN = /^[a-z]+(?:-[a-z]+)*$/;

function unitShareImagePath(code) {
  if (typeof code !== 'string' || !UNIT_CODE_PATTERN.test(code)) {
    return null;
  }
  return `/${SHARE_IMAGE_DIR}/units/${code}.png`;
}

function facultyShareImagePath(slug) {
  if (typeof slug !== 'string' || !FACULTY_SLUG_PATTERN.test(slug)) {
    return null;
  }
  return `/${SHARE_IMAGE_DIR}/faculty/${slug}.png`;
}

function infoShareImagePath(name) {
  if (typeof name !== 'string' || !INFO_CARD_NAMES.includes(name)) {
    return null;
  }
  return `/${SHARE_IMAGE_DIR}/${name}.png`;
}

/**
 * Pairs the image with the card type that suits its shape, so the two can
 * never be chosen apart.
 *
 * `summary_large_image` is a claim about the file at the other end: a 1200x630
 * card exists and is worth showing at full width. It is only made where this
 * build actually wrote one. Everything else keeps `summary`, which the square
 * logo genuinely fits. scripts/verify-seo-output.js holds the built output to
 * both halves of that.
 */
function shareCardMeta(imagePath, siteUrl) {
  const base = String(siteUrl).replace(/\/$/, '');

  if (!imagePath) {
    return { image: `${base}${FALLBACK_IMAGE_PATH}`, card: 'summary' };
  }

  return { image: `${base}${imagePath}`, card: 'summary_large_image' };
}

module.exports = {
  SHARE_IMAGE_DIR,
  FALLBACK_IMAGE_PATH,
  INFO_CARD_NAMES,
  unitShareImagePath,
  facultyShareImagePath,
  infoShareImagePath,
  shareCardMeta,
};
