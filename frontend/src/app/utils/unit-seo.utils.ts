import { Review, TuitionPattern, UnitDetails } from '../models/unit.model';

const MAX_DESCRIPTION_LENGTH = 160;
const MAX_JSON_LD_REVIEWS = 10;

export function normalizeTakeAgainRatio(ratio: number): number {
  return ratio > 1 ? ratio / 100 : ratio;
}

export function truncateDescription(text: string, maxLength = MAX_DESCRIPTION_LENGTH): string {
  const cleaned = text.replace(/\s+/g, ' ').trim();
  if (cleaned.length <= maxLength) {
    return cleaned;
  }
  return `${cleaned.slice(0, maxLength - 1).trimEnd()}…`;
}

export function homePageTitle(): string {
  return 'Curtin University Unit Reviews | CurtinHonestly';
}

export function homePageDescription(): string {
  return 'Read honest student reviews for Curtin University units. Ratings, workload, grades, and would-take-again scores to help you choose your units.';
}

export function unitPageTitle(code: string, name: string, numberOfReviews = 0): string {
  if (numberOfReviews > 0) {
    return `${code} ${name} - ${numberOfReviews} Reviews | CurtinHonestly`;
  }
  return `${code} ${name} | CurtinHonestly`;
}

export function unitPageDescription(unit: UnitDetails): string {
  const reviews = unit.numberOfReviews ?? 0;
  const rating = (unit.averageRating ?? 0).toFixed(1);
  const takeAgain = Math.round(normalizeTakeAgainRatio(unit.wouldTakeAgainRatio ?? 0) * 100);

  if (reviews > 0) {
    return truncateDescription(
      `${unit.code} at Curtin University. ${rating}★ from ${reviews} student reviews. ${takeAgain}% would take again. Read honest experiences for ${unit.name}.`
    );
  }

  return truncateDescription(
    `Student reviews for ${unit.code} (${unit.name}) at Curtin University. Ratings, workload, grades, and honest feedback on CurtinHonestly.`
  );
}

export function unitPagePath(code: string): string {
  return `/units/${encodeURIComponent(code)}`;
}

export function reviewAuthorName(reviewerVerified: boolean): string {
  return reviewerVerified ? 'Verified Curtin Student' : 'Student';
}

export function safeHttpsUrl(url: string | undefined): string | undefined {
  const trimmed = url?.trim();
  if (trimmed && /^https:\/\//i.test(trimmed)) {
    return trimmed;
  }
  return undefined;
}

function reviewsForJsonLd(reviews: Review[]): Review[] {
  return reviews.filter((review) => review.reviewText?.trim());
}

function buildReviewJsonLd(review: Review): Record<string, unknown> {
  const entry: Record<string, unknown> = {
    '@type': 'Review',
    author: { '@type': 'Person', name: reviewAuthorName(review.reviewerVerified) },
    reviewBody: review.reviewText.trim(),
    reviewRating: {
      '@type': 'Rating',
      ratingValue: String(review.rating),
      bestRating: '5',
      worstRating: '1',
    },
  };

  if (review.createdAt) {
    entry['datePublished'] = review.createdAt;
  }

  return entry;
}

function deriveCourseMode(patterns: TuitionPattern[]): string | undefined {
  if (!patterns || patterns.length === 0) return undefined;

  const types = patterns.map((p) => p.type.toLowerCase());
  const hasOnline = types.some(
    (t) => t.includes('online') || t.includes('lecture')
  );
  const hasInPerson = types.some(
    (t) =>
      t.includes('tutorial') ||
      t.includes('workshop') ||
      t.includes('in-person') ||
      t.includes('laboratory')
  );

  if (hasOnline && hasInPerson) return 'blended';
  if (hasOnline && !hasInPerson) return 'online';
  if (!hasOnline && hasInPerson) return 'onsite';
  return undefined;
}

export function buildSitewideJsonLd(siteUrl: string): Record<string, unknown>[] {
  const base = siteUrl.replace(/\/$/, '');
  return [
    {
      '@type': 'WebSite',
      '@id': `${base}/#website`,
      url: `${base}/`,
      name: 'CurtinHonestly',
      inLanguage: 'en-AU',
      description: 'Independent student platform for honest Curtin University unit reviews.',
    },
    {
      '@type': 'Organization',
      '@id': `${base}/#org`,
      name: 'CurtinHonestly',
      url: `${base}/`,
      logo: `${base}/assets/images/logo.png`,
      description: 'Independent student platform. Not affiliated with Curtin University.',
    },
  ];
}

export function buildUnitJsonLd(unit: UnitDetails, siteUrl: string) {
  const base = siteUrl.replace(/\/$/, '');
  const url = `${base}${unitPagePath(unit.code)}`;
  const courseId = `${url}#course`;
  const breadcrumbId = `${url}#breadcrumb`;

  const course: Record<string, unknown> = {
    '@type': 'Course',
    '@id': courseId,
    name: `${unit.code}: ${unit.name}`,
    description: (unit.description || unitPageDescription(unit)).replace(/\s+/g, ' ').trim(),
    courseCode: unit.code,
    url,
    provider: {
      '@type': 'CollegeOrUniversity',
      name: 'Curtin University',
      sameAs: 'https://www.curtin.edu.au/',
    },
  };

  const handbookUrl = safeHttpsUrl(unit.unitLink);
  if (handbookUrl) {
    course['sameAs'] = handbookUrl;
  }

  if (unit.area?.trim()) {
    course['about'] = unit.area.trim();
  }

  if (unit.credits > 0) {
    course['educationalCredentialAwarded'] = `${unit.credits} credit points`;
  }

  const courseMode = deriveCourseMode(unit.tuitionPatterns ?? []);
  const contactHours = unit.contactHours;
  if (courseMode || (contactHours && contactHours > 0)) {
    const instance: Record<string, unknown> = { '@type': 'CourseInstance' };
    if (courseMode) instance['courseMode'] = courseMode;
    if (contactHours && contactHours > 0) instance['courseWorkload'] = `PT${contactHours}H`;
    course['hasCourseInstance'] = instance;
  }

  const eligibleReviews = reviewsForJsonLd(unit.reviews ?? []);
  const reviewCount = unit.numberOfReviews ?? 0;

  const latestReviewDate = (unit.reviews ?? [])
    .map((r) => r.createdAt)
    .filter((d): d is string => Boolean(d))
    .sort()
    .at(-1);
  if (latestReviewDate) {
    course['dateModified'] = latestReviewDate;
  }

  if (reviewCount > 0) {
    course['aggregateRating'] = {
      '@type': 'AggregateRating',
      ratingValue: (unit.averageRating ?? 0).toFixed(1),
      reviewCount,
      bestRating: '5',
      worstRating: '1',
    };

    if (eligibleReviews.length > 0) {
      course['review'] = eligibleReviews
        .slice(0, MAX_JSON_LD_REVIEWS)
        .map((review) => buildReviewJsonLd(review));
    }
  }

  return {
    '@context': 'https://schema.org',
    '@graph': [
      ...buildSitewideJsonLd(siteUrl),
      {
        '@type': 'BreadcrumbList',
        '@id': breadcrumbId,
        itemListElement: [
          {
            '@type': 'ListItem',
            position: 1,
            name: 'Curtin University Unit Reviews',
            item: `${base}/`,
          },
          {
            '@type': 'ListItem',
            position: 2,
            name: `${unit.code} ${unit.name}`,
            item: url,
          },
        ],
      },
      course,
    ],
  };
}

export function buildHomeJsonLd(siteUrl: string) {
  return {
    '@context': 'https://schema.org',
    '@graph': buildSitewideJsonLd(siteUrl),
  };
}
