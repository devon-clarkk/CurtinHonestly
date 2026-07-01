import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  AdminAnalytics,
  AdminOverview,
  AdminReview,
  PagedReviews,
  UserAdmin
} from '../models/admin.model';

@Injectable({ providedIn: 'root' })
export class AdminService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/admin`;

  getOverview(): Observable<AdminOverview> {
    return this.http.get<AdminOverview>(`${this.apiUrl}/stats/overview`);
  }

  getAnalytics(days = 30): Observable<AdminAnalytics> {
    return this.http.get<AdminAnalytics>(`${this.apiUrl}/stats/analytics`, {
      params: { days: String(days) }
    });
  }

  listUsers(): Observable<UserAdmin[]> {
    return this.http.get<UserAdmin[]>(`${this.apiUrl}/users`);
  }

  createUser(email: string, password: string, admin: boolean): Observable<UserAdmin> {
    return this.http.post<UserAdmin>(`${this.apiUrl}/users`, { email, password, admin });
  }

  banUser(id: string): Observable<UserAdmin> {
    return this.http.patch<UserAdmin>(`${this.apiUrl}/users/${id}/ban`, {});
  }

  unbanUser(id: string): Observable<UserAdmin> {
    return this.http.patch<UserAdmin>(`${this.apiUrl}/users/${id}/unban`, {});
  }

  deleteUser(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/users/${id}`);
  }

  listReviews(page = 0, size = 20): Observable<PagedReviews> {
    return this.http.get<PagedReviews>(`${this.apiUrl}/reviews`, {
      params: { page: String(page), size: String(size) }
    });
  }

  deleteReview(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/reviews/${id}`);
  }
}
