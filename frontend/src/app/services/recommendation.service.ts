import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Recommendations, SimilarUnits, UnitMatch } from '../models/recommendation.model';

@Injectable({
  providedIn: 'root'
})
export class RecommendationService {
  private http = inject(HttpClient);
  private apiUrl = environment.apiUrl;

  // Personalised picks for the signed-in user. The backend rebuilds its model
  // after every review change and at least every ten minutes.
  getForMe(): Observable<Recommendations> {
    return this.http.get<Recommendations>(`${this.apiUrl}/recommendations/me`);
  }

  // How well one unit fits the signed-in user, from the same model as getForMe.
  getMatchForUnit(code: string): Observable<UnitMatch> {
    return this.http.get<UnitMatch>(`${this.apiUrl}/recommendations/me/units/${encodeURIComponent(code)}`);
  }

  // Public: units that students who liked this unit also rated well.
  getSimilarUnits(code: string): Observable<SimilarUnits> {
    return this.http.get<SimilarUnits>(`${this.apiUrl}/units/${encodeURIComponent(code)}/similar`);
  }
}
