/**
 * Clubs and the study sessions and events they publish. Served by the public
 * /events and /clubs endpoints, GET /units/{code}/events for the unit page,
 * and the /club/** portal for signed-in club members.
 */

export type ClubEventKind =
  | 'REVISION_SESSION'
  | 'TUTORING'
  | 'WORKSHOP'
  | 'INFO_SESSION'
  | 'SOCIAL'
  | 'OTHER';

/** Display order of kinds in filters and selects; mirrors the backend enum order. */
export const CLUB_EVENT_KINDS: ClubEventKind[] = [
  'REVISION_SESSION',
  'TUTORING',
  'WORKSHOP',
  'INFO_SESSION',
  'SOCIAL',
  'OTHER'
];

export const CLUB_EVENT_KIND_LABELS: Record<ClubEventKind, string> = {
  REVISION_SESSION: 'Revision session',
  TUTORING: 'Tutoring',
  WORKSHOP: 'Workshop',
  INFO_SESSION: 'Info session',
  SOCIAL: 'Social',
  OTHER: 'Other'
};

export type ClubEventStatus = 'DRAFT' | 'PENDING' | 'PUBLISHED' | 'REJECTED' | 'CANCELLED';

export const CLUB_EVENT_STATUS_LABELS: Record<ClubEventStatus, string> = {
  DRAFT: 'Draft',
  PENDING: 'Pending approval',
  PUBLISHED: 'Published',
  REJECTED: 'Rejected',
  CANCELLED: 'Cancelled'
};

export const CLUB_EVENT_LIMITS = {
  title: 140,
  description: 2000,
  location: 200,
  recurrenceNote: 120,
  clubDescription: 600
} as const;

/** One published event as the public site shows it. Times are UTC ISO strings. */
export interface ClubEvent {
  id: string;
  clubId: string;
  clubName: string;
  clubSlug: string;
  title: string;
  description: string | null;
  kind: ClubEventKind;
  kindLabel: string;
  startsAt: string;
  endsAt: string | null;
  /** The start to display: the event's own, or the next weekly projection of a recurring one. */
  nextStartsAt: string;
  location: string | null;
  online: boolean;
  link: string | null;
  recurring: boolean;
  recurrenceNote: string | null;
  /** Which unit pages carry it: "This unit", "All COMP1 and ISAD1 units", "All units". */
  scopeLabel: string;
  targetUnitCode: string | null;
  targetUnitName: string | null;
  showOnHome: boolean;
  viewCount: number;
}

/** An event as its club (or an admin) manages it: every field plus status and audit columns. */
export interface ClubEventManage {
  id: string;
  clubId: string;
  clubName: string;
  clubSlug: string;
  clubTrusted: boolean;
  title: string;
  description: string | null;
  kind: ClubEventKind;
  kindLabel: string;
  startsAt: string;
  endsAt: string | null;
  location: string | null;
  online: boolean;
  link: string | null;
  recurring: boolean;
  recurrenceNote: string | null;
  targetUnitCode: string | null;
  targetUnitName: string | null;
  codePrefixes: string | null;
  faculty: string | null;
  level: string | null;
  scopeLabel: string;
  showOnHome: boolean;
  status: ClubEventStatus;
  statusLabel: string;
  rejectionReason: string | null;
  createdByEmail: string | null;
  createdAt: string;
  updatedAt: string;
  publishedAt: string | null;
  viewCount: number;
}

/**
 * Create or edit payload. A non-empty unitCode targets that one unit and the
 * rule fields are ignored; otherwise the row is a rule built from the prefix
 * list, faculty and level (all optional; none set means every unit page).
 */
export interface ClubEventUpsert {
  title: string;
  description: string | null;
  kind: ClubEventKind;
  startsAt: string;
  endsAt: string | null;
  location: string | null;
  online: boolean;
  link: string | null;
  recurring: boolean;
  recurrenceNote: string | null;
  unitCode: string | null;
  codePrefixes: string | null;
  faculty: string | null;
  level: string | null;
  showOnHome: boolean;
}

export interface ClubSummary {
  id: string;
  name: string;
  slug: string;
  description: string | null;
  websiteUrl: string | null;
  logoUrl: string | null;
  upcomingEventCount: number;
}

export interface ClubProfile {
  id: string;
  name: string;
  slug: string;
  description: string | null;
  websiteUrl: string | null;
  logoUrl: string | null;
  contactEmail: string | null;
  upcomingEvents: ClubEvent[];
}

/** A club as one of its members sees it in the portal, with the caller's role ("OWNER", "EDITOR" or "ADMIN"). */
export interface ClubPortalClub {
  id: string;
  name: string;
  slug: string;
  description: string | null;
  websiteUrl: string | null;
  logoUrl: string | null;
  contactEmail: string | null;
  trusted: boolean;
  active: boolean;
  role: string;
}

export interface ClubProfileUpdate {
  description: string | null;
  websiteUrl: string | null;
  logoUrl: string | null;
  contactEmail: string | null;
}

export interface ClubEventOption {
  value: string;
  label: string;
}

export interface ClubEventOptions {
  kinds: ClubEventOption[];
  statuses: ClubEventOption[];
  faculties: ClubEventOption[];
  levels: ClubEventOption[];
}

export interface ClubEventPreview {
  matchCount: number;
  sampleCodes: string[];
  scopeLabel: string;
}

/** The subset of Spring's Page JSON the events page reads. */
export interface ClubEventPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
