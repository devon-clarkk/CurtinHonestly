import { describe, expect, it } from 'vitest';
import { gradeDistribution, gradedReviewCount } from './grade-distribution.util';
import { Review } from '../models/unit.model';

function review(finalGrade: number | undefined): Review {
  return {
    rating: 4,
    reviewText: 'x',
    semesterTaken: 'Semester 1, 2026',
    professor: '',
    workload: 5,
    hasExam: false,
    wouldTakeAgain: true,
    reviewerVerified: false,
    finalGrade,
  };
}

describe('gradedReviewCount', () => {
  it('counts only reviews with a reported grade', () => {
    const reviews = [review(85), review(undefined), review(60)];
    expect(gradedReviewCount(reviews)).toBe(2);
  });

  it('returns 0 for an empty or undefined list', () => {
    expect(gradedReviewCount([])).toBe(0);
    expect(gradedReviewCount(undefined as unknown as Review[])).toBe(0);
  });
});

describe('gradeDistribution', () => {
  it('buckets grades into Curtin-style bands with correct counts and percentages', () => {
    const reviews = [
      review(35), // Fail
      review(55), // Pass
      review(65), // Credit
      review(65), // Credit
      review(75), // Distinction
      review(90), // High Distinction
    ];

    const result = gradeDistribution(reviews);

    expect(result).toEqual([
      { label: 'Fail', count: 1, percent: 17 },
      { label: 'Pass', count: 1, percent: 17 },
      { label: 'Credit', count: 2, percent: 33 },
      { label: 'Distinction', count: 1, percent: 17 },
      { label: 'High Distinction', count: 1, percent: 17 },
    ]);
  });

  it('respects band boundaries exactly (49 vs 50, 100 included in HD)', () => {
    const reviews = [review(49), review(50), review(100)];
    const result = gradeDistribution(reviews);
    expect(result.find(b => b.label === 'Fail')!.count).toBe(1);
    expect(result.find(b => b.label === 'Pass')!.count).toBe(1);
    expect(result.find(b => b.label === 'High Distinction')!.count).toBe(1);
  });

  it('ignores reviews with no reported grade', () => {
    const reviews = [review(85), review(undefined)];
    const result = gradeDistribution(reviews);
    const total = result.reduce((sum, b) => sum + b.count, 0);
    expect(total).toBe(1);
  });

  it('returns an empty array when no reviews have a grade', () => {
    expect(gradeDistribution([review(undefined), review(undefined)])).toEqual([]);
  });
});
