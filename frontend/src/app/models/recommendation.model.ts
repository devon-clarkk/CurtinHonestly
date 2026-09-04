// Mirrors the backend Recommendation*DTO records.

export interface RecommendationItem {
  unitCode: string;
  unitName: string;
  faculty: string;
  level: string;
  // Predicted affinity mapped to 0..100.
  matchScore: number;
  // 5..99, how much supporting evidence there is.
  confidence: number;
  // Similar students behind the item; review count for cold-start fallback items.
  supportingStudents: number;
  reasons: string[];
}

export interface Recommendations {
  // True when the user has too few reviews or no similar students yet. The
  // recommended list then holds a rating-based fallback and avoid is empty.
  coldStart: boolean;
  message: string | null;
  basedOnReviews: number;
  neighbourCount: number;
  recommended: RecommendationItem[];
  avoid: RecommendationItem[];
}

export interface SimilarUnit {
  unitCode: string;
  unitName: string;
  faculty: string;
  level: string;
  matchScore: number;
  // Co-reviewers behind the score; 0 for catalogue fallback items.
  sharedStudents: number;
}

export interface SimilarUnits {
  items: SimilarUnit[];
  basedOnCoReviews: boolean;
}

// MATCH: similar students have reviewed the unit and a score exists.
// REVIEWED: the signed-in student reviewed the unit themselves.
// COLD_START: too few reviews of their own for a personalised score.
// NO_SIGNAL: enough reviews, but no similar student has reviewed this unit.
export type UnitMatchState = 'MATCH' | 'REVIEWED' | 'COLD_START' | 'NO_SIGNAL';

export interface UnitMatch {
  state: UnitMatchState;
  // 0..100, only meaningful for MATCH.
  matchScore: number;
  // 5..99 for MATCH, 0 otherwise.
  confidence: number;
  supportingStudents: number;
  reasons: string[];
  // The student's own review count.
  basedOnReviews: number;
}
