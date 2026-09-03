import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AdminService } from '../../services/admin.service';
import { AdminOverview } from '../../models/admin.model';
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
  }

  attentionTotal(data: AdminOverview): number {
    return data.pendingUnitRequests + data.openFlaggedReviews + data.unverifiedUsersLast7Days;
  }

  unitUrl(code: string): string {
    return `https://www.curtinhonestly.com/units/${encodeURIComponent(code)}`;
  }
}
