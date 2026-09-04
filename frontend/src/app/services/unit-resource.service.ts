import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { UnitResource, UnitResourceList, UnitResourceSuggestion } from '../models/unit-resource.model';

@Injectable({
  providedIn: 'root'
})
export class UnitResourceService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/units`;

  list(unitCode: string): Observable<UnitResourceList> {
    return this.http.get<UnitResourceList>(`${this.apiUrl}/${encodeURIComponent(unitCode)}/resources`);
  }

  /** Click beacon. Public and fire-and-forget; the caller subscribes and ignores the result. */
  click(unitCode: string, resourceId: string): Observable<void> {
    return this.http.post<void>(
      `${this.apiUrl}/${encodeURIComponent(unitCode)}/resources/${encodeURIComponent(resourceId)}/clicks`,
      {}
    );
  }

  /** Signed-in students only. The suggestion lands in the admin queue as PENDING. */
  suggest(unitCode: string, suggestion: UnitResourceSuggestion): Observable<UnitResource> {
    return this.http.post<UnitResource>(
      `${this.apiUrl}/${encodeURIComponent(unitCode)}/resources/suggestions`,
      suggestion
    );
  }
}
