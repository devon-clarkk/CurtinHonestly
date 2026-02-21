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

export interface UnitDetails {
  code: string;
  name: string;
  description: string;
  faculty: string;
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
