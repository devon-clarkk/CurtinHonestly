import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  ClubEvent,
  ClubEventManage,
  ClubEventOptions,
  ClubEventPage,
  ClubEventPreview,
  ClubEventUpsert,
  ClubPortalClub,
  ClubProfile,
  ClubProfileUpdate,
  ClubSummary
} from '../models/club-event.model';

@Injectable({
  providedIn: 'root'
})
export class ClubEventService {
  private http = inject(HttpClient);
  private apiUrl = environment.apiUrl;

  // Reads (public)

  /** Home page strip: up to `limit` upcoming events flagged for home, soonest first. */
  upcoming(limit = 4): Observable<ClubEvent[]> {
    return this.http.get<ClubEvent[]>(`${this.apiUrl}/events/upcoming`, { params: { limit: String(limit) } });
  }

  /** Unit page card: upcoming events whose targeting covers the unit. */
  forUnit(unitCode: string): Observable<ClubEvent[]> {
    return this.http.get<ClubEvent[]>(`${this.apiUrl}/units/${encodeURIComponent(unitCode)}/events`);
  }

  /** Whether any upcoming published event exists, for the Events nav entry. One page of one. */
  hasUpcoming(): Observable<boolean> {
    return this.list(0, 1).pipe(map((page) => page.totalElements > 0 || page.content.length > 0));
  }

  list(page = 0, size = 20, clubSlug?: string | null, kind?: string | null): Observable<ClubEventPage<ClubEvent>> {
    let params = new HttpParams().set('page', String(page)).set('size', String(size));
    if (clubSlug) params = params.set('clubSlug', clubSlug);
    if (kind) params = params.set('kind', kind);
    return this.http.get<ClubEventPage<ClubEvent>>(`${this.apiUrl}/events`, { params });
  }

  get(id: string): Observable<ClubEvent> {
    return this.http.get<ClubEvent>(`${this.apiUrl}/events/${encodeURIComponent(id)}`);
  }

  /** View beacon. Public and fire-and-forget; the caller subscribes and ignores the result. */
  recordView(id: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/events/${encodeURIComponent(id)}/views`, {});
  }

  clubs(): Observable<ClubSummary[]> {
    return this.http.get<ClubSummary[]>(`${this.apiUrl}/clubs`);
  }

  club(slug: string): Observable<ClubProfile> {
    return this.http.get<ClubProfile>(`${this.apiUrl}/clubs/${encodeURIComponent(slug)}`);
  }

  // Club portal (signed-in club members and admins)

  myClubs(): Observable<ClubPortalClub[]> {
    return this.http.get<ClubPortalClub[]>(`${this.apiUrl}/club/me`);
  }

  options(): Observable<ClubEventOptions> {
    return this.http.get<ClubEventOptions>(`${this.apiUrl}/club/options`);
  }

  updateClub(clubId: string, update: ClubProfileUpdate): Observable<ClubPortalClub> {
    return this.http.put<ClubPortalClub>(`${this.apiUrl}/club/${encodeURIComponent(clubId)}`, update);
  }

  clubEvents(clubId: string): Observable<ClubEventManage[]> {
    return this.http.get<ClubEventManage[]>(`${this.apiUrl}/club/${encodeURIComponent(clubId)}/events`);
  }

  preview(clubId: string, rule: {
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
    return this.http.get<ClubEventPreview>(`${this.apiUrl}/club/${encodeURIComponent(clubId)}/events/preview`, { params });
  }

  createEvent(clubId: string, payload: ClubEventUpsert): Observable<ClubEventManage> {
    return this.http.post<ClubEventManage>(`${this.apiUrl}/club/${encodeURIComponent(clubId)}/events`, payload);
  }

  updateEvent(clubId: string, id: string, payload: ClubEventUpsert): Observable<ClubEventManage> {
    return this.http.put<ClubEventManage>(this.eventUrl(clubId, id), payload);
  }

  publishEvent(clubId: string, id: string): Observable<ClubEventManage> {
    return this.http.post<ClubEventManage>(`${this.eventUrl(clubId, id)}/publish`, {});
  }

  cancelEvent(clubId: string, id: string): Observable<ClubEventManage> {
    return this.http.post<ClubEventManage>(`${this.eventUrl(clubId, id)}/cancel`, {});
  }

  deleteEvent(clubId: string, id: string): Observable<void> {
    return this.http.delete<void>(this.eventUrl(clubId, id));
  }

  private eventUrl(clubId: string, id: string): string {
    return `${this.apiUrl}/club/${encodeURIComponent(clubId)}/events/${encodeURIComponent(id)}`;
  }
}
