import { Injectable, inject } from '@angular/core';
import { CampaignService } from './campaign.service';

// Shared with register.component so a ref captured on any page is still there
// when the visitor reaches the signup form.
export const CAMPAIGN_REF_KEY = 'campaign_ref';

// Captures a ?ref=<slug> from anywhere in the app (not just /register). It
// persists the slug so a later signup — and that user's reviews — attribute to
// the link, and records the visit once per slug per app session. Browser-only:
// callers must guard against SSR before invoking capture().
@Injectable({ providedIn: 'root' })
export class ReferralTrackingService {
  private campaignService = inject(CampaignService);
  private recordedRefs = new Set<string>();

  capture(ref: string | null | undefined): void {
    const slug = ref?.trim();
    if (!slug) {
      return;
    }
    try {
      localStorage.setItem(CAMPAIGN_REF_KEY, slug);
    } catch {
      // Storage unavailable (e.g. private mode) — the visit is still recorded.
    }
    if (!this.recordedRefs.has(slug)) {
      this.recordedRefs.add(slug);
      this.campaignService.recordVisit(slug);
    }
  }

  getStoredRef(): string | null {
    try {
      return localStorage.getItem(CAMPAIGN_REF_KEY);
    } catch {
      return null;
    }
  }
}
