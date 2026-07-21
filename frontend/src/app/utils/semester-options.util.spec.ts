import { describe, expect, it } from 'vitest';
import { generateSemesterOptions } from './semester-options.util';

describe('generateSemesterOptions', () => {
  it('starts with Semester 2 for a July reference date and walks backward', () => {
    const options = generateSemesterOptions(6, new Date('2026-07-21T00:00:00'));
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
    const options = generateSemesterOptions(3, new Date('2026-03-01T00:00:00'));
    expect(options).toEqual(['Semester 1, 2026', 'Summer, 2025/26', 'Semester 2, 2025']);
  });

  it('treats November/December as the start of next year\'s summer label', () => {
    const options = generateSemesterOptions(2, new Date('2025-11-15T00:00:00'));
    expect(options).toEqual(['Summer, 2025/26', 'Semester 2, 2025']);
  });

  it('treats January as still within the summer that started last November', () => {
    const options = generateSemesterOptions(2, new Date('2026-01-10T00:00:00'));
    expect(options).toEqual(['Summer, 2025/26', 'Semester 2, 2025']);
  });

  it('respects the requested count', () => {
    expect(generateSemesterOptions(1, new Date('2026-07-21T00:00:00'))).toHaveLength(1);
    expect(generateSemesterOptions(10, new Date('2026-07-21T00:00:00'))).toHaveLength(10);
  });
});
