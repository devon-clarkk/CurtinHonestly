// Clubs, their members and the events they publish. Managed at /admin/clubs
// and /admin/club-events; the public site reads /events and /clubs.

export type ClubEventStatus = 'DRAFT' | 'PENDING' | 'PUBLISHED' | 'REJECTED' | 'CANCELLED';

export interface ClubOption {
  value: string;
  label: string;
}

export interface ClubEventOptions {
  kinds: ClubOption[];
  statuses: ClubOption[];
  faculties: ClubOption[];
  levels: ClubOption[];
}

export interface AdminClubMember {
  userId: string;
  email: string;
  role: string;
  createdAt: string;
}

export interface AdminClub {
  id: string;
  name: string;
  slug: string;
  description: string | null;
  websiteUrl: string | null;
  logoUrl: string | null;
  contactEmail: string | null;
  /** Trusted clubs publish immediately; others queue for approval. */
  trusted: boolean;
  /** Inactive clubs and their events are hidden from the public site. */
  active: boolean;
  createdAt: string;
  eventCount: number;
  pendingCount: number;
  members: AdminClubMember[];
}

export interface AdminClubUpsert {
  name: string;
  slug: string | null;
  description: string | null;
  websiteUrl: string | null;
  logoUrl: string | null;
  contactEmail: string | null;
  trusted: boolean;
  active: boolean;
}

export interface AdminClubMemberRequest {
  email?: string;
  role: string;
}

export interface ClubEventManage {
  id: string;
  clubId: string;
  clubName: string;
  clubSlug: string;
  clubTrusted: boolean;
  title: string;
  description: string | null;
  kind: string;
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
  kind: string;
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

export interface ClubEventPreview {
  matchCount: number;
  sampleCodes: string[];
  scopeLabel: string;
}
