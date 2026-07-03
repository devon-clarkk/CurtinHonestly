import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { MyReview } from '../models/unit.model';

export interface CampaignProgress {
  qualifyingReviews: number;
  requiredReviews: number;
  entriesEarned: number;
  maxEntries: number;
  requireVerifiedStudent: boolean;
}

export interface CreateReviewResponse {
  review: unknown;
  campaignEntryToken: string | null;
  campaignName: string | null;
  campaignProgress: CampaignProgress | null;
}

@Injectable({
  providedIn: 'root'
})
export class ReviewService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/reviews`;

  getMyReviews(): Observable<MyReview[]> {
    return this.http.get<MyReview[]>(`${this.apiUrl}/me`);
  }

  getMyReviewForUnit(unitCode: string): Observable<MyReview | null> {
    return this.http.get<MyReview>(`${this.apiUrl}/me/unit/${encodeURIComponent(unitCode)}`, {
      observe: 'response'
    }).pipe(
      map(response => (response.status === 204 || !response.body) ? null : response.body)
    );
  }

  createReview(review: unknown): Observable<CreateReviewResponse> {
    return this.http.post<CreateReviewResponse>(this.apiUrl, review);
  }

  updateReview(id: string, review: unknown): Observable<CreateReviewResponse> {
    return this.http.patch<CreateReviewResponse>(`${this.apiUrl}/${id}`, review);
  }

  deleteReview(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
