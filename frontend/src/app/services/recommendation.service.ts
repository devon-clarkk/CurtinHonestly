import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Recommendations, SimilarUnits } from '../models/recommendation.model';

@Injectable({
  providedIn: 'root'
})
export class RecommendationService {
  private http = inject(HttpClient);
  private apiUrl = environment.apiUrl;

  // Personalised picks for the signed-in user. The backend keeps its model for
  // up to ten minutes, so a review written just now shows up on the next rebuild.
  getForMe(): Observable<Recommendations> {
    return this.http.get<Recommendations>(`${this.apiUrl}/recommendations/me`);
  }

  // Public: units that students who liked this unit also rated well.
  getSimilarUnits(code: string): Observable<SimilarUnits> {
    return this.http.get<SimilarUnits>(`${this.apiUrl}/units/${encodeURIComponent(code)}/similar`);
  }
}
