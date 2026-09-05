import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AdminService } from '../../services/admin.service';
import { AdminOverview, AdminRecommendationStats } from '../../models/admin.model';
import { StatTileComponent } from '../../components/stat-tile/stat-tile.component';
import { SeriesChartComponent } from '../../components/series-chart/series-chart.component';
import { compactNumber, percent } from '../../utils/labels';

@Component({
  selector: 'app-overview',
  imports: [RouterLink, StatTileComponent, SeriesChartComponent],
  templateUrl: './overview.component.html',
  styleUrl: './overview.component.css'
})
export class OverviewComponent implements OnInit {
  private adminService = inject(AdminService);

  overview = signal<AdminOverview | null>(null);
  errorMessage = signal<string | null>(null);
  isLoading = signal(true);

  readonly percent = percent;
  readonly compact = compactNumber;

  // Recommendation model shape, loaded beside the overview so a slow or failed
  // model build never delays the main metrics. Null until it arrives.
  recommendationStats = signal<AdminRecommendationStats | null>(null);

  ngOnInit(): void {
    this.adminService.getOverview().subscribe({
      next: (data) => {
        this.overview.set(data);
        this.isLoading.set(false);
      },
      error: () => {
        this.errorMessage.set('Failed to load overview metrics.');
        this.isLoading.set(false);
      }
    });
    this.adminService.getRecommendationStats().subscribe({
      next: (stats) => this.recommendationStats.set(stats),
      error: () => this.recommendationStats.set(null)
    });
  }

  recommendationShare(stats: AdminRecommendationStats, count: number): string {
    return stats.userCount ? this.percent(count / stats.userCount) : '0%';
  }

  modelBuiltLabel(stats: AdminRecommendationStats): string {
    const built = new Date(stats.builtAt);
    if (isNaN(built.getTime())) {
      return 'Built time unknown';
    }
    return `Built ${built.toLocaleTimeString('en-AU', { hour: '2-digit', minute: '2-digit' })}`;
  }

  attentionTotal(data: AdminOverview): number {
    return data.pendingUnitRequests + data.openFlaggedReviews + data.unverifiedUsersLast7Days;
  }

  unitUrl(code: string): string {
    return `https://www.curtinhonestly.com/units/${encodeURIComponent(code)}`;
  }
}
