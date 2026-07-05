import { describe, expect, it } from 'vitest';
import { UnitDetails } from '../models/unit.model';
import {
  buildHomeJsonLd,
  buildSitewideJsonLd,
  buildUnitJsonLd,
  reviewAuthorName,
  safeHttpsUrl,
  unitPageTitle,
} from './unit-seo.utils';

const SITE_URL = 'https://curtinhonestly.com';

function baseUnit(overrides: Partial<UnitDetails> = {}): UnitDetails {
  return {
    code: 'ISYS1000',
    name: 'Introduction to Business Information Systems',
    description: 'An introductory IS unit.',
    unitLink: 'https://handbook.curtin.edu.au/units/isys1000',
    faculty: 'Business and Law',
    level: 'UNDERGRADUATE',
    area: '',
    fieldOfEducation: '',
    credits: 25,
    contactHours: 3,
    resultType: 'Grade',
    tuitionPatterns: [],
    prerequisiteGroups: [],
    numberOfReviews: 0,
    averageRating: 0,
    averageWorkload: 0,
    averageFinalGrade: 0,
    wouldTakeAgainRatio: 0,
    reviews: [],
    ...overrides,
  };
}

function getCourseNode(result: ReturnType<typeof buildUnitJsonLd>): Record<string, unknown> {
  const graph = result['@graph'] as Record<string, unknown>[];
  return graph.find((node) => node['@type'] === 'Course') as Record<string, unknown>;
}

function getBreadcrumbNode(result: ReturnType<typeof buildUnitJsonLd>): Record<string, unknown> {
  const graph = result['@graph'] as Record<string, unknown>[];
  return graph.find((node) => node['@type'] === 'BreadcrumbList') as Record<string, unknown>;
}

// ─── Item 1: review count in title tag ───────────────────────────────────────

describe('unitPageTitle', () => {
  it('includes unit name and review count when reviews exist', () => {
    expect(unitPageTitle('ISYS1000', 'Introduction to BIS', 12)).toBe(
      'ISYS1000 Introduction to BIS — 12 Reviews | CurtinHonestly'
    );
  });

  it('uses code and name without review count when no reviews', () => {
    expect(unitPageTitle('ISYS1000', 'Introduction to BIS', 0)).toBe(
      'ISYS1000 Introduction to BIS | CurtinHonestly'
    );
  });

  it('defaults to no-review format when numberOfReviews is omitted', () => {
    expect(unitPageTitle('ISYS1000', 'Introduction to BIS')).toBe(
      'ISYS1000 Introduction to BIS | CurtinHonestly'
    );
  });
});

// ─── Phase 1: review snippet gating ──────────────────────────────────────────

describe('buildUnitJsonLd', () => {
  it('omits aggregateRating and review when there are no reviews', () => {
    const course = getCourseNode(buildUnitJsonLd(baseUnit(), SITE_URL));

    expect(course['courseCode']).toBe('ISYS1000');
    expect(course['aggregateRating']).toBeUndefined();
    expect(course['review']).toBeUndefined();
  });

  it('includes aggregateRating and review array when reviews exist', () => {
    const unit = baseUnit({
      numberOfReviews: 2,
      averageRating: 4.5,
      reviews: [
        {
          rating: 5,
          reviewText: 'Great unit with practical assignments.',
          semesterTaken: 'Sem 1 2025',
          professor: 'Smith',
          workload: 6,
          hasExam: true,
          wouldTakeAgain: true,
          reviewerVerified: true,
          createdAt: '2025-06-01T10:00:00Z',
        },
        {
          rating: 4,
          reviewText: 'Challenging but fair.',
          semesterTaken: 'Sem 2 2024',
          professor: 'Jones',
          workload: 7,
          hasExam: false,
          wouldTakeAgain: true,
          reviewerVerified: false,
          createdAt: '2024-11-15T08:30:00Z',
        },
      ],
    });

    const course = getCourseNode(buildUnitJsonLd(unit, SITE_URL));
    const aggregateRating = course['aggregateRating'] as Record<string, unknown>;
    const reviews = course['review'] as Record<string, unknown>[];

    expect(aggregateRating).toEqual({
      '@type': 'AggregateRating',
      ratingValue: '4.5',
      reviewCount: 2,
      bestRating: '5',
      worstRating: '1',
    });
    expect(reviews).toHaveLength(2);
    expect(reviews[0]).toMatchObject({
      '@type': 'Review',
      author: { '@type': 'Person', name: reviewAuthorName(true) },
      datePublished: '2025-06-01T10:00:00Z',
      reviewBody: 'Great unit with practical assignments.',
      reviewRating: {
        '@type': 'Rating',
        ratingValue: '5',
        bestRating: '5',
        worstRating: '1',
      },
    });
  });

  it('includes aggregateRating even when average rating is zero', () => {
    const unit = baseUnit({
      numberOfReviews: 1,
      averageRating: 0,
      reviews: [
        {
          rating: 0,
          reviewText: 'Very difficult.',
          semesterTaken: 'Sem 1 2025',
          professor: 'Smith',
          workload: 9,
          hasExam: true,
          wouldTakeAgain: false,
          reviewerVerified: true,
          createdAt: '2025-03-01T12:00:00Z',
        },
      ],
    });

    const course = getCourseNode(buildUnitJsonLd(unit, SITE_URL));
    const aggregateRating = course['aggregateRating'] as Record<string, unknown>;

    expect(aggregateRating['ratingValue']).toBe('0.0');
    expect(aggregateRating['reviewCount']).toBe(1);
  });

  it('excludes reviews with empty text from the review array', () => {
    const unit = baseUnit({
      numberOfReviews: 2,
      averageRating: 3,
      reviews: [
        {
          rating: 3,
          reviewText: '   ',
          semesterTaken: 'Sem 1 2025',
          professor: 'Smith',
          workload: 5,
          hasExam: false,
          wouldTakeAgain: false,
          reviewerVerified: false,
        },
        {
          rating: 4,
          reviewText: 'Solid overview.',
          semesterTaken: 'Sem 2 2024',
          professor: 'Jones',
          workload: 4,
          hasExam: true,
          wouldTakeAgain: true,
          reviewerVerified: true,
          createdAt: '2024-12-01T09:00:00Z',
        },
      ],
    });

    const course = getCourseNode(buildUnitJsonLd(unit, SITE_URL));
    const reviews = course['review'] as Record<string, unknown>[];

    expect(reviews).toHaveLength(1);
    expect(reviews[0]['reviewBody']).toBe('Solid overview.');
    expect((reviews[0]['author'] as Record<string, unknown>)['name']).toBe(reviewAuthorName(true));
  });

  it('uses Student as author name for unverified reviews', () => {
    const unit = baseUnit({
      numberOfReviews: 1,
      averageRating: 3,
      reviews: [
        {
          rating: 3,
          reviewText: 'Decent unit.',
          semesterTaken: 'Sem 1 2025',
          professor: 'Smith',
          workload: 5,
          hasExam: false,
          wouldTakeAgain: false,
          reviewerVerified: false,
          createdAt: '2025-01-01T10:00:00Z',
        },
      ],
    });

    const course = getCourseNode(buildUnitJsonLd(unit, SITE_URL));
    const reviews = course['review'] as Record<string, unknown>[];

    expect((reviews[0]['author'] as Record<string, unknown>)['name']).toBe('Student');
  });
});

// ─── Phase 2: course enrichment ──────────────────────────────────────────────

describe('buildUnitJsonLd — Phase 2 course metadata', () => {
  it('includes @id on Course and BreadcrumbList with stable URL-based IDs', () => {
    const result = buildUnitJsonLd(baseUnit(), SITE_URL);
    const course = getCourseNode(result);
    const breadcrumb = getBreadcrumbNode(result);

    expect(course['@id']).toBe('https://curtinhonestly.com/units/ISYS1000#course');
    expect(breadcrumb['@id']).toBe('https://curtinhonestly.com/units/ISYS1000#breadcrumb');
  });

  it('includes sameAs when unitLink is a valid https URL', () => {
    const course = getCourseNode(buildUnitJsonLd(baseUnit(), SITE_URL));
    expect(course['sameAs']).toBe('https://handbook.curtin.edu.au/units/isys1000');
  });

  it('omits sameAs when unitLink is not https', () => {
    const course = getCourseNode(
      buildUnitJsonLd(baseUnit({ unitLink: 'http://handbook.curtin.edu.au/units/isys1000' }), SITE_URL)
    );
    expect(course['sameAs']).toBeUndefined();
  });

  it('omits sameAs when unitLink is empty', () => {
    const course = getCourseNode(buildUnitJsonLd(baseUnit({ unitLink: '' }), SITE_URL));
    expect(course['sameAs']).toBeUndefined();
  });

  it('includes about when area is set', () => {
    const course = getCourseNode(
      buildUnitJsonLd(baseUnit({ area: 'Business Information Systems' }), SITE_URL)
    );
    expect(course['about']).toBe('Business Information Systems');
  });

  it('omits about when area is empty', () => {
    const course = getCourseNode(buildUnitJsonLd(baseUnit({ area: '' }), SITE_URL));
    expect(course['about']).toBeUndefined();
  });

  it('includes educationalCredentialAwarded when credits > 0', () => {
    const course = getCourseNode(buildUnitJsonLd(baseUnit({ credits: 25 }), SITE_URL));
    expect(course['educationalCredentialAwarded']).toBe('25 credit points');
  });

  it('omits educationalCredentialAwarded when credits is 0', () => {
    const course = getCourseNode(buildUnitJsonLd(baseUnit({ credits: 0 }), SITE_URL));
    expect(course['educationalCredentialAwarded']).toBeUndefined();
  });

  it('uses full description without truncation in JSON-LD', () => {
    const longDesc = 'A '.repeat(200).trim();
    const course = getCourseNode(buildUnitJsonLd(baseUnit({ description: longDesc }), SITE_URL));
    expect((course['description'] as string).length).toBeGreaterThan(160);
  });
});

// ─── Phase 2: safeHttpsUrl ────────────────────────────────────────────────────

describe('safeHttpsUrl', () => {
  it('returns the URL for valid https links', () => {
    expect(safeHttpsUrl('https://handbook.curtin.edu.au/')).toBe('https://handbook.curtin.edu.au/');
  });

  it('returns undefined for http links', () => {
    expect(safeHttpsUrl('http://handbook.curtin.edu.au/')).toBeUndefined();
  });

  it('returns undefined for empty string', () => {
    expect(safeHttpsUrl('')).toBeUndefined();
  });

  it('returns undefined for undefined', () => {
    expect(safeHttpsUrl(undefined)).toBeUndefined();
  });

  it('returns undefined for javascript: URLs', () => {
    expect(safeHttpsUrl('javascript:alert(1)')).toBeUndefined();
  });
});

// ─── Phase 3: sitewide graph ──────────────────────────────────────────────────

// ─── Item 2: inLanguage on WebSite ───────────────────────────────────────────

describe('buildSitewideJsonLd — inLanguage', () => {
  it('sets inLanguage to en-AU on the WebSite node', () => {
    const website = buildSitewideJsonLd(SITE_URL).find((n) => n['@type'] === 'WebSite')!;
    expect(website['inLanguage']).toBe('en-AU');
  });
});

// ─── Item 5: dateModified on Course ──────────────────────────────────────────

describe('buildUnitJsonLd — dateModified', () => {
  it('sets dateModified to the latest review createdAt', () => {
    const unit = baseUnit({
      numberOfReviews: 2,
      averageRating: 4,
      reviews: [
        {
          rating: 4,
          reviewText: 'Good unit.',
          semesterTaken: 'Sem 1 2024',
          professor: 'Smith',
          workload: 5,
          hasExam: false,
          wouldTakeAgain: true,
          reviewerVerified: false,
          createdAt: '2024-06-01T10:00:00Z',
        },
        {
          rating: 4,
          reviewText: 'Also good.',
          semesterTaken: 'Sem 2 2024',
          professor: 'Jones',
          workload: 5,
          hasExam: false,
          wouldTakeAgain: true,
          reviewerVerified: false,
          createdAt: '2025-01-15T08:00:00Z',
        },
      ],
    });

    const course = getCourseNode(buildUnitJsonLd(unit, SITE_URL));
    expect(course['dateModified']).toBe('2025-01-15T08:00:00Z');
  });

  it('omits dateModified when no reviews have createdAt', () => {
    const unit = baseUnit({
      numberOfReviews: 1,
      averageRating: 3,
      reviews: [
        {
          rating: 3,
          reviewText: 'OK.',
          semesterTaken: 'Sem 1 2025',
          professor: 'Smith',
          workload: 5,
          hasExam: false,
          wouldTakeAgain: false,
          reviewerVerified: false,
        },
      ],
    });

    const course = getCourseNode(buildUnitJsonLd(unit, SITE_URL));
    expect(course['dateModified']).toBeUndefined();
  });

  it('omits dateModified when there are no reviews', () => {
    const course = getCourseNode(buildUnitJsonLd(baseUnit(), SITE_URL));
    expect(course['dateModified']).toBeUndefined();
  });
});

describe('buildSitewideJsonLd', () => {
  it('returns WebSite and Organization nodes', () => {
    const nodes = buildSitewideJsonLd(SITE_URL);
    const types = nodes.map((n) => n['@type']);
    expect(types).toContain('WebSite');
    expect(types).toContain('Organization');
  });

  it('WebSite node has correct @id and url', () => {
    const website = buildSitewideJsonLd(SITE_URL).find((n) => n['@type'] === 'WebSite')!;
    expect(website['@id']).toBe('https://curtinhonestly.com/#website');
    expect(website['url']).toBe('https://curtinhonestly.com/');
  });

  it('Organization node has correct @id and disclaimer description', () => {
    const org = buildSitewideJsonLd(SITE_URL).find((n) => n['@type'] === 'Organization')!;
    expect(org['@id']).toBe('https://curtinhonestly.com/#org');
    expect(org['description']).toBe(
      'Independent student platform. Not affiliated with Curtin University.'
    );
  });

  it('handles trailing slash in siteUrl', () => {
    const nodes = buildSitewideJsonLd('https://curtinhonestly.com/');
    const website = nodes.find((n) => n['@type'] === 'WebSite')!;
    expect(website['@id']).toBe('https://curtinhonestly.com/#website');
  });
});

describe('buildUnitJsonLd — Phase 3 sitewide graph', () => {
  it('includes WebSite and Organization nodes in @graph', () => {
    const result = buildUnitJsonLd(baseUnit(), SITE_URL);
    const graph = result['@graph'] as Record<string, unknown>[];
    const types = graph.map((n) => n['@type']);
    expect(types).toContain('WebSite');
    expect(types).toContain('Organization');
    expect(types).toContain('BreadcrumbList');
    expect(types).toContain('Course');
  });
});

describe('buildHomeJsonLd — Phase 3 sitewide graph', () => {
  it('returns a @graph with WebSite and Organization', () => {
    const result = buildHomeJsonLd(SITE_URL);
    expect(result['@context']).toBe('https://schema.org');
    const graph = result['@graph'] as Record<string, unknown>[];
    expect(graph.map((n) => n['@type'])).toContain('WebSite');
    expect(graph.map((n) => n['@type'])).toContain('Organization');
  });

  it('does not include CollegeOrUniversity in WebSite.about', () => {
    const result = buildHomeJsonLd(SITE_URL);
    const graph = result['@graph'] as Record<string, unknown>[];
    const website = graph.find((n) => n['@type'] === 'WebSite')!;
    expect(website['about']).toBeUndefined();
  });
});

// ─── Phase 4: hasCourseInstance ───────────────────────────────────────────────

describe('buildUnitJsonLd — Phase 4 hasCourseInstance', () => {
  it('includes courseWorkload from contactHours as ISO 8601', () => {
    const course = getCourseNode(buildUnitJsonLd(baseUnit({ contactHours: 3 }), SITE_URL));
    const instance = course['hasCourseInstance'] as Record<string, unknown>;
    expect(instance['courseWorkload']).toBe('PT3H');
  });

  it('omits hasCourseInstance when contactHours is 0 and no tuition patterns', () => {
    const course = getCourseNode(
      buildUnitJsonLd(baseUnit({ contactHours: 0, tuitionPatterns: [] }), SITE_URL)
    );
    expect(course['hasCourseInstance']).toBeUndefined();
  });

  it('derives courseMode blended for online lecture + tutorial', () => {
    const course = getCourseNode(
      buildUnitJsonLd(
        baseUnit({
          contactHours: 0,
          tuitionPatterns: [
            { type: 'Lecture', duration: '2 hours' },
            { type: 'Tutorial', duration: '1 hour' },
          ],
        }),
        SITE_URL
      )
    );
    const instance = course['hasCourseInstance'] as Record<string, unknown>;
    expect(instance['courseMode']).toBe('blended');
  });

  it('derives courseMode online for lecture only', () => {
    const course = getCourseNode(
      buildUnitJsonLd(
        baseUnit({
          contactHours: 0,
          tuitionPatterns: [{ type: 'Lecture', duration: '2 hours' }],
        }),
        SITE_URL
      )
    );
    const instance = course['hasCourseInstance'] as Record<string, unknown>;
    expect(instance['courseMode']).toBe('online');
  });

  it('derives courseMode onsite for tutorial only', () => {
    const course = getCourseNode(
      buildUnitJsonLd(
        baseUnit({
          contactHours: 0,
          tuitionPatterns: [{ type: 'Tutorial', duration: '2 hours' }],
        }),
        SITE_URL
      )
    );
    const instance = course['hasCourseInstance'] as Record<string, unknown>;
    expect(instance['courseMode']).toBe('onsite');
  });

  it('omits courseMode for empty tuition patterns', () => {
    const course = getCourseNode(
      buildUnitJsonLd(baseUnit({ tuitionPatterns: [], contactHours: 3 }), SITE_URL)
    );
    const instance = course['hasCourseInstance'] as Record<string, unknown>;
    expect(instance['courseMode']).toBeUndefined();
    expect(instance['courseWorkload']).toBe('PT3H');
  });
});
