import { Component, computed, input } from '@angular/core';

// A single headline number with an optional signed delta against a named
// prior period. Delta colour follows direction x whether up is good.
@Component({
  selector: 'app-stat-tile',
  templateUrl: './stat-tile.component.html',
  styleUrl: './stat-tile.component.css'
})
export class StatTileComponent {
  label = input.required<string>();
  value = input.required<string>();
  hint = input<string | null>(null);
  current = input<number | null>(null);
  prior = input<number | null>(null);
  deltaLabel = input('vs prior 7 days');
  upIsGood = input(true);
  accent = input(false);

  delta = computed(() => {
    const current = this.current();
    const prior = this.prior();
    if (current === null || prior === null) return null;
    const diff = current - prior;
    const pct = prior > 0 ? Math.round((diff / prior) * 100) : null;
    return { diff, pct };
  });

  deltaText = computed(() => {
    const delta = this.delta();
    if (!delta) return '';
    const sign = delta.diff > 0 ? '+' : delta.diff < 0 ? '-' : '';
    const abs = Math.abs(delta.diff).toLocaleString('en-AU');
    const pct = delta.pct === null ? '' : ` (${sign}${Math.abs(delta.pct)}%)`;
    return delta.diff === 0 ? 'No change' : `${sign}${abs}${pct}`;
  });

  deltaTone = computed(() => {
    const delta = this.delta();
    if (!delta || delta.diff === 0) return 'flat';
    const good = delta.diff > 0 === this.upIsGood();
    return good ? 'good' : 'bad';
  });
}
