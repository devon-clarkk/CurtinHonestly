export enum Faculty {
  BUSINESS_AND_LAW = 'BUSINESS_AND_LAW',
  HEALTH_SCIENCES = 'HEALTH_SCIENCES',
  HUMANITIES = 'HUMANITIES',
  SCIENCE_AND_ENGINEERING = 'SCIENCE_AND_ENGINEERING',
  ABORIGINAL_STUDIES = 'ABORIGINAL_STUDIES'
}

export const FacultyDisplayNames: Record<Faculty, string> = {
  [Faculty.BUSINESS_AND_LAW]: 'Business and Law',
  [Faculty.HEALTH_SCIENCES]: 'Health Sciences',
  [Faculty.HUMANITIES]: 'Humanities',
  [Faculty.SCIENCE_AND_ENGINEERING]: 'Science and Engineering',
  [Faculty.ABORIGINAL_STUDIES]: 'Aboriginal Studies'
};

export enum UnitLevel {
  UNDERGRADUATE = 'UNDERGRADUATE',
  POSTGRADUATE = 'POSTGRADUATE'
}

export const UnitLevelDisplayNames: Record<UnitLevel, string> = {
  [UnitLevel.UNDERGRADUATE]: 'Undergraduate',
  [UnitLevel.POSTGRADUATE]: 'Postgraduate'
};

export interface UnitSummary {
  code: string;
  name: string;
  faculty: string;
  level: string;
  numberOfReviews: number;
  averageRating: number;
  wouldTakeAgainRatio: number;
}

// Mirrors the backend ReviewTag enum (review-experience.md #4) — a fixed,
// predefined set, not free text, kept as a single source of truth here
// rather than fetched, matching the roadmap's "predefined toggle chips" sizing.
export const REVIEW_TAGS: { value: string; label: string }[] = [
  { value: 'GROUP_WORK', label: 'Group work' },
  { value: 'WEEKLY_QUIZZES', label: 'Weekly quizzes' },
  { value: 'ATTENDANCE_MARKED', label: 'Attendance marked' },
  { value: 'RECORDED_LECTURES', label: 'Recorded lectures' },
  { value: 'PROCTORED_EXAM', label: 'Proctored exam' },
  { value: 'HEAVY_READING', label: 'Heavy reading load' },
  { value: 'PRACTICAL_LABS', label: 'Practical labs/tutorials' },
  { value: 'OPEN_BOOK_EXAM', label: 'Open-book exam' },
];

export interface TagSummary {
  tag: string;
  label: string;
  count: number;
}

/**
 * Which teaching period a review refers to. Mirrors the backend enum.
 * EARLIER_UNSPECIFIED is the open-ended "before our earliest offered term"
 * bucket and always has a null year.
 */
export type AcademicTerm = 'SEMESTER_1' | 'SEMESTER_2' | 'SUMMER' | 'EARLIER_UNSPECIFIED';

export interface Review {
  id?: string;
  rating: number;
  finalGrade?: number;
  reviewText: string;
  // Structured, not a label. Use formatTerm() from semester-options.util to display.
  termType?: AcademicTerm | null;
  termYear?: number | null;
  professor: string;
  workload: number;
  hasExam: boolean;
  wouldTakeAgain: boolean;
  tags?: string[];
  likeCount?: number;
  likedByCurrentUser?: boolean;
  reviewerVerified: boolean;
  // Reviewer standing, absent on anonymised reviews. Labels come from the
  // backend; REVIEWER_TIERS in utils/reviewer-tier.util.ts has the same text
  // plus descriptions for the client.
  reviewerTier?: ReviewerTier | null;
  reviewerTierLabel?: string | null;
  reviewerRecognition?: RecognitionTier | null;
  reviewerRecognitionLabel?: string | null;
  createdAt?: string;
}

/** Activity tier by number of reviews written. Mirrors the backend enum. */
export type ReviewerTier = 'LURKER' | 'NEWCOMER' | 'CONTRIBUTOR' | 'REGULAR' | 'TOP_REVIEWER' | 'LEGEND';

/** Recognition tier by helpful marks received across all reviews. Mirrors the backend enum. */
export type RecognitionTier = 'APPRECIATED' | 'VALUED_REVIEWER' | 'COMMUNITY_FAVOURITE';

/**
 * The signed-in student's own reviewer standing (GET /reviewer-rank/me).
 * recognitionTier is null until the first threshold; the next* fields are
 * null or 0 at the top of each ladder.
 */
export interface ReviewerProfile {
  activityTier: ReviewerTier;
  activityTierLabel: string;
  recognitionTier: RecognitionTier | null;
  recognitionTierLabel: string | null;
  reviewCount: number;
  likesReceived: number;
  reviewsToNextTier: number;
  nextTierLabel: string | null;
  nextTierThreshold: number;
  likesToNextRecognition: number;
  nextRecognitionLabel: string | null;
  nextRecognitionThreshold: number;
}

export interface Tip {
  id: string;
  text: string;
  authorVerified: boolean;
  ownedByCurrentUser: boolean;
  createdAt: string;
}

export interface MyReview {
  id: string;
  unitCode: string;
  unitName: string;
  rating: number;
  finalGrade?: number | null;
  reviewText: string;
  termType?: AcademicTerm | null;
  termYear?: number | null;
  professor?: string;
  workload?: number;
  hasExam?: boolean;
  wouldTakeAgain?: boolean;
  tags?: string[];
  likeCount?: number;
  createdAt: string;
}

export interface TuitionPattern {
  type: string;
  duration: string;
}

export interface PrerequisiteOption {
  code: string;
  title: string;
  concurrent: boolean;
}

export interface CoursePrerequisiteOption {
  courseCode: string;
  credits?: number;
  title: string;
  concurrent: boolean;
}

export interface PrerequisiteGroup {
  groupName: string;
  requirement: string;
  position: number;
  options: PrerequisiteOption[];
  courseOptions: CoursePrerequisiteOption[];
  // Eligibility (roadmap 4.4) — only populated when logged in. `satisfied`
  // is null (not false) when it can't be fully checked from completed-unit
  // data alone (see `unverifiable`).
  satisfied?: boolean | null;
  unverifiable?: boolean;
}

export interface UnitDetails {
  code: string;
  name: string;
  description: string;
  unitLink: string;
  faculty: string;
  level: string;
  
  // New fields
  area: string;
  fieldOfEducation: string;
  credits: number;
  contactHours: number;
  resultType: string;
  tuitionPatterns: TuitionPattern[];
  prerequisiteGroups: PrerequisiteGroup[];
  // Overall eligibility across all groups (roadmap 4.4) — only populated when
  // logged in; null also when some group can't be fully checked.
  prerequisitesEligible?: boolean | null;

  // Review stats
  numberOfReviews: number;
  averageRating: number;
  averageWorkload: number;
  averageFinalGrade: number;
  wouldTakeAgainRatio: number;
  tagSummary?: TagSummary[];
  reviews: Review[];
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}
