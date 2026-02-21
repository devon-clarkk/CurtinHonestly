import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Page, UnitSummary, UnitDetails, Review } from '../models/unit.model';

@Injectable({
  providedIn: 'root'
})
export class UnitService {
  private http = inject(HttpClient);
  // Tip for beginners: This is the URL where your backend is running.
  private apiUrl = 'http://localhost:8080/units';

  // Fetches a paginated list of unit summaries
  getUnits(page: number = 0, size: number = 10): Observable<Page<UnitSummary>> {
    return this.http.get<Page<UnitSummary>>(`${this.apiUrl}?page=${page}&size=${size}`);
  }

  // Fetches full details for a single unit using its unit code
  getUnitByCode(code: string): Observable<UnitDetails> {
    return this.http.get<UnitDetails>(`${this.apiUrl}/${code}`);
  }

  // Fetches only the reviews for a specific unit
  getReviewsForUnit(code: string): Observable<Review[]> {
    return this.http.get<Review[]>(`${this.apiUrl}/${code}/reviews`);
  }
}
