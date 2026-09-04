import { Component, Input, OnChanges, PLATFORM_ID, SimpleChanges, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { RouterLink } from '@angular/router';
import { BoardService } from '../../../services/board.service';
import { BoardUnitSummary } from '../../../models/board.model';
import { timeAgo } from '../board-time.util';

/**
 * "Unit discussion" card for the unit page: counts, the three most active
 * threads, and a link to start one. Drop in with
 * `<app-unit-board-preview [unitCode]="unit.code" />`.
 *
 * Fetches in the browser only. The unit page is prerendered and this content
 * changes by the minute, so it must never be baked into the static HTML.
 */
@Component({
  selector: 'app-unit-board-preview',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './unit-board-preview.component.html',
  styleUrl: './unit-board-preview.component.css'
})
export class UnitBoardPreviewComponent implements OnChanges {
  @Input() unitCode: string | null | undefined;

  private boardService = inject(BoardService);
  private platformId = inject(PLATFORM_ID);

  summary = signal<BoardUnitSummary | null>(null);
  loading = signal(false);
  failed = signal(false);

  readonly timeAgo = timeAgo;

  ngOnChanges(changes: SimpleChanges): void {
    if (!changes['unitCode']) {
      return;
    }
    this.summary.set(null);
    this.failed.set(false);
    const code = (this.unitCode ?? '').trim();
    if (!code || !isPlatformBrowser(this.platformId)) {
      return;
    }
    this.loading.set(true);
    this.boardService.getUnitSummary(code).subscribe({
      next: (summary) => {
        this.summary.set(summary);
        this.loading.set(false);
      },
      error: () => {
        this.failed.set(true);
        this.loading.set(false);
      }
    });
  }

  boardCode(): string {
    return this.summary()?.unitCode ?? (this.unitCode ?? '').trim().toUpperCase();
  }

  replyLabel(count: number): string {
    return count === 1 ? 'reply' : 'replies';
  }
}
