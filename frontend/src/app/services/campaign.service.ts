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
}
