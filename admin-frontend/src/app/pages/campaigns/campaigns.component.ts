import { Component, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../../services/admin.service';
import { CampaignAdmin, CampaignEntryAdmin } from '../../models/admin.model';

@Component({
  selector: 'app-campaigns',
  imports: [FormsModule, DatePipe],
  templateUrl: './campaigns.component.html',
  styleUrl: './campaigns.component.css'
})
export class CampaignsComponent implements OnInit {
  private adminService = inject(AdminService);

  campaigns = signal<CampaignAdmin[]>([]);
  selectedEntries = signal<CampaignEntryAdmin[]>([]);
  selectedCampaignId = signal<string | null>(null);
  errorMessage = signal<string | null>(null);
  successMessage = signal<string | null>(null);

  slug = '';
  code = '';
  name = '';
  prizeDescription = '';
  startsAt = '';
  endsAt = '';
  maxRedemptions: number | null = null;
  minReviewLength = 50;
  maxEntriesPerUser = 1;
  requireVerifiedStudent = true;
  requiredReviewCount = 1;
  minLikesReceived = 0;
  minLikesGiven = 0;

  // Tracking-only referral link form (no reward config).
  refSlug = '';
  refName = '';

  ngOnInit(): void {
    this.refreshCampaigns();
    this.setDefaultDates();
  }

  refreshCampaigns(): void {
    this.adminService.listCampaigns().subscribe({
      next: (data) => this.campaigns.set(data),
      error: () => this.errorMessage.set('Failed to load campaigns.')
    });
  }

  createCampaign(): void {
    this.clearMessages();

    this.adminService.createCampaign({
      slug: this.slug,
      code: this.code,
      name: this.name,
      prizeDescription: this.prizeDescription,
      startsAt: new Date(this.startsAt).toISOString(),
      endsAt: new Date(this.endsAt).toISOString(),
      maxRedemptions: this.maxRedemptions,
      minReviewLength: this.minReviewLength,
      maxEntriesPerUser: this.maxEntriesPerUser,
      requireVerifiedStudent: this.requireVerifiedStudent,
      requiredReviewCount: this.requiredReviewCount,
      minLikesReceived: this.minLikesReceived,
      minLikesGiven: this.minLikesGiven
    }).subscribe({
      next: () => {
        this.successMessage.set('Campaign created.');
        this.slug = '';
        this.code = '';
        this.name = '';
        this.prizeDescription = '';
        this.maxRedemptions = null;
        this.minLikesReceived = 0;
        this.minLikesGiven = 0;
        this.setDefaultDates();
        this.refreshCampaigns();
      },
      error: (err) => this.errorMessage.set(err.error?.error || 'Failed to create campaign.')
    });
  }

  createReferralLink(): void {
    this.clearMessages();

    this.adminService.createReferralLink({ slug: this.refSlug, name: this.refName }).subscribe({
      next: () => {
        this.successMessage.set('Referral link created.');
        this.refSlug = '';
        this.refName = '';
        this.refreshCampaigns();
      },
      error: (err) => this.errorMessage.set(err.error?.error || 'Failed to create referral link.')
    });
  }

  toggleActive(campaign: CampaignAdmin): void {
    this.clearMessages();
    this.adminService.setCampaignActive(campaign.id, !campaign.active).subscribe({
      next: () => {
        this.successMessage.set(campaign.active ? 'Campaign deactivated.' : 'Campaign activated.');
        this.refreshCampaigns();
      },
      error: () => this.errorMessage.set('Failed to update campaign status.')
    });
  }

  viewEntries(campaign: CampaignAdmin): void {
    this.clearMessages();
    this.selectedCampaignId.set(campaign.id);
    this.adminService.listCampaignEntries(campaign.id).subscribe({
      next: (entries) => this.selectedEntries.set(entries),
      error: () => this.errorMessage.set('Failed to load campaign entries.')
    });
  }

  registrationLink(campaign: CampaignAdmin): string {
    const origin = typeof window !== 'undefined' ? window.location.origin.replace(':4201', ':4200') : 'https://curtinhonestly.com';
    // Tracking-only links carry only the ref (their code is a hidden placeholder);
    // reward campaigns include the promo code so it prefills on the register page.
    const base = `${origin}/register?ref=${encodeURIComponent(campaign.slug)}`;
    return campaign.trackingOnly ? base : `${base}&code=${encodeURIComponent(campaign.code)}`;
  }

  copyLink(campaign: CampaignAdmin): void {
    navigator.clipboard.writeText(this.registrationLink(campaign)).then(() => {
      this.successMessage.set(campaign.trackingOnly ? 'Referral link copied.' : 'Registration link copied.');
    }).catch(() => this.errorMessage.set('Could not copy link.'));
  }

  exportEntriesCsv(): void {
    const entries = this.selectedEntries();
    if (!entries.length) {
      return;
    }

    const header = 'entryToken,userEmail,unitCode,createdAt';
    const rows = entries.map(entry =>
      [entry.entryToken, entry.userEmail, entry.unitCode, entry.createdAt]
        .map(value => `"${String(value).replace(/"/g, '""')}"`)
        .join(',')
    );
    const csv = [header, ...rows].join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `campaign-entries-${this.selectedCampaignId()}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  }

  private setDefaultDates(): void {
    const now = new Date();
    const end = new Date(now);
    end.setDate(end.getDate() + 21);
    this.startsAt = now.toISOString().slice(0, 16);
    this.endsAt = end.toISOString().slice(0, 16);
  }

  private clearMessages(): void {
    this.errorMessage.set(null);
    this.successMessage.set(null);
  }
}
