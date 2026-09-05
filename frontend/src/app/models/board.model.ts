import { RecognitionTier, ReviewerTier } from './unit.model';

/** Mirrors the backend BoardScope enum. */
export type BoardScope = 'GENERAL' | 'UNIT';

export const BOARD_TITLE_MAX = 140;
export const BOARD_BODY_MAX = 4000;
export const BOARD_REASON_MAX = 300;

/**
 * Everything shown about an author. The pseudonym is a keyed hash of the
 * user id, stable across a user's posts, never their email. Tiers reuse the
 * review tier vocabulary and are null when there is nothing to show.
 */
export interface BoardAuthor {
  pseudonym: string;
  verifiedStudent: boolean;
  tier: ReviewerTier | null;
  tierLabel: string | null;
  recognition: RecognitionTier | null;
  recognitionLabel: string | null;
}

export interface BoardThreadSummary {
  id: string;
  scope: BoardScope;
  unitCode: string | null;
  unitName: string | null;
  title: string;
  excerpt: string;
  author: BoardAuthor;
  replyCount: number;
  pinned: boolean;
  locked: boolean;
  createdAt: string;
  lastActivityAt: string;
}

export interface BoardPost {
  id: string;
  threadId: string;
  body: string;
  // Null on a removed post, which renders as a "[removed]" placeholder.
  author: BoardAuthor | null;
  op: boolean;
  deleted: boolean;
  ownedByCurrentUser: boolean;
  canEdit: boolean;
  createdAt: string;
  editedAt: string | null;
}

export interface BoardThreadDetail {
  id: string;
  scope: BoardScope;
  unitCode: string | null;
  unitName: string | null;
  title: string;
  body: string;
  author: BoardAuthor;
  replyCount: number;
  pinned: boolean;
  locked: boolean;
  ownedByCurrentUser: boolean;
  canEdit: boolean;
  createdAt: string;
  editedAt: string | null;
  lastActivityAt: string;
  posts: BoardPost[];
  postPage: number;
  postTotalPages: number;
  postTotal: number;
}

export interface BoardUnitSummary {
  unitCode: string;
  unitName: string;
  threadCount: number;
  postCount: number;
  latestThreads: BoardThreadSummary[];
}

/** The subset of Spring's Page JSON the board pages read. */
export interface BoardPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export type BoardThreadSort = 'activity' | 'newest';
