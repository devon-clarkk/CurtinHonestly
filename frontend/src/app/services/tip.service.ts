import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Tip } from '../models/unit.model';

@Injectable({
  providedIn: 'root'
})
export class TipService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/units`;

  getTips(unitCode: string): Observable<Tip[]> {
    return this.http.get<Tip[]>(`${this.apiUrl}/${unitCode}/tips`);
  }

  createTip(unitCode: string, text: string): Observable<Tip> {
    return this.http.post<Tip>(`${this.apiUrl}/${unitCode}/tips`, { text });
  }

  deleteTip(unitCode: string, tipId: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${unitCode}/tips/${tipId}`);
  }
}
