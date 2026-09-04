import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { AuthService } from '../../../services/auth.service';
import { BoardService } from '../../../services/board.service';
import { SeoService } from '../../../services/seo.service';
import {
  BOARD_BODY_MAX,
  BOARD_REASON_MAX,
  BOARD_TITLE_MAX,
  BoardPost,
  BoardThreadDetail
} from '../../../models/board.model';
import { IconComponent } from '../../icon/icon.component';
import { BoardAuthorComponent } from '../board-author/board-author.component';
import { apiErrorMessage, fullDate, timeAgo } from '../board-time.util';

const POST_PAGE_SIZE = 100;

type ReportTarget = { kind: 'thread' | 'post'; id: string };

/**
 * /boards/threads/:id: the opening post, replies, and the reply composer.
 * Owners get Edit (15 minutes) and Delete on their own content; every
 * signed-in student can report a thread or a reply.
 */
@Component({
  selector: 'app-thread-view',
  standalone: true,
  imports: [NgTemplateOutlet, FormsModule, RouterLink, IconComponent, BoardAuthorComponent],
  templateUrl: './thread-view.component.html',
  styleUrls: ['../boards.css', './thread-view.component.css']
})
export class ThreadViewComponent implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private boardService = inject(BoardService);
  private seoService = inject(SeoService);
  authService = inject(AuthService);
  private destroy$ = new Subject<void>();

  readonly titleMax = BOARD_TITLE_MAX;
  readonly bodyMax = BOARD_BODY_MAX;
  readonly reasonMax = BOARD_REASON_MAX;
  readonly timeAgo = timeAgo;
  readonly fullDate = fullDate;

  threadId = signal('');
  thread = signal<BoardThreadDetail | null>(null);
  loading = signal(true);
  notFound = signal(false);
  errorMessage = signal<string | null>(null);

  // Reply composer
  replyBody = signal('');
  replySubmitting = signal(false);
  replyError = signal<string | null>(null);

  // Editing the opening post
  editingThread = signal(false);
  editTitle = signal('');
  editBody = signal('');
  editSubmitting = signal(false);
  editError = signal<string | null>(null);

  // Editing one reply
  editingPostId = signal<string | null>(null);
  editPostBody = signal('');

  // Reporting
  reportTarget = signal<ReportTarget | null>(null);
  reportReason = signal('');
  reportSubmitting = signal(false);
  reportedIds = signal<Set<string>>(new Set());

  actionMessage = signal<string | null>(null);
  actionError = signal<string | null>(null);

  boardLink = computed<string[]>(() => {
    const thread = this.thread();
    return thread?.scope === 'UNIT' && thread.unitCode ? ['/boards/units', thread.unitCode] : ['/boards'];
  });

  boardLabel = computed(() => {
    const thread = this.thread();
    return thread?.scope === 'UNIT' && thread.unitCode ? `${thread.unitCode} discussion` : 'General discussion';
  });

  canReply = computed(() => {
    const body = this.replyBody().trim();
    return body.length > 0 && this.replyBody().length <= this.bodyMax && !this.replySubmitting();
  });

  canSaveThread = computed(() => {
    const title = this.editTitle().trim();
    const body = this.editBody().trim();
    return title.length > 0 && body.length > 0
      && this.editTitle().length <= this.titleMax && this.editBody().length <= this.bodyMax
      && !this.editSubmitting();
  });

  canSavePost = computed(() => {
    const body = this.editPostBody().trim();
    return body.length > 0 && this.editPostBody().length <= this.bodyMax && !this.editSubmitting();
  });

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntil(this.destroy$)).subscribe((params) => {
      const id = params.get('id') ?? '';
      this.threadId.set(id);
      this.seoService.noIndex('Discussion | CurtinHonestly');
      this.resetTransientState();
      this.load(0);
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  load(page: number): void {
    const id = this.threadId();
    if (!id) {
      this.notFound.set(true);
      this.loading.set(false);
      return;
    }
    this.loading.set(true);
    this.notFound.set(false);
    this.errorMessage.set(null);
    this.boardService.getThread(id, page, POST_PAGE_SIZE).subscribe({
      next: (thread) => {
        this.thread.set(thread);
        this.seoService.noIndex(`${thread.title} | CurtinHonestly`);
        this.loading.set(false);
      },
      error: (err) => {
        if (err?.status === 404) {
          this.notFound.set(true);
        } else {
          this.errorMessage.set('This thread could not be loaded. Please try again shortly.');
        }
        this.loading.set(false);
      }
    });
  }

  previousPostPage(): void {
    const thread = this.thread();
    if (thread && thread.postPage > 0) {
      this.load(thread.postPage - 1);
    }
  }

  nextPostPage(): void {
    const thread = this.thread();
    if (thread && thread.postPage + 1 < thread.postTotalPages) {
      this.load(thread.postPage + 1);
    }
  }

  // Replies

  submitReply(): void {
    const thread = this.thread();
    if (!thread || !this.canReply()) {
      return;
    }
    this.replySubmitting.set(true);
    this.replyError.set(null);
    this.boardService.createPost(thread.id, this.replyBody().trim()).subscribe({
      next: () => {
        this.replySubmitting.set(false);
        this.replyBody.set('');
        // Reload the last page so the new reply is visible wherever it landed.
        const lastPage = Math.max(0, Math.ceil((thread.postTotal + 1) / POST_PAGE_SIZE) - 1);
        this.load(lastPage);
      },
      error: (err) => {
        this.replySubmitting.set(false);
        this.replyError.set(apiErrorMessage(err, 'Your reply could not be posted. Please try again.'));
      }
    });
  }

  // Editing the thread

  startEditThread(): void {
    const thread = this.thread();
    if (!thread) {
      return;
    }
    this.clearMessages();
    this.editingPostId.set(null);
    this.editTitle.set(thread.title);
    this.editBody.set(thread.body);
    this.editError.set(null);
    this.editingThread.set(true);
  }

  cancelEditThread(): void {
    this.editingThread.set(false);
    this.editError.set(null);
  }

  saveThread(): void {
    const thread = this.thread();
    if (!thread || !this.canSaveThread()) {
      return;
    }
    this.editSubmitting.set(true);
    this.editError.set(null);
    this.boardService.updateThread(thread.id, this.editTitle().trim(), this.editBody().trim()).subscribe({
      next: (updated) => {
        this.editSubmitting.set(false);
        this.editingThread.set(false);
        this.thread.set(updated);
        this.actionMessage.set('Thread updated.');
      },
      error: (err) => {
        this.editSubmitting.set(false);
        this.editError.set(apiErrorMessage(err, 'The thread could not be updated.'));
      }
    });
  }

  deleteThread(): void {
    const thread = this.thread();
    if (!thread) {
      return;
    }
    if (!confirm('Delete this thread? Replies stay visible but the thread leaves every list.')) {
      return;
    }
    this.clearMessages();
    this.boardService.deleteThread(thread.id).subscribe({
      next: () => this.router.navigate(this.boardLink()),
      error: (err) => this.actionError.set(apiErrorMessage(err, 'The thread could not be deleted.'))
    });
  }

  // Editing a reply

  startEditPost(post: BoardPost): void {
    this.clearMessages();
    this.editingThread.set(false);
    this.editPostBody.set(post.body);
    this.editError.set(null);
    this.editingPostId.set(post.id);
  }

  cancelEditPost(): void {
    this.editingPostId.set(null);
    this.editError.set(null);
  }

  savePost(post: BoardPost): void {
    if (!this.canSavePost()) {
      return;
    }
    this.editSubmitting.set(true);
    this.editError.set(null);
    this.boardService.updatePost(post.id, this.editPostBody().trim()).subscribe({
      next: (updated) => {
        this.editSubmitting.set(false);
        this.editingPostId.set(null);
        this.replacePost(updated);
        this.actionMessage.set('Reply updated.');
      },
      error: (err) => {
        this.editSubmitting.set(false);
        this.editError.set(apiErrorMessage(err, 'The reply could not be updated.'));
      }
    });
  }

  deletePost(post: BoardPost): void {
    if (!confirm('Delete this reply? It will show as removed in the thread.')) {
      return;
    }
    this.clearMessages();
    this.boardService.deletePost(post.id).subscribe({
      next: () => {
        const thread = this.thread();
        if (thread) {
          this.load(thread.postPage);
        }
        this.actionMessage.set('Reply removed.');
      },
      error: (err) => this.actionError.set(apiErrorMessage(err, 'The reply could not be deleted.'))
    });
  }

  // Reporting

  openReport(kind: 'thread' | 'post', id: string): void {
    this.clearMessages();
    this.reportReason.set('');
    this.reportTarget.set({ kind, id });
  }

  cancelReport(): void {
    this.reportTarget.set(null);
    this.reportReason.set('');
  }

  isReporting(kind: 'thread' | 'post', id: string): boolean {
    const target = this.reportTarget();
    return !!target && target.kind === kind && target.id === id;
  }

  isReported(id: string): boolean {
    return this.reportedIds().has(id);
  }

  submitReport(): void {
    const target = this.reportTarget();
    if (!target || this.reportSubmitting()) {
      return;
    }
    this.reportSubmitting.set(true);
    const reason = this.reportReason().trim() || null;
    const request$ = target.kind === 'thread'
      ? this.boardService.flagThread(target.id, reason)
      : this.boardService.flagPost(target.id, reason);
    request$.subscribe({
      next: () => {
        this.reportSubmitting.set(false);
        this.reportedIds.update((ids) => new Set(ids).add(target.id));
        this.reportTarget.set(null);
        this.reportReason.set('');
        this.actionMessage.set('Thanks, a moderator will take a look.');
      },
      error: (err) => {
        this.reportSubmitting.set(false);
        this.actionError.set(apiErrorMessage(err, 'The report could not be sent. Please try again.'));
      }
    });
  }

  replyLabel(count: number): string {
    return count === 1 ? 'reply' : 'replies';
  }

  private replacePost(updated: BoardPost): void {
    this.thread.update((thread) => thread
      ? { ...thread, posts: thread.posts.map((p) => (p.id === updated.id ? updated : p)) }
      : thread);
  }

  private resetTransientState(): void {
    this.editingThread.set(false);
    this.editingPostId.set(null);
    this.reportTarget.set(null);
    this.replyBody.set('');
    this.clearMessages();
  }

  private clearMessages(): void {
    this.actionMessage.set(null);
    this.actionError.set(null);
  }
}
