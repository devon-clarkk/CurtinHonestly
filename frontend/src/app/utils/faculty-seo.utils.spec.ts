import { describe, expect, it } from 'vitest';
import { Faculty, FacultyDisplayNames, UnitDetails, UnitSummary } from '../models/unit.model';
import {
  FACULTY_HUBS,
  facultyHubByName,
  facultyHubBySlug,
  facultyPagePath,
  unitCodeForUrl,
} from './faculty.util';
import {
  buildFacultyJsonLd,
  buildUnitJsonLd,
  facultyPageDescription,
  facultyPageTitle,
  groupUnitsByCodePrefix,
} from './unit-seo.utils';

const SITE_URL = 'https://www.curtinhonestly.com';

function unit(code: string, name: string): UnitSummary {
  return {
    code,
    name,
    faculty: 'Science and Engineering',
    level: 'Undergraduate',
    numberOfReviews: 0,
    averageRating: 0,
    wouldTakeAgainRatio: 0,
  };
}

function unitDetails(faculty: string): UnitDetails {
  return {
    code: 'COMP1000',
    name: 'Unix and C Programming',
    description: 'An introductory unit.',
    unitLink: 'https://handbook.curtin.edu.au/units/comp1000',
    faculty,
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
  } as unknown as UnitDetails;
}

function breadcrumbTrail(faculty: string): Record<string, unknown>[] {
  const graph = buildUnitJsonLd(unitDetails(faculty), SITE_URL)['@graph'] as Record<string, unknown>[];
  const node = graph.find((n) => n['@type'] === 'BreadcrumbList')!;
  return node['itemListElement'] as Record<string, unknown>[];
}

// The hub list is the join between three things that cannot see each other: the
// Faculty enum the API filters on, the display string the API puts on a unit,
// and the slugs the router prerenders. A faculty missing from the JSON gets no
// hub page, and every unit in it silently loses its only inbound link.
describe('FACULTY_HUBS covers the Faculty enum', () => {
  it('has one hub per faculty, and no extras', () => {
    expect(FACULTY_HUBS.map((h) => h.faculty).sort()).toEqual(Object.values(Faculty).sort());
  });

  it('uses the display name the API returns on a unit', () => {
    for (const hub of FACULTY_HUBS) {
      expect(hub.name).toBe(FacultyDisplayNames[hub.faculty as Faculty]);
    }
  });

  it('has unique, URL-safe slugs', () => {
    const slugs = FACULTY_HUBS.map((h) => h.slug);
    expect(new Set(slugs).size).toBe(slugs.length);
    for (const slug of slugs) {
      expect(slug).toMatch(/^[a-z0-9-]+$/);
    }
  });
});

describe('facultyHubByName', () => {
  it('resolves every display name the API can return', () => {
    for (const name of Object.values(FacultyDisplayNames)) {
      expect(facultyHubByName(name)?.name).toBe(name);
    }
  });

  it('tolerates surrounding whitespace and casing', () => {
    expect(facultyHubByName('  science and engineering  ')?.slug).toBe('science-and-engineering');
  });

  it('returns undefined for an unknown or empty faculty', () => {
    expect(facultyHubByName('Faculty of Wizardry')).toBeUndefined();
    expect(facultyHubByName('')).toBeUndefined();
    expect(facultyHubByName(undefined)).toBeUndefined();
  });
});

describe('facultyHubBySlug', () => {
  it('resolves a known slug and rejects an unknown one', () => {
    expect(facultyHubBySlug('humanities')?.name).toBe('Humanities');
    expect(facultyHubBySlug('nope')).toBeUndefined();
  });
});

describe('facultyPagePath', () => {
  it('builds the hub path', () => {
    expect(facultyPagePath('health-sciences')).toBe('/faculty/health-sciences');
  });
});

describe('facultyPageTitle and facultyPageDescription', () => {
  it('names the faculty and the university in the title', () => {
    expect(facultyPageTitle('Science and Engineering')).toBe(
      'Curtin Science and Engineering Units | CurtinHonestly'
    );
  });

  it('states the unit count when there is one', () => {
    expect(facultyPageDescription('Humanities', 501)).toContain('all 501 Humanities units');
  });

  it('drops the count rather than saying zero', () => {
    const description = facultyPageDescription('Humanities', 0);
    expect(description).not.toContain('0');
    expect(description).toContain('Humanities units at Curtin University');
  });

  it('stays inside the description length limit', () => {
    for (const hub of FACULTY_HUBS) {
      expect(facultyPageDescription(hub.name, 572).length).toBeLessThanOrEqual(160);
    }
  });
});

describe('groupUnitsByCodePrefix', () => {
  it('groups by the four-letter subject prefix, sorted within and between groups', () => {
    const groups = groupUnitsByCodePrefix([
      unit('MATH1014', 'Calculus'),
      unit('COMP2006', 'Operating Systems'),
      unit('COMP1000', 'Unix and C Programming'),
    ]);

    expect(groups.map((g) => g.prefix)).toEqual(['COMP', 'MATH']);
    expect(groups[0].units.map((u) => u.code)).toEqual(['COMP1000', 'COMP2006']);
  });

  it('keeps every unit', () => {
    const units = [unit('COMP1000', 'A'), unit('ELEN2000', 'B'), unit('COMP1005', 'C')];
    const grouped = groupUnitsByCodePrefix(units).flatMap((g) => g.units);
    expect(grouped).toHaveLength(units.length);
  });

  it('returns nothing for an empty catalogue', () => {
    expect(groupUnitsByCodePrefix([])).toEqual([]);
  });
});

describe('buildFacultyJsonLd', () => {
  const hub = facultyHubBySlug('science-and-engineering')!;
  const units = [unit('COMP1000', 'Unix and C Programming'), unit('MATH1014', 'Calculus')];
  const graph = buildFacultyJsonLd(hub, units, SITE_URL)['@graph'] as Record<string, unknown>[];
  const collection = graph.find((n) => n['@type'] === 'CollectionPage')!;
  const itemList = collection['mainEntity'] as Record<string, unknown>;

  it('carries the sitewide nodes and a CollectionPage', () => {
    expect(graph.map((n) => n['@type'])).toEqual(
      expect.arrayContaining(['WebSite', 'Organization', 'BreadcrumbList', 'CollectionPage'])
    );
  });

  it('points the CollectionPage at the hub URL', () => {
    expect(collection['url']).toBe('https://www.curtinhonestly.com/faculty/science-and-engineering');
  });

  // The ItemList is a claim about what the page links. If it counts differently
  // from the page it describes the page wrongly, so it is built from the same
  // list the template renders and asserted against it here.
  it('lists every unit on the page, in order, with absolute URLs', () => {
    expect(itemList['numberOfItems']).toBe(2);

    const items = itemList['itemListElement'] as Record<string, unknown>[];
    expect(items).toHaveLength(2);
    expect(items[0]['position']).toBe(1);
    expect(items[0]['url']).toBe('https://www.curtinhonestly.com/units/COMP1000');
    expect(items[1]['position']).toBe(2);
    expect(items[1]['name']).toBe('MATH1014: Calculus');
  });

  it('breadcrumbs home then the hub', () => {
    const trail = graph.find((n) => n['@type'] === 'BreadcrumbList')!['itemListElement'] as Record<
      string,
      unknown
    >[];
    expect(trail.map((i) => i['item'])).toEqual([
      'https://www.curtinhonestly.com/',
      'https://www.curtinhonestly.com/faculty/science-and-engineering',
    ]);
  });
});

describe('buildUnitJsonLd breadcrumb', () => {
  it('routes through the faculty hub', () => {
    const trail = breadcrumbTrail('Science and Engineering');

    expect(trail.map((i) => i['position'])).toEqual([1, 2, 3]);
    expect(trail[1]['name']).toBe('Science and Engineering Units');
    expect(trail[1]['item']).toBe('https://www.curtinhonestly.com/faculty/science-and-engineering');
    expect(trail[2]['item']).toBe('https://www.curtinhonestly.com/units/COMP1000');
  });

  it('falls back to home then unit when the faculty does not resolve', () => {
    const trail = breadcrumbTrail('Faculty of Wizardry');

    expect(trail.map((i) => i['position'])).toEqual([1, 2]);
    expect(trail[1]['item']).toBe('https://www.curtinhonestly.com/units/COMP1000');
  });
});

// Prerequisite options arrive from the handbook import carrying a version
// suffix, and some are not unit codes at all. Both used to be linked straight
// through, producing /units/COMP1002v1 and /units/1922: URLs with no page
// behind them, which served the noindex shell to every reader and crawler that
// followed a prerequisite.
describe('unitCodeForUrl', () => {
  it('strips the version suffix the import adds', () => {
    expect(unitCodeForUrl('COMP1002v1')).toBe('COMP1002');
    expect(unitCodeForUrl('COMP1000V2')).toBe('COMP1000');
  });

  it('passes a bare code through unchanged', () => {
    expect(unitCodeForUrl('COMP1002')).toBe('COMP1002');
  });

  it('normalises case and surrounding space', () => {
    expect(unitCodeForUrl('  comp1002v1  ')).toBe('COMP1002');
  });

  it('rejects legacy numeric course ids, which have no unit page', () => {
    expect(unitCodeForUrl('1922')).toBeUndefined();
  });

  it('rejects empty and malformed input rather than building a dead URL', () => {
    expect(unitCodeForUrl('')).toBeUndefined();
    expect(unitCodeForUrl(undefined)).toBeUndefined();
    expect(unitCodeForUrl('not a code')).toBeUndefined();
    expect(unitCodeForUrl('COMP')).toBeUndefined();
    expect(unitCodeForUrl('COMP10021')).toBeUndefined();
  });

  it('accepts the code shapes the live catalogue actually uses', () => {
    for (const code of ['COMP1000', 'MATH1014', 'INDE1001', 'NPSC1003', 'ISYS2014', 'MXEN2003']) {
      expect(unitCodeForUrl(code)).toBe(code);
    }
  });
});
