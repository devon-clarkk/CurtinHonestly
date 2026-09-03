import { Component, computed, input } from '@angular/core';
import { TimeSeriesPoint } from '../../models/admin.model';

interface Slot {
  x: number;
  width: number;
  label: string;
  tick: boolean;
  point: TimeSeriesPoint;
  usersBar: { x: number; y: number; width: number; height: number };
  reviewsBar: { x: number; y: number; width: number; height: number };
  usersY: number;
  reviewsY: number;
}

// Daily users vs reviews on one shared count axis. Grouped columns up to 45
// days; lines beyond that, where columns would be thinner than 2px.
// Slot 1 (gold) is reviews, the metric the site runs on; slot 2 (blue) is users.
@Component({
  selector: 'app-series-chart',
  templateUrl: './series-chart.component.html',
  styleUrl: './series-chart.component.css'
})
export class SeriesChartComponent {
  points = input.required<TimeSeriesPoint[]>();

  readonly width = 760;
  readonly height = 240;
  readonly padding = { top: 12, right: 12, bottom: 30, left: 36 };

  private readonly plotWidth = this.width - this.padding.left - this.padding.right;
  private readonly plotHeight = this.height - this.padding.top - this.padding.bottom;
  readonly baselineY = this.padding.top + this.plotHeight;

  asLines = computed(() => this.points().length > 45);

  yMax = computed(() => {
    const raw = Math.max(0, ...this.points().flatMap((p) => [p.users, p.reviews]));
    return niceCeil(Math.max(4, raw));
  });

  yTicks = computed(() => {
    const max = this.yMax();
    const steps = 4;
    return Array.from({ length: steps + 1 }, (_, i) => {
      const value = Math.round((max / steps) * i);
      return { value, y: this.yFor(value) };
    });
  });

  slots = computed<Slot[]>(() => {
    const points = this.points();
    const n = points.length;
    if (!n) return [];
    const slotWidth = this.plotWidth / n;
    const gap = 2;
    const barWidth = Math.min(24, Math.max(2, (slotWidth - gap * 3) / 2));
    const groupWidth = barWidth * 2 + gap;
    const every = Math.max(1, Math.ceil(n / 8));
    return points.map((point, i) => {
      const x = this.padding.left + i * slotWidth;
      const groupX = x + (slotWidth - groupWidth) / 2;
      const usersY = this.yFor(point.users);
      const reviewsY = this.yFor(point.reviews);
      return {
        x,
        width: slotWidth,
        label: shortDate(point.period),
        // Count back from the newest day so the last date is always labelled
        // and neighbouring labels never collide.
        tick: (n - 1 - i) % every === 0,
        point,
        reviewsBar: { x: groupX, y: reviewsY, width: barWidth, height: this.baselineY - reviewsY },
        usersBar: { x: groupX + barWidth + gap, y: usersY, width: barWidth, height: this.baselineY - usersY },
        usersY,
        reviewsY
      };
    });
  });

  usersPath = computed(() => this.linePath(this.slots().map((s) => [s.x + s.width / 2, s.usersY])));
  reviewsPath = computed(() => this.linePath(this.slots().map((s) => [s.x + s.width / 2, s.reviewsY])));

  totals = computed(() => ({
    users: this.points().reduce((sum, p) => sum + p.users, 0),
    reviews: this.points().reduce((sum, p) => sum + p.reviews, 0)
  }));

  lastSlot = computed(() => {
    const slots = this.slots();
    return slots.length ? slots[slots.length - 1] : null;
  });

  tooltip(slot: Slot): string {
    return `${longDate(slot.point.period)}: ${slot.point.reviews} reviews, ${slot.point.users} users`;
  }

  private yFor(value: number): number {
    return this.padding.top + this.plotHeight - (value / this.yMax()) * this.plotHeight;
  }

  private linePath(coords: number[][]): string {
    return coords.map(([x, y], i) => `${i === 0 ? 'M' : 'L'}${x.toFixed(1)} ${y.toFixed(1)}`).join(' ');
  }
}

function niceCeil(value: number): number {
  const magnitude = Math.pow(10, Math.floor(Math.log10(value)));
  const normalized = value / magnitude;
  const nice = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 4 ? 4 : normalized <= 5 ? 5 : 10;
  return nice * magnitude;
}

function shortDate(period: string): string {
  const date = new Date(`${period}T00:00:00Z`);
  return date.toLocaleDateString('en-AU', { day: 'numeric', month: 'short', timeZone: 'UTC' });
}

function longDate(period: string): string {
  const date = new Date(`${period}T00:00:00Z`);
  return date.toLocaleDateString('en-AU', { weekday: 'short', day: 'numeric', month: 'short', timeZone: 'UTC' });
}
