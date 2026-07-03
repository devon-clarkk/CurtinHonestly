import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { MyReview } from '../models/unit.model';

export interface CreateReviewResponse {
  review: unknown;
  campaignEntryToken: string | null;
  campaignName: string | null;
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

  createReview(review: unknown): Observable<CreateReviewResponse> {
    return this.http.post<CreateReviewResponse>(this.apiUrl, review);
  }

  deleteReview(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
