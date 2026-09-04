import { Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../services/auth.service';
import { BoardService } from '../../../services/board.service';
import { BOARD_BODY_MAX, BOARD_TITLE_MAX, BoardScope, BoardThreadDetail } from '../../../models/board.model';
import { apiErrorMessage } from '../board-time.util';

/**
 * Inline "start a thread" form, shared by the general board and every unit
 * board. Anonymous visitors see a sign-in prompt in its place; the parent
 * decides where to go once `created` fires.
 */
@Component({
  selector: 'app-thread-composer',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './thread-composer.component.html',
  styleUrls: ['../boards.css', './thread-composer.component.css']
})
export class ThreadComposerComponent {
  scope = input<BoardScope>('GENERAL');
  unitCode = input<string | null>(null);

  created = output<BoardThreadDetail>();
  cancelled = output<void>();

  authService = inject(AuthService);
  private boardService = inject(BoardService);

  readonly titleMax = BOARD_TITLE_MAX;
  readonly bodyMax = BOARD_BODY_MAX;

  title = signal('');
  body = signal('');
  submitting = signal(false);
  errorMessage = signal<string | null>(null);

  canSubmit = computed(() => {
    const title = this.title().trim();
    const body = this.body().trim();
    return title.length > 0 && body.length > 0
      && this.title().length <= this.titleMax && this.body().length <= this.bodyMax
      && !this.submitting();
  });

  heading = computed(() => {
    const code = this.unitCode();
    return this.scope() === 'UNIT' && code ? `Start a discussion about ${code}` : 'Start a thread';
  });

  submit(): void {
    if (!this.canSubmit()) {
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);
    const title = this.title().trim();
    const body = this.body().trim();
    const code = this.unitCode();
    const request$ = this.scope() === 'UNIT' && code
      ? this.boardService.createUnitThread(code, title, body)
      : this.boardService.createGeneralThread(title, body);

    request$.subscribe({
      next: (thread) => {
        this.submitting.set(false);
        this.title.set('');
        this.body.set('');
        this.created.emit(thread);
      },
      error: (err) => {
        this.submitting.set(false);
        this.errorMessage.set(apiErrorMessage(err, 'Your thread could not be posted. Please try again.'));
      }
    });
  }

  cancel(): void {
    this.errorMessage.set(null);
    this.cancelled.emit();
  }
}
