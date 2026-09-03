import { Component, computed, input } from '@angular/core';

export interface HBarRow {
  label: string;
  value: number;
  // Meter mode: the bar fills value/total of a same-hue track.
  total?: number;
  // Text shown at the bar tip. Defaults to the value.
  valueLabel?: string;
  // Hover text. Defaults to "label: valueLabel".
  title?: string;
}

// Horizontal bars, one series, gold. "bar" mode scales every bar against the
// largest value; "meter" mode fills a lighter same-hue track to value/total.
// Every chart carries a table twin, so no value depends on the bar alone.
@Component({
  selector: 'app-hbar-chart',
  templateUrl: './hbar-chart.component.html',
  styleUrl: './hbar-chart.component.css'
})
export class HbarChartComponent {
  rows = input.required<HBarRow[]>();
  mode = input<'bar' | 'meter'>('bar');
  labelHeader = input('Label');
  valueHeader = input('Count');
  emptyMessage = input('No data yet.');

  max = computed(() => Math.max(1, ...this.rows().map((r) => r.value)));

  widthPercent(row: HBarRow): number {
    if (this.mode() === 'meter') {
      const total = row.total ?? 0;
      return total > 0 ? Math.min(100, (row.value / total) * 100) : 0;
    }
    return (row.value / this.max()) * 100;
  }

  valueText(row: HBarRow): string {
    return row.valueLabel ?? row.value.toLocaleString('en-AU');
  }

  titleText(row: HBarRow): string {
    return row.title ?? `${row.label}: ${this.valueText(row)}`;
  }
}
