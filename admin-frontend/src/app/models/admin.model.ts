export interface AdminOverview {
  totalUsers: number;
  totalReviews: number;
  totalUnits: number;
  reviewsLast7Days: number;
  usersLast7Days: number;
  averageRating: number;
}

export interface TimeSeriesPoint {
  period: string;
  users: number;
  reviews: number;
}

export interface AdminAnalytics {
  signupsAndReviewsOverTime: TimeSeriesPoint[];
  reviewsByFaculty: Record<string, number>;
  averageWorkload: number;
  wouldTakeAgainRatio: number;
}

export interface UserAdmin {
  id: string;
  email: string;
  username: string;
  roles: string[];
  banned: boolean;
  reviewCount: number;
  createdAt: string;
}

export interface AdminReview {
  id: string;
  unitCode: string;
  authorEmail: string;
  rating: number;
  reviewText: string;
  createdAt: string;
}

export interface PagedReviews {
  content: AdminReview[];
  totalElements: number;
  totalPages: number;
  number: number;
}
