import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { AuthService } from '../../../services/auth.service';
import { BoardService } from '../../../services/board.service';
import { SeoService } from '../../../services/seo.service';
import { BoardThreadDetail, BoardThreadSort, BoardThreadSummary, BoardUnitSummary } from '../../../models/board.model';
import { IconComponent } from '../../icon/icon.component';
import { BoardAuthorComponent } from '../board-author/board-author.component';
import { ThreadComposerComponent } from '../thread-composer/thread-composer.component';
import { fullDate, timeAgo } from '../board-time.util';

const PAGE_SIZE = 20;

/** /boards/units/:code: one unit's board. `?compose=1` opens the composer straight away. */
@Component({
  selector: 'app-unit-board',
  standalone: true,
  imports: [RouterLink, IconComponent, BoardAuthorComponent, ThreadComposerComponent],
  templateUrl: './unit-board.component.html',
  styleUrls: ['../boards.css', './unit-board.component.css']
})
export class UnitBoardComponent implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private boardService = inject(BoardService);
  private seoService = inject(SeoService);
  authService = inject(AuthService);
  private destroy$ = new Subject<void>();

  unitCode = signal('');
  summary = signal<BoardUnitSummary | null>(null);
  notFound = signal(false);

  threads = signal<BoardThreadSummary[]>([]);
  page = signal(0);
  totalPages = signal(0);
  totalThreads = signal(0);
  sort = signal<BoardThreadSort>('activity');
  loading = signal(true);
  errorMessage = signal<string | null>(null);

  composing = signal(false);

  readonly timeAgo = timeAgo;
  readonly fullDate = fullDate;

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntil(this.destroy$)).subscribe((params) => {
      const code = (params.get('code') ?? '').toUpperCase();
      this.unitCode.set(code);
      this.seoService.noIndex(`${code} discussion | CurtinHonestly`);
      this.composing.set(this.route.snapshot.queryParamMap.get('compose') === '1');
      this.loadSummary(code);
      this.load(0);
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private loadSummary(code: string): void {
    this.notFound.set(false);
    this.boardService.getUnitSummary(code).subscribe({
      next: (summary) => {
        this.summary.set(summary);
        this.seoService.noIndex(`${summary.unitCode} ${summary.unitName} discussion | CurtinHonestly`);
      },
      error: (err) => {
        if (err?.status === 404) {
          this.notFound.set(true);
          this.loading.set(false);
        }
      }
    });
  }

  load(page: number): void {
    const code = this.unitCode();
    if (!code) {
      return;
    }
    this.loading.set(true);
    this.errorMessage.set(null);
    this.boardService.getUnitThreads(code, page, PAGE_SIZE, this.sort()).subscribe({
      next: (result) => {
        this.threads.set(result.content);
        this.page.set(result.number);
        this.totalPages.set(result.totalPages);
        this.totalThreads.set(result.totalElements);
        this.loading.set(false);
      },
      error: (err) => {
        if (err?.status === 404) {
          this.notFound.set(true);
        } else {
          this.errorMessage.set('This board could not be loaded. Please try again shortly.');
        }
        this.loading.set(false);
      }
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
