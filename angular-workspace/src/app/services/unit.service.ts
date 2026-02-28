import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Page, UnitSummary, UnitDetails, Review, Faculty, FacultyDisplayNames, UnitLevel, UnitLevelDisplayNames } from '../models/unit.model';

@Injectable({
  providedIn: 'root'
})
export class UnitService {
  private http = inject(HttpClient);
  // Tip for beginners: This is the URL where your backend is running.
  private apiUrl = 'http://localhost:8080/units';

  // Fetches a paginated list of unit summaries with optional filters and sorting
  getUnits(
    page: number = 0,
    size: number = 10,
    search?: string,
    faculties?: Faculty[],
    level?: UnitLevel,
    sortBy?: string
  ): Observable<Page<UnitSummary>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    if (search) params = params.set('search', search);
    if (faculties && faculties.length > 0) {
      faculties.forEach(f => params = params.append('faculties', f));
    }
    if (level) params = params.set('level', level);
    if (sortBy) params = params.set('sortBy', sortBy);

    return this.http.get<Page<UnitSummary>>(this.apiUrl, { params });
  }

  getFaculties() {
    return Object.entries(FacultyDisplayNames).map(([value, label]) => ({ value: value as Faculty, label }));
  }

  getUnitLevels() {
    return Object.entries(UnitLevelDisplayNames).map(([value, label]) => ({ value: value as UnitLevel, label }));
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
