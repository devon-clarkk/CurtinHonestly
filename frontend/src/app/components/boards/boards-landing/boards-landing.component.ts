import { Component, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../services/auth.service';
import { BoardService } from '../../../services/board.service';
import { SeoService } from '../../../services/seo.service';
import { BoardThreadDetail, BoardThreadSort, BoardThreadSummary } from '../../../models/board.model';
import { BoardAuthorComponent } from '../board-author/board-author.component';
import { ThreadComposerComponent } from '../thread-composer/thread-composer.component';
import { fullDate, timeAgo } from '../board-time.util';

const PAGE_SIZE = 20;
const RECENT_LIMIT = 10;

/**
 * /boards: the general discussion list on the left, recently active unit
 * boards on the right. Client-rendered and noindex for now.
 */
@Component({
  selector: 'app-boards-landing',
  standalone: true,
  imports: [RouterLink, BoardAuthorComponent, ThreadComposerComponent],
  templateUrl: './boards-landing.component.html',
  styleUrls: ['../boards.css', './boards-landing.component.css']
})
export class BoardsLandingComponent implements OnInit {
  private boardService = inject(BoardService);
  private seoService = inject(SeoService);
  private router = inject(Router);
  authService = inject(AuthService);

  threads = signal<BoardThreadSummary[]>([]);
  page = signal(0);
  totalPages = signal(0);
  totalThreads = signal(0);
  sort = signal<BoardThreadSort>('activity');
  loading = signal(true);
  errorMessage = signal<string | null>(null);

  recent = signal<BoardThreadSummary[]>([]);
  recentLoading = signal(true);

  composing = signal(false);

  readonly timeAgo = timeAgo;
  readonly fullDate = fullDate;

  ngOnInit(): void {
    this.seoService.noIndex('Boards | CurtinHonestly');
    this.load(0);
    this.loadRecent();
  }

  load(page: number): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.boardService.getGeneralThreads(page, PAGE_SIZE, this.sort()).subscribe({
      next: (result) => {
        this.threads.set(result.content);
        this.page.set(result.number);
        this.totalPages.set(result.totalPages);
        this.totalThreads.set(result.totalElements);
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('The board could not be loaded. Please try again shortly.');
        this.loading.set(false);
      }
    });
  }

  loadRecent(): void {
    this.recentLoading.set(true);
    this.boardService.getRecent(RECENT_LIMIT).subscribe({
      next: (threads) => {
        this.recent.set(threads);
        this.recentLoading.set(false);
      },
      error: () => this.recentLoading.set(false)
    });
  }

  setSort(sort: BoardThreadSort): void {
    if (this.sort() === sort) {
      return;
    }
    this.sort.set(sort);
    this.load(0);
  }

  previousPage(): void {
    if (this.page() > 0) {
      this.load(this.page() - 1);
    }
  }

  nextPage(): void {
    if (this.page() + 1 < this.totalPages()) {
      this.load(this.page() + 1);
    }
  }

  toggleComposer(): void {
    this.composing.update((open) => !open);
  }

  onCreated(thread: BoardThreadDetail): void {
    this.composing.set(false);
    this.router.navigate(['/boards/threads', thread.id]);
  }

  replyLabel(count: number): string {
    return count === 1 ? 'reply' : 'replies';
  }
}
