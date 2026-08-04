import { describe, expect, it } from 'vitest';
import {
  BEFORE_EARLIEST_OPTION,
  generateRecentTerms,
  generateSemesterOptions,
} from './semester-options.util';

describe('generateRecentTerms', () => {
  it('starts with Semester 2 for a July reference date and walks backward', () => {
    const options = generateRecentTerms(6, new Date('2026-07-21T00:00:00'));
    expect(options).toEqual([
      'Semester 2, 2026',
      'Semester 1, 2026',
      'Summer, 2025/26',
      'Semester 2, 2025',
      'Semester 1, 2025',
      'Summer, 2024/25',
    ]);
  });

  it('starts with Semester 1 for a March reference date', () => {
    const options = generateRecentTerms(3, new Date('2026-03-01T00:00:00'));
    expect(options).toEqual(['Semester 1, 2026', 'Summer, 2025/26', 'Semester 2, 2025']);
  });

  it('treats November/December as the start of next year\'s summer label', () => {
    const options = generateRecentTerms(2, new Date('2025-11-15T00:00:00'));
    expect(options).toEqual(['Summer, 2025/26', 'Semester 2, 2025']);
  });

  it('treats January as still within the summer that started last November', () => {
    const options = generateRecentTerms(2, new Date('2026-01-10T00:00:00'));
    expect(options).toEqual(['Summer, 2025/26', 'Semester 2, 2025']);
  });

  it('respects the requested count', () => {
    expect(generateRecentTerms(1, new Date('2026-07-21T00:00:00'))).toHaveLength(1);
    expect(generateRecentTerms(10, new Date('2026-07-21T00:00:00'))).toHaveLength(10);
  });
});

describe('generateSemesterOptions', () => {
  const july2026 = new Date('2026-07-21T00:00:00');

  it('runs from the current term back to Semester 1, 2022', () => {
    const options = generateSemesterOptions(july2026);
    expect(options[0]).toBe('Semester 2, 2026');
    expect(options).toContain('Semester 1, 2022');
  });

  it('stops at Semester 1, 2022 and does not offer anything older', () => {
    const options = generateSemesterOptions(july2026);
    expect(options).not.toContain('Summer, 2021/22');
    expect(options).not.toContain('Semester 2, 2021');
  });

  it('ends with the catch-all for anything earlier', () => {
    const options = generateSemesterOptions(july2026);
    expect(options[options.length - 1]).toBe(BEFORE_EARLIEST_OPTION);
    expect(BEFORE_EARLIEST_OPTION).toBe('Before 2022');
  });

  it('covers every term in between, newest first, with no gaps or repeats', () => {
    const options = generateSemesterOptions(july2026);
    const terms = options.slice(0, -1);

    // S2 2026 back to S1 2022 is 14 terms: 4 full years of three terms, plus
    // S1 and S2 of 2026.
    expect(terms).toHaveLength(14);
    expect(new Set(terms).size).toBe(terms.length);
    expect(terms).toEqual(generateRecentTerms(14, july2026));
  });

  it('offers only the catch-all if the reference date predates the floor', () => {
    const options = generateSemesterOptions(new Date('2019-07-21T00:00:00'));
    expect(options).toEqual([BEFORE_EARLIEST_OPTION]);
  });

  it('grows as time passes rather than going stale at the recent end', () => {
    const later = generateSemesterOptions(new Date('2028-07-21T00:00:00'));
    expect(later[0]).toBe('Semester 2, 2028');
    expect(later).toContain('Semester 1, 2022');
    expect(later.length).toBeGreaterThan(generateSemesterOptions(july2026).length);
  });
});
