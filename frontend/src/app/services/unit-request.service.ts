import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface UnitRequestResponse {
  id: string;
  requestedCode: string;
  note: string | null;
  createdAt: string;
}

@Injectable({
  providedIn: 'root'
})
export class UnitRequestService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/unit-requests`;

  requestUnit(requestedCode: string, note?: string): Observable<UnitRequestResponse> {
    return this.http.post<UnitRequestResponse>(this.apiUrl, { requestedCode, note });
  }
}
