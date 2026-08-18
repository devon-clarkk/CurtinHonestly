import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, of, tap } from 'rxjs';
import { UnitCacheService } from './unit-cache.service';
import { Page, UnitSummary, UnitDetails, Review, Faculty, FacultyDisplayNames, UnitLevel, UnitLevelDisplayNames } from '../models/unit.model';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class UnitService {
  private http = inject(HttpClient);
  private cache = inject(UnitCacheService);
  // Tip for beginners: This is the URL where your backend is running.
  private apiUrl = `${environment.apiUrl}/units`;

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

    // Served from the tab's short-lived cache when it is still warm, so a
    // reload — or going into a unit and coming back — costs no request at all.
    // Returns synchronously on a hit, which is why a cached page never flashes
    // a spinner. Null on the server: SSR always fetches.
    //
    // Keyed on the query alone. Safe because a unit summary carries nothing
    // user-specific — if that ever changes, the key has to change with it.
    const key = params.toString();
    const cached = this.cache.read<Page<UnitSummary>>(key);
    if (cached) {
      return of(cached);
    }

    return this.http.get<Page<UnitSummary>>(this.apiUrl, { params })
      .pipe(tap(page => this.cache.write(key, page)));
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
