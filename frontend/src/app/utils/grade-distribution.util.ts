import { Review } from '../models/unit.model';

export interface GradeBand {
  label: string;
  count: number;
  percent: number;
}

// Curtin-style grade bands — more meaningful to students than raw 10-point
// buckets ("what did people actually score" in terms they already recognize).
const GRADE_BANDS: { label: string; min: number; max: number }[] = [
  { label: 'Fail', min: 0, max: 49 },
  { label: 'Pass', min: 50, max: 59 },
  { label: 'Credit', min: 60, max: 69 },
  { label: 'Distinction', min: 70, max: 79 },
  { label: 'High Distinction', min: 80, max: 100 },
];

// Reviews with a null/undefined finalGrade don't count toward the anonymity
// gate below — "no grade reported" isn't a data point to bucket.
export function gradedReviewCount(reviews: Review[]): number {
  return (reviews || []).filter(r => r.finalGrade !== null && r.finalGrade !== undefined).length;
}

/**
 * Buckets reviews with a reported grade into Curtin-style bands. Gate on
 * gradedReviewCount(reviews) >= 5 before showing this — grouping fewer than
 * 5 data points risks de-anonymizing a single reviewer (review-experience.md #7).
 */
export function gradeDistribution(reviews: Review[]): GradeBand[] {
  const graded = (reviews || []).filter(r => r.finalGrade !== null && r.finalGrade !== undefined);
  const total = graded.length;
  if (total === 0) {
    return [];
  }
  return GRADE_BANDS.map(band => {
    const count = graded.filter(r => r.finalGrade! >= band.min && r.finalGrade! <= band.max).length;
    return { label: band.label, count, percent: Math.round((count / total) * 100) };
  });
}
