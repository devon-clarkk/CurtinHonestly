import { describe, expect, it } from 'vitest';
import {
  INDEXABLE_ROBOTS,
  MIN_REVIEWS_TO_INDEX,
  UNREVIEWED_ROBOTS,
  isUnitIndexable,
  unitRobotsDirective,
} from './unit-indexability';

describe('unit indexability', () => {
  it('offers a unit for indexing once it reaches the review threshold', () => {
    expect(isUnitIndexable(MIN_REVIEWS_TO_INDEX)).toBe(true);
    expect(isUnitIndexable(MIN_REVIEWS_TO_INDEX + 6)).toBe(true);
    expect(unitRobotsDirective(MIN_REVIEWS_TO_INDEX)).toBe(INDEXABLE_ROBOTS);
  });

  it('keeps a unit below the threshold out of the index', () => {
    expect(isUnitIndexable(MIN_REVIEWS_TO_INDEX - 1)).toBe(false);
    expect(unitRobotsDirective(0)).toBe(UNREVIEWED_ROBOTS);
  });

  /**
   * The listing can omit the count, and a page that cannot show it has reviews
   * makes no claim to the index.
   */
  it('treats a missing or malformed count as no reviews', () => {
    expect(isUnitIndexable(undefined)).toBe(false);
    expect(isUnitIndexable(null)).toBe(false);
    expect(isUnitIndexable(Number.NaN)).toBe(false);
  });

  /**
   * Every unit page links its faculty hub and its prerequisite chain. Those
   * links have to keep counting for crawlers on a page that is itself held out
   * of the index, which is the difference from the shell's `noindex, nofollow`.
   */
  it('lets crawlers follow links from a page that is not indexed', () => {
    const directives = UNREVIEWED_ROBOTS.split(',').map((d) => d.trim());

    expect(directives).toContain('noindex');
    expect(directives).toContain('follow');
    expect(directives).not.toContain('nofollow');
  });
});
