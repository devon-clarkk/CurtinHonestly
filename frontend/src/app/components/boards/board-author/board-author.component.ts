import { Component, computed, input } from '@angular/core';
import { IconComponent } from '../../icon/icon.component';
import { BoardAuthor } from '../../../models/board.model';
import { reviewerRecognitionDisplay, reviewerTierDisplay } from '../../../utils/reviewer-tier.util';

/**
 * Pseudonym plus the marks that go with it: verified student, reviewer tier,
 * recognition, and OP on the thread author's posts. The same chip markup as
 * review cards on the unit page, so authors look the same everywhere.
 */
@Component({
  selector: 'app-board-author',
  standalone: true,
  imports: [IconComponent],
  template: `
    <span class="author">
      <span class="pseudonym">{{ author()?.pseudonym ?? 'Former student' }}</span>
      @if (author()?.verifiedStudent) {
        <span class="verified-badge"
              title="This student confirmed a Curtin student email address"
              aria-label="Verified Curtin student">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="15" height="15" aria-hidden="true">
            <path fill="currentColor" d="M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4zm-1 14.59l-4.29-4.3 1.42-1.41L11 12.17l5.88-5.88 1.41 1.41L11 15.59z"/>
          </svg>
          <span class="verified-label">Verified student</span>
        </span>
      }
      @if (op()) {
        <span class="tier-chip op" title="Started this thread">OP</span>
      }
      @if (tier(); as tier) {
        <span class="tier-chip" [title]="tier.description">{{ tier.label }}</span>
      }
      @if (recognition(); as recognition) {
        <span class="tier-chip recognition" [title]="recognition.description">
          <app-icon name="thumb-up" [filled]="true" />
          {{ recognition.label }}
        </span>
      }
    </span>
  `,
  styles: [`
    .author {
      display: inline-flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 6px;
      font-size: 0.95rem;
    }

    .pseudonym {
      font-weight: 700;
      color: var(--secondary-color);
    }

    .verified-badge {
      display: inline-flex;
      align-items: center;
      gap: 3px;
      color: #2563eb;
      font-size: 0.8rem;
      font-weight: 600;
    }

    .tier-chip {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      padding: 1px 8px;
      border: 1px solid var(--border-color);
      border-radius: var(--radius-none);
      background: var(--surface-muted);
      color: var(--text-secondary);
      font-size: 0.75rem;
      font-weight: 600;
      line-height: 1.5;
    }

    .tier-chip.recognition {
      border-color: #ead9a8;
      background: #fbf6e6;
      color: var(--primary-ink);
    }

    .tier-chip.op {
      border-color: var(--secondary-color);
      background: var(--secondary-color);
      color: #fff;
      letter-spacing: 0.04em;
    }

    .tier-chip app-icon {
      display: inline-flex;
      font-size: 0.75rem;
    }

    @media (max-width: 480px) {
      .verified-label {
        position: absolute;
        width: 1px;
        height: 1px;
        overflow: hidden;
        clip: rect(0 0 0 0);
      }
    }
  `]
})
export class BoardAuthorComponent {
  author = input.required<BoardAuthor | null>();
  op = input(false);

  tier = computed(() => {
    const a = this.author();
    return a ? reviewerTierDisplay({ reviewerTier: a.tier, reviewerTierLabel: a.tierLabel }) : null;
  });

  recognition = computed(() => {
    const a = this.author();
    return a
      ? reviewerRecognitionDisplay({ reviewerRecognition: a.recognition, reviewerRecognitionLabel: a.recognitionLabel })
      : null;
  });
}
