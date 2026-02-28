export interface UnitSummary {
  code: string;
  name: string;
  faculty: string;
  numberOfReviews: number;
  averageRating: number;
  wouldTakeAgainRatio: number;
}

export interface Review {
  rating: number;
  finalGrade?: number;
  reviewText: string;
  semesterTaken: string;
  professor: string;
  workload: number;
  hasExam: boolean;
  wouldTakeAgain: boolean;
  userName: string;
}

export interface TuitionPattern {
  type: string;
  duration: string;
}

export interface PrerequisiteOption {
  code: string;
  title: string;
}

export interface PrerequisiteGroup {
  groupName: string;
  requirement: string;
  position: number;
  options: PrerequisiteOption[];
}

export interface UnitDetails {
  code: string;
  name: string;
  description: string;
  unitLink: string;
  faculty: string;
  
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
