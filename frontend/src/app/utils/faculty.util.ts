import facultiesJson from '../faculties.json';

/**
 * The five faculty hub pages, in the order they are offered to a reader.
 *
 * One list, three consumers that must not drift: the router prerenders a page
 * per slug, generate-seo-assets.js puts the same slugs in the sitemap, and unit
 * pages link back to their own faculty. The build script is CommonJS and cannot
 * import a TypeScript enum, which is why this is JSON rather than a const.
 *
 * `faculty` is the enum name the API filters on. `name` is the display string
 * the API returns on a unit, which is what a unit page has to match on to find
 * its hub. They are different spellings of the same thing and both are needed.
 */
export interface FacultyHub {
  slug: string;
  faculty: string;
  name: string;
}

export const FACULTY_HUBS: FacultyHub[] = facultiesJson;

export function facultyHubBySlug(slug: string): FacultyHub | undefined {
  return FACULTY_HUBS.find((hub) => hub.slug === slug);
}

/** Resolves the display name the API puts on a unit back to its hub. */
export function facultyHubByName(name: string | undefined): FacultyHub | undefined {
  const trimmed = name?.trim().toLowerCase();
  if (!trimmed) {
    return undefined;
  }
  return FACULTY_HUBS.find((hub) => hub.name.toLowerCase() === trimmed);
}

export function facultyPagePath(slug: string): string {
  return `/faculty/${slug}`;
}
