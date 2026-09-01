import { Review, TuitionPattern, UnitDetails, UnitSummary } from '../models/unit.model';

/** The only fields a link on an index page needs. */
export type UnitLink = Pick<UnitSummary, 'code' | 'name'>;
import { FacultyHub, facultyHubByName, facultyPagePath } from './faculty.util';

const MAX_DESCRIPTION_LENGTH = 160;
const MAX_TITLE_LENGTH = 60;
const BRAND_SUFFIX = ' | CurtinHonestly';
const MAX_JSON_LD_REVIEWS = 10;

/**
 * Serialize a JSON-LD object for embedding in a <script type="application/ld+json">.
 *
 * A <script> is a raw-text HTML element: server-side rendering serializes its text
 * content verbatim, without entity-encoding (entities inside raw-text elements are
 * not decoded by parsers). JSON.stringify does NOT escape `<`, `>` or `&`, so a
 * review containing `</script>...` would break out of the tag in the prerendered
 * HTML and inject arbitrary markup (stored XSS). Escaping these to their \u form
 * keeps the JSON-LD semantically identical for consumers (a JSON parser decodes
 * < back to `<`) while making it impossible to close the script element early.
 */
export function serializeJsonLd(data: unknown): string {
  return JSON.stringify(data)
    .replace(/</g, '\\u003c')
    .replace(/>/g, '\\u003e')
    .replace(/&/g, '\\u0026');
}

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

/**
 * Appends the brand only where it still fits.
 *
 * Search results cut a title around 60 characters, and unit names run long:
 * "INDH2008 Behavioural Science for Indigenous Mental Health Practitioners"
 * is 71 before any suffix. Spending the last 17 on boilerplate pushes out the
 * words that tell this page apart from the handbook's, and the brand is not
 * lost by dropping it, because the result already shows the domain.
 */
function withBrand(title: string): string {
  return title.length + BRAND_SUFFIX.length <= MAX_TITLE_LENGTH ? `${title}${BRAND_SUFFIX}` : title;
}

export function homePageTitle(): string {
  return 'Curtin University Unit Reviews | CurtinHonestly';
}

export function homePageDescription(): string {
  return 'Read honest student reviews for Curtin University units. Ratings, workload, grades, and would-take-again scores to help you choose your units.';
}

export function unitPageTitle(code: string, name: string, numberOfReviews = 0): string {
  // The review count goes ahead of the brand: it is the part that says this
  // page has something the official handbook entry does not.
  if (numberOfReviews > 0) {
    return withBrand(`${code} ${name} - ${numberOfReviews} Reviews`);
  }
  return withBrand(`${code} ${name}`);
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

export function facultyPageTitle(facultyName: string): string {
  return withBrand(`Curtin ${facultyName} Units`);
}

export function facultyPageDescription(facultyName: string, unitCount: number): string {
  if (unitCount > 0) {
    return truncateDescription(
      `Browse all ${unitCount} ${facultyName} units at Curtin University. Student ratings, ` +
        'workload, and honest reviews for every unit.'
    );
  }

  return truncateDescription(
    `Browse ${facultyName} units at Curtin University. Student ratings, workload, and honest ` +
      'reviews from the people who took them.'
  );
}

/**
 * Groups a faculty's units under their four-letter code prefix.
 *
 * A flat list of several hundred links is hard to scan and says nothing about
 * what is in it. The prefix is the subject: grouping under it turns one page
 * into a set of labelled sections a reader can jump between, and puts the
 * subject codes students actually search into the page as headings.
 */
export function groupUnitsByCodePrefix<T extends UnitLink>(units: T[]): { prefix: string; units: T[] }[] {
  const groups = new Map<string, T[]>();

  for (const unit of units) {
    const prefix = unit.code.slice(0, 4).toUpperCase();
    const group = groups.get(prefix);
    if (group) {
      group.push(unit);
    } else {
      groups.set(prefix, [unit]);
    }
  }

  return [...groups.entries()]
    .map(([prefix, grouped]) => ({
      prefix,
      units: [...grouped].sort((a, b) => a.code.localeCompare(b.code)),
    }))
    .sort((a, b) => a.prefix.localeCompare(b.prefix));
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
      // The machine-readable half of the about page. A review platform is asked
      // who stands behind it, by readers and by the answer engines that decide
      // whether to quote it, and an unattributed one has no answer. sameAs
      // points at profiles that can be checked independently.
      founder: {
        '@type': 'Person',
        '@id': `${base}/#founder`,
        name: 'Devon Clark',
        sameAs: [
          'https://www.linkedin.com/in/devon-clark-22b235212/',
          'https://github.com/devon-clarkk',
        ],
      },
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

  // schema.org types `about` as a Thing, not a string. A bare string validates
  // loosely but gives a consumer nothing to resolve; a named Thing does.
  if (unit.area?.trim()) {
    course['about'] = { '@type': 'Thing', name: unit.area.trim() };
  }

  // Credit points are a credit count, which is what numberOfCredits is for.
  // educationalCredentialAwarded names a credential, a degree or certificate,
  // so "25 credit points" was the wrong property rather than a wrong value.
  if (unit.credits > 0) {
    course['numberOfCredits'] = unit.credits;
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
        itemListElement: buildUnitBreadcrumbTrail(unit, base, url),
      },
      course,
    ],
  };
}

/**
 * Home, then the unit's faculty hub, then the unit.
 *
 * The hub rung is what makes the trail worth having: it is the page that links
 * every unit in the faculty, so declaring it here matches the crawl path a
 * reader and a crawler both take. A unit whose faculty does not resolve keeps
 * the two-rung trail rather than losing its breadcrumb.
 */
function buildUnitBreadcrumbTrail(
  unit: UnitDetails,
  base: string,
  url: string
): Record<string, unknown>[] {
  const trail: Record<string, unknown>[] = [
    {
      '@type': 'ListItem',
      position: 1,
      name: 'Curtin University Unit Reviews',
      item: `${base}/`,
    },
  ];

  const hub = facultyHubByName(unit.faculty);
  if (hub) {
    trail.push({
      '@type': 'ListItem',
      position: 2,
      name: `${hub.name} Units`,
      item: `${base}${facultyPagePath(hub.slug)}`,
    });
  }

  trail.push({
    '@type': 'ListItem',
    position: trail.length + 1,
    name: `${unit.code} ${unit.name}`,
    item: url,
  });

  return trail;
}

/**
 * A faculty hub is a list of links, and CollectionPage plus ItemList is how that
 * is said in schema.org. The ItemList carries every unit on the page in render
 * order, which is the same set the HTML links, so the markup describes the page
 * rather than making a separate claim about it.
 */
export function buildFacultyJsonLd(hub: FacultyHub, units: UnitLink[], siteUrl: string) {
  const base = siteUrl.replace(/\/$/, '');
  const url = `${base}${facultyPagePath(hub.slug)}`;

  return {
    '@context': 'https://schema.org',
    '@graph': [
      ...buildSitewideJsonLd(siteUrl),
      {
        '@type': 'BreadcrumbList',
        '@id': `${url}#breadcrumb`,
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
            name: `${hub.name} Units`,
            item: url,
          },
        ],
      },
      {
        '@type': 'CollectionPage',
        '@id': `${url}#collection`,
        url,
        name: `Curtin ${hub.name} Units`,
        description: facultyPageDescription(hub.name, units.length),
        inLanguage: 'en-AU',
        about: {
          '@type': 'CollegeOrUniversity',
          name: 'Curtin University',
          sameAs: 'https://www.curtin.edu.au/',
        },
        mainEntity: {
          '@type': 'ItemList',
          '@id': `${url}#units`,
          numberOfItems: units.length,
          itemListElement: units.map((unit, index) => ({
            '@type': 'ListItem',
            position: index + 1,
            name: `${unit.code}: ${unit.name}`,
            url: `${base}${unitPagePath(unit.code)}`,
          })),
        },
      },
    ],
  };
}

/**
 * An ordinary content page: about, contact. Carries the sitewide nodes so the
 * publisher is identified, plus a breadcrumb back to the home page.
 */
export function buildInfoPageJsonLd(
  siteUrl: string,
  page: { title: string; description: string; path: string }
) {
  const base = siteUrl.replace(/\/$/, '');
  const url = `${base}${page.path}`;

  return {
    '@context': 'https://schema.org',
    '@graph': [
      ...buildSitewideJsonLd(siteUrl),
      {
        '@type': 'BreadcrumbList',
        '@id': `${url}#breadcrumb`,
        itemListElement: [
          { '@type': 'ListItem', position: 1, name: 'Curtin University Unit Reviews', item: `${base}/` },
          { '@type': 'ListItem', position: 2, name: page.title, item: url },
        ],
      },
      {
        '@type': 'WebPage',
        '@id': `${url}#page`,
        url,
        name: page.title,
        description: page.description,
        inLanguage: 'en-AU',
        isPartOf: { '@id': `${base}/#website` },
        publisher: { '@id': `${base}/#org` },
      },
    ],
  };
}

export function buildHomeJsonLd(siteUrl: string) {
  return {
    '@context': 'https://schema.org',
    '@graph': buildSitewideJsonLd(siteUrl),
  };
}
