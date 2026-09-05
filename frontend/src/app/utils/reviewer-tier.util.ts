import { RecognitionTier, Review, ReviewerProfile, ReviewerTier } from '../models/unit.model';

/**
 * Display copy for reviewer tiers, in one place. The backend decides which
 * tier a reviewer holds and sends the label with it; this map supplies the
 * same label as a fallback plus the description shown on hover and on the
 * My Reviews rank panel. Thresholds are not repeated here: the profile
 * endpoint returns the next threshold, so the numbers cannot drift.
 */
export interface TierDisplay {
  label: string;
  description: string;
  glyph: string;
}

export const REVIEWER_TIERS: Record<ReviewerTier, TierDisplay> = {
  LURKER: { label: 'Lurker', description: 'No reviews posted yet.', glyph: '○' },
  NEWCOMER: { label: 'Newcomer', description: 'First reviews posted.', glyph: '✎' },
  CONTRIBUTOR: { label: 'Contributor', description: 'Building a track record across units.', glyph: '✎' },
  REGULAR: { label: 'Regular', description: 'A steady voice on the catalogue.', glyph: '◆' },
  TOP_REVIEWER: { label: 'Top Reviewer', description: 'Among the most active reviewers on the site.', glyph: '★' },
  LEGEND: { label: 'Legend', description: 'One of the most prolific reviewers on the site.', glyph: '✦' },
};

export const RECOGNITION_TIERS: Record<RecognitionTier, TierDisplay> = {
  APPRECIATED: { label: 'Appreciated', description: 'Other students have marked these reviews helpful.', glyph: '♥' },
  VALUED_REVIEWER: { label: 'Valued Reviewer', description: 'Reviews other students keep marking helpful.', glyph: '♥' },
  COMMUNITY_FAVOURITE: { label: 'Community Favourite', description: 'Among the most helpful reviewers on the site.', glyph: '♥' },
};

type TierFields = Pick<Review, 'reviewerTier' | 'reviewerTierLabel'>;
type RecognitionFields = Pick<Review, 'reviewerRecognition' | 'reviewerRecognitionLabel'>;

/**
 * What to show for a review's activity tier, or null when there is nothing to
 * show: anonymised reviews carry no tier, and a Lurker chip would only ever
 * appear on bad data since posting a review makes someone a Newcomer.
 */
export function reviewerTierDisplay(review: TierFields | null | undefined): TierDisplay | null {
  const tier = review?.reviewerTier;
  if (!tier || tier === 'LURKER') {
    return null;
  }
  return withServerLabel(REVIEWER_TIERS[tier], review?.reviewerTierLabel);
}

/** What to show for a review's recognition tier, or null when it has not been earned. */
export function reviewerRecognitionDisplay(review: RecognitionFields | null | undefined): TierDisplay | null {
  const tier = review?.reviewerRecognition;
  if (!tier) {
    return null;
  }
  return withServerLabel(RECOGNITION_TIERS[tier], review?.reviewerRecognitionLabel);
}

/** The backend label wins when present, so a copy change ships without a client release. */
function withServerLabel(base: TierDisplay | undefined, serverLabel: string | null | undefined): TierDisplay | null {
  if (base) {
    return serverLabel ? { ...base, label: serverLabel } : base;
  }
  return serverLabel ? { label: serverLabel, description: '', glyph: '' } : null;
}

/** 0 to 100. A target of 0 means the top of the ladder, which reads as full. */
export function progressPercent(current: number, target: number): number {
  if (!target || target <= 0) {
    return 100;
  }
  const ratio = Math.max(0, current) / target;
  return Math.max(0, Math.min(100, Math.round(ratio * 100)));
}

type TierNudgeFields = Pick<ReviewerProfile, 'activityTierLabel' | 'nextTierLabel' | 'reviewsToNextTier'>;
type RecognitionNudgeFields = Pick<
  ReviewerProfile,
  'recognitionTier' | 'recognitionTierLabel' | 'nextRecognitionLabel' | 'likesToNextRecognition'
>;

/** One line telling the student what the next activity tier takes. */
export function nextTierNudge(profile: TierNudgeFields): string {
  if (!profile.nextTierLabel) {
    return `${profile.activityTierLabel} is the highest tier. Every new review keeps the catalogue current.`;
  }
  const n = Math.max(1, profile.reviewsToNextTier);
  return `Write ${n} more ${n === 1 ? 'review' : 'reviews'} to reach ${profile.nextTierLabel}.`;
}

/** One line telling the student what the next recognition takes. */
export function nextRecognitionNudge(profile: RecognitionNudgeFields): string {
  if (!profile.nextRecognitionLabel) {
    return `${profile.recognitionTierLabel ?? 'This'} is the highest recognition.`;
  }
  const n = Math.max(1, profile.likesToNextRecognition);
  const verb = profile.recognitionTier ? 'reach' : 'earn';
  return `${n} more helpful ${n === 1 ? 'mark' : 'marks'} on your reviews to ${verb} ${profile.nextRecognitionLabel}.`;
}
