import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Page, UnitSummary } from '../models/unit.model';

@Injectable({
  providedIn: 'root'
})
export class UnitService {
  private http = inject(HttpClient);
  private apiUrl = 'http://localhost:8080/units';

  getUnits(page: number = 0, size: number = 10): Observable<Page<UnitSummary>> {
    return this.http.get<Page<UnitSummary>>(`${this.apiUrl}?page=${page}&size=${size}`);
  }
}
