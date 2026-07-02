import { UnitDetails } from '../models/unit.model';

const MAX_DESCRIPTION_LENGTH = 160;

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

export function unitPageTitle(code: string, name: string): string {
  return `${code} ${name} Reviews | Curtin University | CurtinHonestly`;
}

export function unitPageDescription(unit: UnitDetails): string {
  const reviews = unit.numberOfReviews ?? 0;
  const rating = (unit.averageRating ?? 0).toFixed(1);
  const takeAgain = Math.round(normalizeTakeAgainRatio(unit.wouldTakeAgainRatio ?? 0) * 100);

  if (reviews > 0) {
    return truncateDescription(
      `${unit.code} at Curtin University — ${rating}★ from ${reviews} student reviews. ${takeAgain}% would take again. Read honest experiences for ${unit.name}.`
    );
  }

  return truncateDescription(
    `Student reviews for ${unit.code} (${unit.name}) at Curtin University. Ratings, workload, grades, and honest feedback on CurtinHonestly.`
  );
}

export function unitPagePath(code: string): string {
  return `/units/${encodeURIComponent(code)}`;
}

export function buildUnitJsonLd(unit: UnitDetails, siteUrl: string) {
  const url = `${siteUrl.replace(/\/$/, '')}${unitPagePath(unit.code)}`;
  const graph: Record<string, unknown>[] = [
    {
      '@type': 'BreadcrumbList',
      itemListElement: [
        {
          '@type': 'ListItem',
          position: 1,
          name: 'Curtin University Unit Reviews',
          item: siteUrl,
        },
        {
          '@type': 'ListItem',
          position: 2,
          name: `${unit.code} ${unit.name}`,
          item: url,
        },
      ],
    },
    {
      '@type': 'Course',
      name: `${unit.code} — ${unit.name}`,
      description: truncateDescription(unit.description || unitPageDescription(unit), 300),
      url,
      provider: {
        '@type': 'CollegeOrUniversity',
        name: 'Curtin University',
        sameAs: 'https://www.curtin.edu.au/',
      },
    },
  ];

  if (unit.numberOfReviews > 0 && unit.averageRating > 0) {
    (graph[1] as Record<string, unknown>)['aggregateRating'] = {
      '@type': 'AggregateRating',
      ratingValue: unit.averageRating.toFixed(1),
      reviewCount: unit.numberOfReviews,
      bestRating: '5',
      worstRating: '1',
    };
  }

  return {
    '@context': 'https://schema.org',
    '@graph': graph,
  };
}

export function buildHomeJsonLd(siteUrl: string) {
  return {
    '@context': 'https://schema.org',
    '@type': 'WebSite',
    name: 'CurtinHonestly',
    alternateName: 'Curtin Honestly',
    url: siteUrl,
    description: homePageDescription(),
    about: {
      '@type': 'CollegeOrUniversity',
      name: 'Curtin University',
      sameAs: 'https://www.curtin.edu.au/',
    },
  };
}
