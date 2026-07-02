import { Component, inject, OnInit, signal } from '@angular/core';
import { AdminService } from '../../services/admin.service';
import { AdminOverview } from '../../models/admin.model';

@Component({
  selector: 'app-overview',
  templateUrl: './overview.component.html',
  styleUrl: './overview.component.css'
})
export class OverviewComponent implements OnInit {
  private adminService = inject(AdminService);

  overview = signal<AdminOverview | null>(null);
  errorMessage = signal<string | null>(null);
  isLoading = signal(true);

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
}
