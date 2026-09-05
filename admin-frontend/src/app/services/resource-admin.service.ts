import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  ResourceOptions,
  ResourcePreview,
  ResourceReorderItem,
  ResourceStatus,
  UnitResourceAdmin,
  UnitResourceUpsert
} from '../models/resource-admin.model';

@Injectable({ providedIn: 'root' })
export class ResourceAdminService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/admin/unit-resources`;

  list(status?: ResourceStatus): Observable<UnitResourceAdmin[]> {
    const params = status ? new HttpParams().set('status', status) : undefined;
    return this.http.get<UnitResourceAdmin[]>(this.apiUrl, { params });
  }

  options(): Observable<ResourceOptions> {
    return this.http.get<ResourceOptions>(`${this.apiUrl}/options`);
  }

  preview(rule: {
    unitCode?: string | null;
    codePrefixes?: string | null;
    faculty?: string | null;
    level?: string | null;
  }): Observable<ResourcePreview> {
    let params = new HttpParams();
    if (rule.unitCode) params = params.set('unitCode', rule.unitCode);
    if (rule.codePrefixes) params = params.set('codePrefixes', rule.codePrefixes);
    if (rule.faculty) params = params.set('faculty', rule.faculty);
    if (rule.level) params = params.set('level', rule.level);
    return this.http.get<ResourcePreview>(`${this.apiUrl}/preview`, { params });
  }

  create(payload: UnitResourceUpsert): Observable<UnitResourceAdmin> {
    return this.http.post<UnitResourceAdmin>(this.apiUrl, payload);
  }

  update(id: string, payload: UnitResourceUpsert): Observable<UnitResourceAdmin> {
    return this.http.put<UnitResourceAdmin>(`${this.apiUrl}/${id}`, payload);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  approve(id: string): Observable<UnitResourceAdmin> {
    return this.http.post<UnitResourceAdmin>(`${this.apiUrl}/${id}/approve`, {});
  }

  reject(id: string): Observable<UnitResourceAdmin> {
    return this.http.post<UnitResourceAdmin>(`${this.apiUrl}/${id}/reject`, {});
  }

  reorder(items: ResourceReorderItem[]): Observable<void> {
    return this.http.put<void>(`${this.apiUrl}/reorder`, { items });
  }
}
