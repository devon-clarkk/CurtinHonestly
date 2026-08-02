import { describe, expect, it } from 'vitest';
import { isResultsSeasonWindow } from './results-season.util';

describe('isResultsSeasonWindow', () => {
  it('is true just after Semester 1 results (late June)', () => {
    expect(isResultsSeasonWindow(new Date('2026-06-27T00:00:00'))).toBe(true);
  });

  it('is true into early July, still within the Semester 1 window', () => {
    expect(isResultsSeasonWindow(new Date('2026-07-05T00:00:00'))).toBe(true);
  });

  it('is false once the Semester 1 window has closed', () => {
    expect(isResultsSeasonWindow(new Date('2026-07-21T00:00:00'))).toBe(false);
  });

  it('is true just after Semester 2 results (late November)', () => {
    expect(isResultsSeasonWindow(new Date('2026-11-27T00:00:00'))).toBe(true);
  });

  it('is true into early December, still within the Semester 2 window', () => {
    expect(isResultsSeasonWindow(new Date('2026-12-05T00:00:00'))).toBe(true);
  });

  it('is false in the middle of semester', () => {
    expect(isResultsSeasonWindow(new Date('2026-04-15T00:00:00'))).toBe(false);
    expect(isResultsSeasonWindow(new Date('2026-09-15T00:00:00'))).toBe(false);
  });

  it('is false right before the June window opens', () => {
    expect(isResultsSeasonWindow(new Date('2026-06-24T23:59:59'))).toBe(false);
  });
});
