import { Component, HostListener, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { BoardAdminService } from '../../services/board-admin.service';
import { BoardAdminFlaggedItem, BoardAdminPost, BoardAdminThread } from '../../models/board-admin.model';

const PAGE_SIZE = 20;
const EXCERPT_LENGTH = 110;

/** What the detail dialog shows: one thread or one post, with the flag context when opened from the queue. */
interface Detail {
  kind: 'thread' | 'post';
  id: string;
  threadId: string;
  heading: string;
  subheading: string;
  body: string;
  facts: { label: string; value: string }[];
  thread: BoardAdminThread | null;
  flag: BoardAdminFlaggedItem | null;
  flagCount: number;
}

@Component({
  selector: 'app-boards',
  imports: [DatePipe],
  templateUrl: './boards.component.html',
  styleUrl: './boards.component.css'
})
export class BoardsComponent implements OnInit {
  private boardAdminService = inject(BoardAdminService);

  flagged = signal<BoardAdminFlaggedItem[]>([]);

  threads = signal<BoardAdminThread[]>([]);
  threadPage = signal(0);
  threadTotalPages = signal(0);
  threadTotal = signal(0);

  posts = signal<BoardAdminPost[]>([]);
  postPage = signal(0);
  postTotalPages = signal(0);
  postTotal = signal(0);

  selected = signal<Detail | null>(null);
  errorMessage = signal<string | null>(null);
  successMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.refreshFlagged();
    this.refreshThreads();
    this.refreshPosts();
  }

  // Loading

  refreshFlagged(): void {
    this.boardAdminService.listFlagged().subscribe({
      next: (items) => this.flagged.set(items),
      error: () => this.errorMessage.set('Failed to load flagged board content.')
    });
  }

  refreshThreads(page = this.threadPage()): void {
    this.boardAdminService.listThreads(page, PAGE_SIZE).subscribe({
      next: (result) => {
        this.threads.set(result.content);
        this.threadPage.set(result.number);
        this.threadTotalPages.set(result.totalPages);
        this.threadTotal.set(result.totalElements);
      },
      error: () => this.errorMessage.set('Failed to load board threads.')
    });
  }

  refreshPosts(page = this.postPage()): void {
    this.boardAdminService.listPosts(page, PAGE_SIZE).subscribe({
      next: (result) => {
        this.posts.set(result.content);
        this.postPage.set(result.number);
        this.postTotalPages.set(result.totalPages);
        this.postTotal.set(result.totalElements);
      },
      error: () => this.errorMessage.set('Failed to load board posts.')
    });
  }

  previousThreadPage(): void {
    if (this.threadPage() > 0) this.refreshThreads(this.threadPage() - 1);
  }

  nextThreadPage(): void {
    if (this.threadPage() + 1 < this.threadTotalPages()) this.refreshThreads(this.threadPage() + 1);
  }

  previousPostPage(): void {
    if (this.postPage() > 0) this.refreshPosts(this.postPage() - 1);
  }

  nextPostPage(): void {
    if (this.postPage() + 1 < this.postTotalPages()) this.refreshPosts(this.postPage() + 1);
  }

  // Thread moderation

  togglePinned(thread: BoardAdminThread): void {
    this.clearMessages();
    const action = thread.pinned
      ? this.boardAdminService.unpinThread(thread.id)
      : this.boardAdminService.pinThread(thread.id);
    action.subscribe({
      next: (updated) => {
        this.successMessage.set(updated.pinned ? 'Thread pinned.' : 'Thread unpinned.');
        this.replaceThread(updated);
      },
      error: () => this.errorMessage.set('Failed to update the pin.')
    });
  }

  toggleLocked(thread: BoardAdminThread): void {
    this.clearMessages();
    const action = thread.locked
      ? this.boardAdminService.unlockThread(thread.id)
      : this.boardAdminService.lockThread(thread.id);
    action.subscribe({
      next: (updated) => {
        this.successMessage.set(updated.locked ? 'Thread locked. New replies are closed.' : 'Thread unlocked.');
        this.replaceThread(updated);
      },
      error: () => this.errorMessage.set('Failed to update the lock.')
    });
  }

  removeThread(id: string, title: string): void {
    if (!confirm(`Remove the thread "${title}"? It leaves every list and its flags are cleared.`)) return;
    this.clearMessages();
    this.boardAdminService.removeThread(id).subscribe({
      next: () => {
        this.successMessage.set('Thread removed.');
        if (this.selected()?.kind === 'thread' && this.selected()?.id === id) this.closeDetail();
        this.refreshAll();
      },
      error: () => this.errorMessage.set('Failed to remove the thread.')
    });
  }

  removePost(id: string): void {
    if (!confirm('Remove this reply? It shows as removed in the thread and its flags are cleared.')) return;
    this.clearMessages();
    this.boardAdminService.removePost(id).subscribe({
      next: () => {
        this.successMessage.set('Reply removed.');
        if (this.selected()?.kind === 'post' && this.selected()?.id === id) this.closeDetail();
        this.refreshAll();
      },
      error: () => this.errorMessage.set('Failed to remove the reply.')
    });
  }

  // Flags

  dismissFlags(item: BoardAdminFlaggedItem): void {
    this.clearMessages();
    const action = item.targetType === 'THREAD'
      ? this.boardAdminService.dismissThreadFlags(item.targetId)
      : this.boardAdminService.dismissPostFlags(item.targetId);
    action.subscribe({
      next: () => {
        this.successMessage.set('Flags dismissed, content kept.');
        if (this.selected()?.id === item.targetId) this.closeDetail();
        this.refreshAll();
      },
      error: () => this.errorMessage.set('Failed to dismiss flags.')
    });
  }

  removeFlagged(item: BoardAdminFlaggedItem): void {
    if (item.targetType === 'THREAD') {
      this.removeThread(item.targetId, item.threadTitle);
    } else {
      this.removePost(item.targetId);
    }
  }

  // Detail dialog

  openThread(thread: BoardAdminThread): void {
    this.selected.set({
      kind: 'thread',
      id: thread.id,
      threadId: thread.id,
      heading: thread.title,
      subheading: this.boardLabel(thread.scope, thread.unitCode, thread.unitName),
      body: thread.body,
      facts: [
        { label: 'Author', value: this.authorLine(thread.authorPseudonym, thread.authorEmail) },
        { label: 'Verified', value: thread.authorEmail ? (thread.authorVerified ? 'Yes' : 'No') : 'Account removed' },
        { label: 'Replies', value: String(thread.replyCount) },
        { label: 'Flags', value: String(thread.flagCount) },
        { label: 'Status', value: this.statusLine(thread) },
        { label: 'Posted', value: this.dateLine(thread.createdAt) },
        { label: 'Last activity', value: this.dateLine(thread.lastActivityAt) },
        { label: 'Thread ID', value: thread.id }
      ],
      thread,
      flag: null,
      flagCount: thread.flagCount
    });
  }

  openPost(post: BoardAdminPost): void {
    this.selected.set({
      kind: 'post',
      id: post.id,
      threadId: post.threadId,
      heading: `Reply in "${post.threadTitle}"`,
      subheading: post.unitCode ? `${post.unitCode} board` : 'General board',
      body: post.body,
      facts: [
        { label: 'Author', value: this.authorLine(post.authorPseudonym, post.authorEmail) },
        { label: 'Verified', value: post.authorEmail ? (post.authorVerified ? 'Yes' : 'No') : 'Account removed' },
        { label: 'Flags', value: String(post.flagCount) },
        { label: 'Posted', value: this.dateLine(post.createdAt) },
        { label: 'Edited', value: post.editedAt ? this.dateLine(post.editedAt) : 'No' },
        { label: 'Post ID', value: post.id }
      ],
      thread: null,
      flag: null,
      flagCount: post.flagCount
    });
  }

  openFlagged(item: BoardAdminFlaggedItem): void {
    const thread = item.targetType === 'THREAD' ? this.threads().find((t) => t.id === item.targetId) ?? null : null;
    this.selected.set({
      kind: item.targetType === 'THREAD' ? 'thread' : 'post',
      id: item.targetId,
      threadId: item.threadId,
      heading: item.targetType === 'THREAD' ? item.threadTitle : `Reply in "${item.threadTitle}"`,
      subheading: item.unitCode ? `${item.unitCode} board` : 'General board',
      body: item.body,
      facts: [
        { label: 'Author', value: this.authorLine(item.authorPseudonym, item.authorEmail) },
        { label: 'Flags', value: String(item.flagCount) },
        { label: 'Latest flag', value: item.latestFlagAt ? this.dateLine(item.latestFlagAt) : 'Unknown' },
        { label: 'Posted', value: this.dateLine(item.createdAt) },
        { label: 'ID', value: item.targetId }
      ],
      thread,
      flag: item,
      flagCount: item.flagCount
    });
  }

  closeDetail(): void {
    this.selected.set(null);
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.selected()) this.closeDetail();
  }

  // Display helpers

  excerpt(text: string | null): string {
    if (!text) return '';
    const collapsed = text.replace(/\s+/g, ' ').trim();
    return collapsed.length > EXCERPT_LENGTH ? `${collapsed.slice(0, EXCERPT_LENGTH - 1).trim()}...` : collapsed;
  }

  boardLabel(scope: string, unitCode: string | null, unitName: string | null): string {
    if (scope === 'UNIT' && unitCode) {
      return unitName ? `${unitCode} ${unitName}` : `${unitCode} board`;
    }
    return 'General board';
  }

  threadUrl(threadId: string): string {
    return `https://www.curtinhonestly.com/boards/threads/${encodeURIComponent(threadId)}`;
  }

  private authorLine(pseudonym: string, email: string | null): string {
    return email ? `${pseudonym} (${email})` : `${pseudonym} (account removed)`;
  }

  private statusLine(thread: BoardAdminThread): string {
    const parts: string[] = [];
    if (thread.pinned) parts.push('Pinned');
    if (thread.locked) parts.push('Locked');
    return parts.length ? parts.join(', ') : 'Open';
  }

  private dateLine(iso: string): string {
    const date = new Date(iso);
    return Number.isNaN(date.getTime()) ? iso : date.toLocaleString('en-AU', { dateStyle: 'medium', timeStyle: 'short' });
  }

  private replaceThread(updated: BoardAdminThread): void {
    this.threads.update((threads) => threads.map((t) => (t.id === updated.id ? updated : t)));
    const detail = this.selected();
    if (detail?.kind === 'thread' && detail.id === updated.id) {
      this.openThread(updated);
    }
  }

  private refreshAll(): void {
    this.refreshFlagged();
    this.refreshThreads();
    this.refreshPosts();
  }

  private clearMessages(): void {
    this.errorMessage.set(null);
    this.successMessage.set(null);
  }
}
