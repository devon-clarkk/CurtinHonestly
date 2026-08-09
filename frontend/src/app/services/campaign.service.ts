import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface CampaignValidation {
  valid: boolean;
  message: string;
  campaignName: string | null;
  prizeDescription: string | null;
  endsAt: string | null;
}

@Injectable({ providedIn: 'root' })
export class CampaignService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/campaigns`;

  validate(ref?: string, code?: string): Observable<CampaignValidation> {
    const params: Record<string, string> = {};
    if (ref?.trim()) {
      params['ref'] = ref.trim();
    }
    if (code?.trim()) {
      params['code'] = code.trim();
    }
    return this.http.get<CampaignValidation>(`${this.apiUrl}/validate`, { params });
  }

  // Fire-and-forget beacon: record that a visitor arrived via a referral link.
  // Errors are swallowed — tracking must never disrupt the signup flow.
  recordVisit(ref: string): void {
    if (!ref?.trim()) {
      return;
    }
    this.http.post(`${this.apiUrl}/visit`, null, { params: { ref: ref.trim() } })
      .subscribe({ error: () => {} });
  }
}
