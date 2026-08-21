import { Component, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../../services/admin.service';
import { CampaignAdmin, CampaignEntryAdmin, ReferralLinkAdmin } from '../../models/admin.model';

@Component({
  selector: 'app-campaigns',
  imports: [FormsModule, DatePipe],
  templateUrl: './campaigns.component.html',
  styleUrl: './campaigns.component.css'
})
export class CampaignsComponent implements OnInit {
  private adminService = inject(AdminService);

  campaigns = signal<CampaignAdmin[]>([]);
  referralLinks = signal<ReferralLinkAdmin[]>([]);
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

  // Referral link form: a slug + name + landing page + which campaigns it enrols
  // signups into (none = a pure tracking link; several = multiple draws per link).
  refSlug = '';
  refName = '';
  // Landing-page picker: which page the link forwards to. 'unit'/'custom' reveal
  // an extra input; everything else maps straight to a path in resolveLandingPath().
  refLandingType: 'home' | 'register' | 'unit' | 'custom' = 'home';
  refUnitCode = '';
  refCustomPath = '';
  refCampaignIds = new Set<string>();

  ngOnInit(): void {
    this.refreshCampaigns();
    this.refreshReferralLinks();
    this.setDefaultDates();
  }

  // Only reward campaigns can be attached to a referral link (tracking-only ones
  // enrol no one), so they're the options in the multi-select.
  rewardCampaigns(): CampaignAdmin[] {
    return this.campaigns().filter(c => !c.trackingOnly);
  }

  refreshReferralLinks(): void {
    this.adminService.listReferralLinks().subscribe({
      next: (data) => this.referralLinks.set(data),
      error: () => this.errorMessage.set('Failed to load referral links.')
    });
  }

  isRefCampaignSelected(id: string): boolean {
    return this.refCampaignIds.has(id);
  }

  toggleRefCampaign(id: string): void {
    if (this.refCampaignIds.has(id)) {
      this.refCampaignIds.delete(id);
    } else {
      this.refCampaignIds.add(id);
    }
  }

  // Live plain-English restatement of the entry rules, so the admin can see what
  // their numbers actually do without decoding each field.
  campaignRulesSummary(): string {
    const per = Math.max(1, this.requiredReviewCount || 1);
    const max = Math.max(1, this.maxEntriesPerUser || 1);
    const minLen = Math.max(0, this.minReviewLength || 0);

    let sentence = per === 1
      ? `Students earn 1 draw entry for every qualifying review`
      : `Students earn 1 draw entry for every ${per} qualifying reviews`;
    sentence += `, up to ${max} ${max === 1 ? 'entry' : 'entries'} each.`;

    const quals: string[] = [];
    if (minLen > 0) {
      quals.push(`at least ${minLen} characters long`);
    }
    if (this.minLikesReceived > 0) {
      quals.push(`marked helpful by at least ${this.minLikesReceived} ${this.minLikesReceived === 1 ? 'person' : 'people'}`);
    }
    if (quals.length) {
      sentence += ` A review counts once it's ${quals.join(' and ')}.`;
    }

    if (this.requireVerifiedStudent) {
      sentence += ' Only verified Curtin students earn entries.';
    }
    if (this.minLikesGiven > 0) {
      sentence += ` Each user must first mark at least ${this.minLikesGiven} review${this.minLikesGiven === 1 ? '' : 's'} helpful.`;
    }

    sentence += ' Only reviews left between the start and end dates count.';
    return sentence;
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

    this.adminService.createReferralLink({
      slug: this.refSlug,
      name: this.refName,
      landingPath: this.resolveLandingPath(),
      campaignIds: [...this.refCampaignIds]
    }).subscribe({
      next: () => {
        this.successMessage.set('Referral link created.');
        this.refSlug = '';
        this.refName = '';
        this.refLandingType = 'home';
        this.refUnitCode = '';
        this.refCustomPath = '';
        this.refCampaignIds = new Set<string>();
        this.refreshReferralLinks();
      },
      error: (err) => this.errorMessage.set(err.error?.error || 'Failed to create referral link.')
    });
  }

  // Share links must point at the STUDENT site, not wherever the admin panel is
  // served from. In prod the admin runs on admin.curtinhonestly.com, so strip the
  // admin. subdomain; locally the admin runs on :4201, so map it to the app's :4200.
  private studentOrigin(): string {
    if (typeof window === 'undefined') {
      return 'https://curtinhonestly.com';
    }
    return window.location.origin
      .replace('://admin.', '://')
      .replace(':4201', ':4200');
  }

  referralLinkUrl(link: ReferralLinkAdmin): string {
    const origin = this.studentOrigin();
    return `${origin}${link.landingPath || '/'}?ref=${encodeURIComponent(link.slug)}`;
  }

  copyReferralLinkUrl(link: ReferralLinkAdmin): void {
    navigator.clipboard.writeText(this.referralLinkUrl(link)).then(() => {
      this.successMessage.set('Referral link copied.');
    }).catch(() => this.errorMessage.set('Could not copy link.'));
  }

  toggleReferralLinkActive(link: ReferralLinkAdmin): void {
    this.clearMessages();
    this.adminService.setReferralLinkActive(link.id, !link.active).subscribe({
      next: () => {
        this.successMessage.set(link.active ? 'Referral link deactivated.' : 'Referral link activated.');
        this.refreshReferralLinks();
      },
      error: () => this.errorMessage.set('Failed to update referral link.')
    });
  }

  // Turns the picker selection into a site-relative path. The backend
  // re-validates/normalises this, so this only needs to produce a sensible value.
  private resolveLandingPath(): string {
    switch (this.refLandingType) {
      case 'register':
        return '/register';
      case 'unit': {
        const code = this.refUnitCode.trim().toUpperCase();
        return code ? `/units/${code}` : '/';
      }
      case 'custom': {
        const path = this.refCustomPath.trim();
        if (!path) {
          return '/';
        }
        return path.startsWith('/') ? path : `/${path}`;
      }
      case 'home':
      default:
        return '/';
    }
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
    const origin = this.studentOrigin();
    const ref = encodeURIComponent(campaign.slug);
    if (campaign.trackingOnly) {
      // Tracking links forward to the admin-chosen page (site-wide capture records
      // the ref anywhere); the code is a hidden placeholder so it's not included.
      const path = campaign.landingPath || '/';
      return `${origin}${path}?ref=${ref}`;
    }
    // Reward campaigns land on register with the promo code prefilled.
    return `${origin}/register?ref=${ref}&code=${encodeURIComponent(campaign.code)}`;
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
