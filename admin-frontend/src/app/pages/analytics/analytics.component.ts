import { Component, inject, OnInit, signal } from '@angular/core';
import { DecimalPipe, KeyValuePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../../services/admin.service';
import { AdminAnalytics } from '../../models/admin.model';

@Component({
  selector: 'app-analytics',
  imports: [KeyValuePipe, DecimalPipe, FormsModule],
  templateUrl: './analytics.component.html',
  styleUrl: './analytics.component.css'
})
export class AnalyticsComponent implements OnInit {
  private adminService = inject(AdminService);

  analytics = signal<AdminAnalytics | null>(null);
  errorMessage = signal<string | null>(null);
  isLoading = signal(true);
  selectedDays = 30;

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

  maxSeriesValue(points: { users: number; reviews: number }[]): number {
    if (!points.length) return 1;
    return Math.max(1, ...points.flatMap((p) => [p.users, p.reviews]));
  }

  barHeight(value: number, max: number): string {
    return `${Math.round((value / max) * 100)}%`;
  }
}
