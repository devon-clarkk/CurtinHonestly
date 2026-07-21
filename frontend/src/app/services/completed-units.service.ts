import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class CompletedUnitsService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/users/me/completed-units`;

  getCompletedUnits(): Observable<string[]> {
    return this.http.get<string[]>(this.apiUrl);
  }

  updateCompletedUnits(unitCodes: string[]): Observable<string[]> {
    return this.http.put<string[]>(this.apiUrl, { unitCodes });
  }
}
