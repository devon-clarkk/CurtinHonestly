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

export interface Review {
  id?: string;
  rating: number;
  finalGrade?: number;
  reviewText: string;
  semesterTaken: string;
  professor: string;
  workload: number;
  hasExam: boolean;
  wouldTakeAgain: boolean;
  likeCount?: number;
  likedByCurrentUser?: boolean;
  reviewerVerified: boolean;
  createdAt?: string;
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
  semesterTaken: string;
  professor?: string;
  workload?: number;
  hasExam?: boolean;
  wouldTakeAgain?: boolean;
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

  // Review stats
  numberOfReviews: number;
  averageRating: number;
  averageWorkload: number;
  averageFinalGrade: number;
  wouldTakeAgainRatio: number;
  reviews: Review[];
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}
