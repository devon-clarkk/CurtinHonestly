import { describe, expect, it } from 'vitest';
import {
  RECOGNITION_TIERS,
  REVIEWER_TIERS,
  nextRecognitionNudge,
  nextTierNudge,
  progressPercent,
  reviewerRecognitionDisplay,
  reviewerTierDisplay,
} from './reviewer-tier.util';

describe('reviewerTierDisplay', () => {
  it('returns nothing for an anonymised review', () => {
    expect(reviewerTierDisplay({})).toBeNull();
    expect(reviewerTierDisplay(null)).toBeNull();
    expect(reviewerTierDisplay({ reviewerTier: null })).toBeNull();
  });

  it('never shows a Lurker chip', () => {
    expect(reviewerTierDisplay({ reviewerTier: 'LURKER', reviewerTierLabel: 'Lurker' })).toBeNull();
  });

  it('uses the client map when the backend sends no label', () => {
    const display = reviewerTierDisplay({ reviewerTier: 'TOP_REVIEWER' });
    expect(display?.label).toBe('Top Reviewer');
    expect(display?.description).toBe(REVIEWER_TIERS.TOP_REVIEWER.description);
  });

  it('prefers the backend label but keeps the client description', () => {
    const display = reviewerTierDisplay({ reviewerTier: 'LEGEND', reviewerTierLabel: 'Legend of Bentley' });
    expect(display?.label).toBe('Legend of Bentley');
    expect(display?.description).toBe(REVIEWER_TIERS.LEGEND.description);
  });
});

describe('reviewerRecognitionDisplay', () => {
  it('returns nothing until recognition is earned', () => {
    expect(reviewerRecognitionDisplay({ reviewerRecognition: null })).toBeNull();
    expect(reviewerRecognitionDisplay({})).toBeNull();
  });

  it('shows the earned recognition', () => {
    const display = reviewerRecognitionDisplay({ reviewerRecognition: 'VALUED_REVIEWER' });
    expect(display?.label).toBe('Valued Reviewer');
    expect(display?.description).toBe(RECOGNITION_TIERS.VALUED_REVIEWER.description);
  });
});

describe('progressPercent', () => {
  it('is the rounded ratio, clamped to 0 to 100', () => {
    expect(progressPercent(0, 10)).toBe(0);
    expect(progressPercent(7, 10)).toBe(70);
    expect(progressPercent(1, 3)).toBe(33);
    expect(progressPercent(12, 10)).toBe(100);
    expect(progressPercent(-2, 10)).toBe(0);
  });

  it('reads as full at the top of the ladder, where there is no next threshold', () => {
    expect(progressPercent(25, 0)).toBe(100);
  });
});

describe('nextTierNudge', () => {
  it('counts the reviews still needed, with the right plural', () => {
    expect(nextTierNudge({ activityTierLabel: 'Regular', nextTierLabel: 'Top Reviewer', reviewsToNextTier: 3 }))
      .toBe('Write 3 more reviews to reach Top Reviewer.');
    expect(nextTierNudge({ activityTierLabel: 'Newcomer', nextTierLabel: 'Contributor', reviewsToNextTier: 1 }))
      .toBe('Write 1 more review to reach Contributor.');
  });

  it('acknowledges the top tier instead of asking for more', () => {
    const nudge = nextTierNudge({ activityTierLabel: 'Legend', nextTierLabel: null, reviewsToNextTier: 0 });
    expect(nudge).toContain('Legend is the highest tier');
  });
});

describe('nextRecognitionNudge', () => {
  it('says earn before any recognition and reach afterwards', () => {
    expect(nextRecognitionNudge({
      recognitionTier: null, recognitionTierLabel: null, nextRecognitionLabel: 'Appreciated', likesToNextRecognition: 5,
    })).toBe('5 more helpful marks on your reviews to earn Appreciated.');
    expect(nextRecognitionNudge({
      recognitionTier: 'APPRECIATED', recognitionTierLabel: 'Appreciated', nextRecognitionLabel: 'Valued Reviewer', likesToNextRecognition: 1,
    })).toBe('1 more helpful mark on your reviews to reach Valued Reviewer.');
  });

  it('acknowledges the top recognition', () => {
    expect(nextRecognitionNudge({
      recognitionTier: 'COMMUNITY_FAVOURITE', recognitionTierLabel: 'Community Favourite', nextRecognitionLabel: null, likesToNextRecognition: 0,
    })).toBe('Community Favourite is the highest recognition.');
  });
});
