import { describe, expect, it } from 'vitest';
import {
  BEFORE_EARLIEST_OPTION,
  formatTerm,
  generateRecentTerms,
  generateSemesterOptions,
  termSortKey,
} from './semester-options.util';

const labels = (options: { label: string }[]) => options.map(o => o.label);

describe('generateRecentTerms', () => {
  it('starts with Semester 2 for a July reference date and walks backward', () => {
    const options = generateRecentTerms(6, new Date('2026-07-21T00:00:00'));
    expect(labels(options)).toEqual([
      'Semester 2, 2026',
      'Semester 1, 2026',
      'Summer, 2025/26',
      'Semester 2, 2025',
      'Semester 1, 2025',
      'Summer, 2024/25',
    ]);
  });

  it('carries the structured value alongside the label', () => {
    const [first, , summer] = generateRecentTerms(3, new Date('2026-07-21T00:00:00'));
    expect(first).toEqual({ termType: 'SEMESTER_2', termYear: 2026, label: 'Semester 2, 2026' });
    // Summer spans a year boundary; the stored year is the one it ends in.
    expect(summer).toEqual({ termType: 'SUMMER', termYear: 2026, label: 'Summer, 2025/26' });
  });

  it('starts with Semester 1 for a March reference date', () => {
    const options = generateRecentTerms(3, new Date('2026-03-01T00:00:00'));
    expect(labels(options)).toEqual(['Semester 1, 2026', 'Summer, 2025/26', 'Semester 2, 2025']);
  });

  it('treats November/December as the start of next year\'s summer label', () => {
    const options = generateRecentTerms(2, new Date('2025-11-15T00:00:00'));
    expect(labels(options)).toEqual(['Summer, 2025/26', 'Semester 2, 2025']);
  });

  it('treats January as still within the summer that started last November', () => {
    const options = generateRecentTerms(2, new Date('2026-01-10T00:00:00'));
    expect(labels(options)).toEqual(['Summer, 2025/26', 'Semester 2, 2025']);
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
    expect(options[0].label).toBe('Semester 2, 2026');
    expect(labels(options)).toContain('Semester 1, 2022');
  });

  it('stops at Semester 1, 2022 and does not offer anything older', () => {
    const options = labels(generateSemesterOptions(july2026));
    expect(options).not.toContain('Summer, 2021/22');
    expect(options).not.toContain('Semester 2, 2021');
  });

  it('ends with the catch-all, which carries no year', () => {
    const options = generateSemesterOptions(july2026);
    expect(options[options.length - 1]).toEqual(BEFORE_EARLIEST_OPTION);
    expect(BEFORE_EARLIEST_OPTION).toEqual({
      termType: 'EARLIER_UNSPECIFIED',
      termYear: null,
      label: 'Before 2022',
    });
  });

  it('covers every term in between, newest first, with no gaps or repeats', () => {
    const options = generateSemesterOptions(july2026);
    const terms = options.slice(0, -1);

    // S2 2026 back to S1 2022 is 14 terms: 4 full years of three terms, plus
    // S1 and S2 of 2026.
    expect(terms).toHaveLength(14);
    expect(new Set(labels(terms)).size).toBe(terms.length);
    expect(terms).toEqual(generateRecentTerms(14, july2026));
  });

  it('offers only the catch-all if the reference date predates the floor', () => {
    expect(generateSemesterOptions(new Date('2019-07-21T00:00:00'))).toEqual([BEFORE_EARLIEST_OPTION]);
  });

  it('grows as time passes rather than going stale at the recent end', () => {
    const later = generateSemesterOptions(new Date('2028-07-21T00:00:00'));
    expect(later[0].label).toBe('Semester 2, 2028');
    expect(labels(later)).toContain('Semester 1, 2022');
    expect(later.length).toBeGreaterThan(generateSemesterOptions(july2026).length);
  });
});

describe('formatTerm', () => {
  it('renders each term type', () => {
    expect(formatTerm('SEMESTER_1', 2024)).toBe('Semester 1, 2024');
    expect(formatTerm('SEMESTER_2', 2024)).toBe('Semester 2, 2024');
    expect(formatTerm('SUMMER', 2025)).toBe('Summer, 2024/25');
    expect(formatTerm('EARLIER_UNSPECIFIED', null)).toBe('Before 2022');
  });

  it('returns empty rather than a half-formed label when data is missing', () => {
    expect(formatTerm(null, null)).toBe('');
    expect(formatTerm(undefined, 2024)).toBe('');
    // A dated term with no year cannot be rendered honestly.
    expect(formatTerm('SEMESTER_1', null)).toBe('');
  });
});

describe('termSortKey', () => {
  it('orders terms chronologically within and across years', () => {
    const summer2025 = termSortKey('SUMMER', 2025)!;
    const s1of2025 = termSortKey('SEMESTER_1', 2025)!;
    const s2of2025 = termSortKey('SEMESTER_2', 2025)!;
    const summer2026 = termSortKey('SUMMER', 2026)!;

    expect(summer2025).toBeLessThan(s1of2025);
    expect(s1of2025).toBeLessThan(s2of2025);
    expect(s2of2025).toBeLessThan(summer2026);
  });

  it('gives no position to terms that have none', () => {
    expect(termSortKey('EARLIER_UNSPECIFIED', null)).toBeNull();
    expect(termSortKey(null, null)).toBeNull();
    expect(termSortKey('SEMESTER_1', null)).toBeNull();
  });
});
