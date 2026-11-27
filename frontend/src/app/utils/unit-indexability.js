/**
 * Which unit pages search engines are asked to index.
 *
 * A unit page earns its place in the index through its reviews: they are the
 * content no other site has. A page without them restates the handbook entry
 * for the unit, which the handbook itself already ranks for. Search engines
 * judge quality across a whole site, so the index holds the pages that carry
 * reviews, and each remaining page joins it with the build that follows its
 * first review.
 *
 * Two runtimes need the same answer and cannot share TypeScript, so this is
 * plain JavaScript with a hand-written declaration file, matching
 * share-image.js: scripts/generate-seo-assets.js decides which units the
 * sitemap lists under Node before `ng build` exists, and SeoService writes the
 * robots tag on the page. A sitemap that lists a page whose own markup says
 * `noindex` sends search engines two contradictory signals, so both sides read
 * the rule from here, and scripts/verify-seo-output.js holds the built output
 * to it.
 */

/** Published reviews a unit needs before its page is offered for indexing. */
const MIN_REVIEWS_TO_INDEX = 1;

/** The page is in the sitemap and says `index`. */
const INDEXABLE_ROBOTS = 'index, follow';

/**
 * The page stays out of the index and its links still count. `follow` is the
 * point: every unit page links its faculty hub and its prerequisite chain, and
 * those links should keep working for crawlers whatever the page's own status.
 */
const UNREVIEWED_ROBOTS = 'noindex, follow';

function isUnitIndexable(numberOfReviews) {
  const reviews = Number(numberOfReviews);
  return Number.isFinite(reviews) && reviews >= MIN_REVIEWS_TO_INDEX;
}

function unitRobotsDirective(numberOfReviews) {
  return isUnitIndexable(numberOfReviews) ? INDEXABLE_ROBOTS : UNREVIEWED_ROBOTS;
}

module.exports = {
  MIN_REVIEWS_TO_INDEX,
  INDEXABLE_ROBOTS,
  UNREVIEWED_ROBOTS,
  isUnitIndexable,
  unitRobotsDirective,
};
