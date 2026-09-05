import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../../services/admin.service';
import { AdminAnalytics } from '../../models/admin.model';
import { StatTileComponent } from '../../components/stat-tile/stat-tile.component';
import { SeriesChartComponent } from '../../components/series-chart/series-chart.component';
import { HbarChartComponent, HBarRow } from '../../components/hbar-chart/hbar-chart.component';
import { compactNumber, percent, termLabel } from '../../utils/labels';

@Component({
  selector: 'app-analytics',
  imports: [FormsModule, StatTileComponent, SeriesChartComponent, HbarChartComponent],
  templateUrl: './analytics.component.html',
  styleUrl: './analytics.component.css'
})
export class AnalyticsComponent implements OnInit {
  private adminService = inject(AdminService);

  analytics = signal<AdminAnalytics | null>(null);
  errorMessage = signal<string | null>(null);
  isLoading = signal(true);
  selectedDays = 30;

  readonly percent = percent;
  readonly compact = compactNumber;

  ratingRows = computed<HBarRow[]>(() => this.shareRows(this.analytics()?.ratingDistribution ?? []));
  workloadRows = computed<HBarRow[]>(() => this.shareRows(this.analytics()?.workloadDistribution ?? []));

  facultyRows = computed<HBarRow[]>(() =>
    (this.analytics()?.facultyBreakdown ?? []).map((f) => ({
      label: f.label,
      value: f.unitsWithReviews,
      total: f.units,
      valueLabel: `${f.unitsWithReviews} / ${f.units} (${percent(f.units ? f.unitsWithReviews / f.units : 0, 1)})`,
      title: `${f.label}: ${f.unitsWithReviews} of ${f.units} units reviewed, ${f.reviews} reviews`
    }))
  );

  termRows = computed<HBarRow[]>(() =>
    (this.analytics()?.reviewsByTerm ?? []).map((t) => ({
      label: termLabel(t.termType, t.termYear),
      value: t.count
    }))
  );

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.adminService.getAnalytics(this.selectedDays).subscribe({
      next: (data) => {
        this.analytics.set(data);
        this.isLoading.set(false);
      },
      error: () => {
        this.errorMessage.set('Failed to load analytics.');
        this.isLoading.set(false);
      }
    });
  }

  unitUrl(code: string): string {
    return `https://www.curtinhonestly.com/units/${encodeURIComponent(code)}`;
  }

  // Count plus share of all reviews at the bar tip, so the histogram reads
  // without a second axis.
  private shareRows(buckets: { label: string; count: number }[]): HBarRow[] {
    const total = buckets.reduce((sum, b) => sum + b.count, 0);
    return buckets.map((b) => ({
      label: b.label,
      value: b.count,
      valueLabel: `${b.count.toLocaleString('en-AU')} (${percent(total ? b.count / total : 0)})`
    }));
  }
}
