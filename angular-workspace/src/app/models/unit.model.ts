export interface UnitSummary {
  code: String;
  name: String;
  faculty: String;
  numberOfReviews: number;
  averageRating: number;
  wouldTakeAgainRatio: number;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}
