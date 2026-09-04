/**
 * Curated links attached to a unit (Discord servers, clubs, textbooks, past
 * papers and so on). Served by GET /units/{code}/resources.
 */

export type ResourceKind =
  | 'DISCORD'
  | 'CLUB'
  | 'STUDY_GROUP'
  | 'NOTES'
  | 'PAST_PAPERS'
  | 'TEXTBOOK'
  | 'VIDEO'
  | 'WEBSITE'
  | 'OTHER';

/** Display order of the kind groups on the unit page; mirrors the backend enum order. */
export const RESOURCE_KIND_ORDER: ResourceKind[] = [
  'DISCORD',
  'CLUB',
  'STUDY_GROUP',
  'NOTES',
  'PAST_PAPERS',
  'TEXTBOOK',
  'VIDEO',
  'WEBSITE',
  'OTHER'
];

export const RESOURCE_KIND_LABELS: Record<ResourceKind, string> = {
  DISCORD: 'Discord server',
  CLUB: 'Club or society',
  STUDY_GROUP: 'Study group',
  NOTES: 'Notes',
  PAST_PAPERS: 'Past papers',
  TEXTBOOK: 'Textbook',
  VIDEO: 'Video',
  WEBSITE: 'Website',
  OTHER: 'Other'
};

export interface UnitResource {
  id: string;
  title: string;
  url: string;
  description: string | null;
  kind: ResourceKind;
  kindLabel: string;
  /** Why the link is on this page: "This unit", "All COMP units", "Science and Engineering", "All units". */
  scopeLabel: string;
}

export interface UnitResourceList {
  items: UnitResource[];
}

export interface UnitResourceSuggestion {
  title: string;
  url: string;
  kind: ResourceKind;
  description?: string;
  note?: string;
}
