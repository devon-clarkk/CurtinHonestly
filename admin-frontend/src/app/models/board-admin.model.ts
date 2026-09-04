export type BoardScope = 'GENERAL' | 'UNIT';

export interface BoardAdminThread {
  id: string;
  scope: BoardScope;
  unitCode: string | null;
  unitName: string | null;
  title: string;
  body: string;
  authorPseudonym: string;
  authorEmail: string | null;
  authorVerified: boolean;
  replyCount: number;
  pinned: boolean;
  locked: boolean;
  flagCount: number;
  createdAt: string;
  editedAt: string | null;
  lastActivityAt: string;
  deletedAt: string | null;
}

export interface BoardAdminPost {
  id: string;
  threadId: string;
  threadTitle: string;
  unitCode: string | null;
  body: string;
  authorPseudonym: string;
  authorEmail: string | null;
  authorVerified: boolean;
  flagCount: number;
  createdAt: string;
  editedAt: string | null;
  deletedAt: string | null;
}

export type BoardFlagTargetType = 'THREAD' | 'POST';

export interface BoardAdminFlaggedItem {
  targetType: BoardFlagTargetType;
  targetId: string;
  threadId: string;
  threadTitle: string;
  unitCode: string | null;
  body: string;
  authorPseudonym: string;
  authorEmail: string | null;
  flagCount: number;
  reasons: string[];
  latestFlagAt: string | null;
  createdAt: string;
}

export interface BoardAdminPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
}
