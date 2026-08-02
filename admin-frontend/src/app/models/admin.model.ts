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
  verifiedStudent: boolean;
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

export interface CampaignAdmin {
  id: string;
  slug: string;
  code: string;
  name: string;
  prizeDescription: string | null;
  startsAt: string;
  endsAt: string;
  active: boolean;
  maxRedemptions: number | null;
  minReviewLength: number;
  maxEntriesPerUser: number;
  requireVerifiedStudent: boolean;
  requiredReviewCount: number;
  minLikesReceived: number;
  minLikesGiven: number;
  signupCount: number;
  entryCount: number;
  createdAt: string;
}

export interface CampaignEntryAdmin {
  id: string;
  entryToken: string;
  userEmail: string;
  unitCode: string;
  createdAt: string;
}

export interface UnitRequestAdmin {
  id: string;
  requestedCode: string;
  note: string | null;
  createdAt: string;
}

export interface FlaggedReviewAdmin {
  reviewId: string;
  unitCode: string;
  reviewText: string;
  flagCount: number;
}
