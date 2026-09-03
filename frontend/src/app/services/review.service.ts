import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { UnitCacheService } from './unit-cache.service';
import { environment } from '../../environments/environment';
import { MyReview, ReviewerProfile } from '../models/unit.model';

export interface CampaignProgress {
  qualifyingReviews: number;
  requiredReviews: number;
  entriesEarned: number;
  maxEntries: number;
  requireVerifiedStudent: boolean;
  minLikesReceived: number;
  minLikesGiven: number;
  likesGiven: number;
}

export interface CreateReviewResponse {
  review: unknown;
  campaignEntryToken: string | null;
  campaignName: string | null;
  newEntryCount: number;
}

export interface ReviewLikeResponse {
  reviewId: string;
  likeCount: number;
  likedByCurrentUser: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class ReviewService {
  private http = inject(HttpClient);
  // Writing a review changes review counts and ratings on the catalog, so the
  // cached unit list has to go with it.
  private unitCache = inject(UnitCacheService);
  private apiUrl = `${environment.apiUrl}/reviews`;

  getMyReviews(): Observable<MyReview[]> {
    return this.http.get<MyReview[]>(`${this.apiUrl}/me`);
  }

  // Authenticated. Lives outside /reviews because GET /reviews/** is admin-only
  // on the backend, with /reviews/me as the one exact-path exception.
  getMyReviewerProfile(): Observable<ReviewerProfile> {
    return this.http.get<ReviewerProfile>(`${environment.apiUrl}/reviewer-rank/me`);
  }

  createReview(review: unknown): Observable<CreateReviewResponse> {
    return this.http.post<CreateReviewResponse>(this.apiUrl, review)
      .pipe(tap(() => this.unitCache.clear()));
  }

  updateReview(id: string, review: unknown): Observable<unknown> {
    return this.http.put(`${this.apiUrl}/${id}`, review)
      .pipe(tap(() => this.unitCache.clear()));
  }

  deleteReview(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`)
      .pipe(tap(() => this.unitCache.clear()));
  }

  likeReview(id: string): Observable<ReviewLikeResponse> {
    return this.http.post<ReviewLikeResponse>(`${this.apiUrl}/${id}/likes`, {});
  }

  unlikeReview(id: string): Observable<ReviewLikeResponse> {
    return this.http.delete<ReviewLikeResponse>(`${this.apiUrl}/${id}/likes`);
  }

  flagReview(id: string, reason?: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${id}/flags`, { reason });
  }
}
