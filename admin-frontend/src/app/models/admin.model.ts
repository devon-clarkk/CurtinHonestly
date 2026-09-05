export interface TimeSeriesPoint {
  period: string;
  users: number;
  reviews: number;
}

export interface UnitLeader {
  code: string;
  name: string;
  reviewCount: number;
  averageRating: number;
}

export interface RequestedUnit {
  code: string;
  count: number;
}

export interface DistributionBucket {
  label: string;
  count: number;
}

export interface FacultyBreakdown {
  faculty: string;
  label: string;
  units: number;
  unitsWithReviews: number;
  reviews: number;
}

export interface TermCount {
  termType: string;
  termYear: number | null;
  count: number;
}

export interface LikedReview {
  id: string;
  unitCode: string;
  likeCount: number;
  excerpt: string;
}

export interface ActiveReviewer {
  maskedEmail: string;
  reviewCount: number;
  likesReceived: number;
}

export interface AdminOverview {
  totalUsers: number;
  verifiedUsers: number;
  bannedUsers: number;
  totalReviews: number;
  reviewsWithText: number;
  unitsWithAtLeastOneReview: number;
  totalUnits: number;
  coverageRatio: number;
  pendingUnitRequests: number;
  openFlaggedReviews: number;
  totalLikes: number;
  usersLast7Days: number;
  usersPrior7Days: number;
  reviewsLast7Days: number;
  reviewsPrior7Days: number;
  unverifiedUsersLast7Days: number;
  verificationRate: number;
  signupsAndReviewsOverTime: TimeSeriesPoint[];
  topUnits: UnitLeader[];
  mostRequestedUnits: RequestedUnit[];
}

// GET /admin/recommendations/stats: the in-memory recommendation model
// currently serving requests.
export interface AdminRecommendationStats {
  builtAt: string;
  reviewCount: number;
  // Students with at least one attributed review.
  userCount: number;
  unitCount: number;
  // Students with enough reviews and at least one similar student.
  usersWithNeighbours: number;
  // Students under the personalisation minimum or with no similar student.
  coldStartUsers: number;
  meanNeighboursPerUser: number;
  // Pairs of units reviewed by at least one common student.
  itemPairsWithCoReviews: number;
}

export interface AdminAnalytics {
  periodDays: number;
  signupsAndReviewsOverTime: TimeSeriesPoint[];
  totalUsers: number;
  totalReviews: number;
  activeReviewers: number;
  verificationRate: number;
  activeReviewerShare: number;
  reviewsPerActiveReviewer: number;
  ratingDistribution: DistributionBucket[];
  workloadDistribution: DistributionBucket[];
  averageWorkload: number;
  wouldTakeAgainRatio: number;
  averageReviewTextLength: number;
  gradeShare: number;
  facultyBreakdown: FacultyBreakdown[];
  reviewsByTerm: TermCount[];
  mostLikedReviews: LikedReview[];
  mostActiveReviewers: ActiveReviewer[];
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

// The full stored review: the same fields the public unit page shows plus the
// moderation context. The list and the detail endpoint share this shape.
export interface AdminReview {
  id: string;
  unitCode: string;
  unitName: string;
  authorEmail: string;
  authorId: string | null;
  authorVerified: boolean;
  rating: number;
  finalGrade: number | null;
  reviewText: string | null;
  termType: string | null;
  termYear: number | null;
  professor: string | null;
  workload: number;
  hasExam: boolean;
  wouldTakeAgain: boolean;
  tags: string[];
  likeCount: number;
  flagCount: number;
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
  trackingOnly: boolean;
  visitCount: number;
  landingPath: string | null;
  signupCount: number;
  reviewCount: number;
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

export interface ReferralLinkAdmin {
  id: string;
  slug: string;
  name: string;
  landingPath: string | null;
  active: boolean;
  visitCount: number;
  signupCount: number;
  reviewCount: number;
  campaigns: { id: string; name: string }[];
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
