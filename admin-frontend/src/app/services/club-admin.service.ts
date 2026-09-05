import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  AdminClub,
  AdminClubMemberRequest,
  AdminClubUpsert,
  ClubEventManage,
  ClubEventOptions,
  ClubEventPreview,
  ClubEventStatus,
  ClubEventUpsert
} from '../models/club-admin.model';

@Injectable({ providedIn: 'root' })
export class ClubAdminService {
  private http = inject(HttpClient);
  private clubsUrl = `${environment.apiUrl}/admin/clubs`;
  private eventsUrl = `${environment.apiUrl}/admin/club-events`;

  // Clubs and members

  listClubs(): Observable<AdminClub[]> {
    return this.http.get<AdminClub[]>(this.clubsUrl);
  }

  createClub(payload: AdminClubUpsert): Observable<AdminClub> {
    return this.http.post<AdminClub>(this.clubsUrl, payload);
  }

  updateClub(id: string, payload: AdminClubUpsert): Observable<AdminClub> {
    return this.http.put<AdminClub>(`${this.clubsUrl}/${id}`, payload);
  }

  deleteClub(id: string): Observable<void> {
    return this.http.delete<void>(`${this.clubsUrl}/${id}`);
  }

  /** 404 with a clear message when no account exists for the email yet. */
  addMember(clubId: string, request: AdminClubMemberRequest): Observable<AdminClub> {
    return this.http.post<AdminClub>(`${this.clubsUrl}/${clubId}/members`, request);
  }

  setMemberRole(clubId: string, userId: string, role: string): Observable<AdminClub> {
    return this.http.put<AdminClub>(`${this.clubsUrl}/${clubId}/members/${userId}`, { role });
  }

  removeMember(clubId: string, userId: string): Observable<AdminClub> {
    return this.http.delete<AdminClub>(`${this.clubsUrl}/${clubId}/members/${userId}`);
  }

  // Events

  listEvents(status?: ClubEventStatus | null, clubId?: string | null): Observable<ClubEventManage[]> {
    let params = new HttpParams();
    if (status) params = params.set('status', status);
    if (clubId) params = params.set('clubId', clubId);
    return this.http.get<ClubEventManage[]>(this.eventsUrl, { params });
  }

  options(): Observable<ClubEventOptions> {
    return this.http.get<ClubEventOptions>(`${this.eventsUrl}/options`);
  }

  preview(rule: {
    unitCode?: string | null;
    codePrefixes?: string | null;
    faculty?: string | null;
    level?: string | null;
  }): Observable<ClubEventPreview> {
    let params = new HttpParams();
    if (rule.unitCode) params = params.set('unitCode', rule.unitCode);
    if (rule.codePrefixes) params = params.set('codePrefixes', rule.codePrefixes);
    if (rule.faculty) params = params.set('faculty', rule.faculty);
    if (rule.level) params = params.set('level', rule.level);
    return this.http.get<ClubEventPreview>(`${this.eventsUrl}/preview`, { params });
  }

  /** Admin-created events publish immediately. */
  createEvent(clubId: string, payload: ClubEventUpsert): Observable<ClubEventManage> {
    return this.http.post<ClubEventManage>(`${this.clubsUrl}/${clubId}/events`, payload);
  }

  updateEvent(id: string, payload: ClubEventUpsert): Observable<ClubEventManage> {
    return this.http.put<ClubEventManage>(`${this.eventsUrl}/${id}`, payload);
  }

  approveEvent(id: string): Observable<ClubEventManage> {
    return this.http.post<ClubEventManage>(`${this.eventsUrl}/${id}/approve`, {});
  }

  rejectEvent(id: string, reason: string): Observable<ClubEventManage> {
    return this.http.post<ClubEventManage>(`${this.eventsUrl}/${id}/reject`, { reason });
  }

  cancelEvent(id: string): Observable<ClubEventManage> {
    return this.http.post<ClubEventManage>(`${this.eventsUrl}/${id}/cancel`, {});
  }

  deleteEvent(id: string): Observable<void> {
    return this.http.delete<void>(`${this.eventsUrl}/${id}`);
  }
}
