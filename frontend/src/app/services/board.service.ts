import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  BoardPage,
  BoardPost,
  BoardThreadDetail,
  BoardThreadSort,
  BoardThreadSummary,
  BoardUnitSummary
} from '../models/board.model';

@Injectable({
  providedIn: 'root'
})
export class BoardService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/boards`;

  // Reads (public)

  getGeneralThreads(page = 0, size = 20, sort: BoardThreadSort = 'activity'): Observable<BoardPage<BoardThreadSummary>> {
    return this.http.get<BoardPage<BoardThreadSummary>>(`${this.apiUrl}/general/threads`, {
      params: { page: String(page), size: String(size), sort }
    });
  }

  getUnitThreads(unitCode: string, page = 0, size = 20, sort: BoardThreadSort = 'activity'): Observable<BoardPage<BoardThreadSummary>> {
    return this.http.get<BoardPage<BoardThreadSummary>>(`${this.apiUrl}/units/${encodeURIComponent(unitCode)}/threads`, {
      params: { page: String(page), size: String(size), sort }
    });
  }

  getUnitSummary(unitCode: string): Observable<BoardUnitSummary> {
    return this.http.get<BoardUnitSummary>(`${this.apiUrl}/units/${encodeURIComponent(unitCode)}/summary`);
  }

  getRecent(limit = 10): Observable<BoardThreadSummary[]> {
    return this.http.get<BoardThreadSummary[]>(`${this.apiUrl}/recent`, { params: { limit: String(limit) } });
  }

  getThread(id: string, page = 0, size = 100): Observable<BoardThreadDetail> {
    return this.http.get<BoardThreadDetail>(`${this.apiUrl}/threads/${encodeURIComponent(id)}`, {
      params: { page: String(page), size: String(size) }
    });
  }

  // Writes (signed-in)

  createGeneralThread(title: string, body: string): Observable<BoardThreadDetail> {
    return this.http.post<BoardThreadDetail>(`${this.apiUrl}/general/threads`, { title, body });
  }

  createUnitThread(unitCode: string, title: string, body: string): Observable<BoardThreadDetail> {
    return this.http.post<BoardThreadDetail>(`${this.apiUrl}/units/${encodeURIComponent(unitCode)}/threads`, { title, body });
  }

  createPost(threadId: string, body: string): Observable<BoardPost> {
    return this.http.post<BoardPost>(`${this.apiUrl}/threads/${encodeURIComponent(threadId)}/posts`, { body });
  }

  updateThread(id: string, title: string, body: string): Observable<BoardThreadDetail> {
    return this.http.put<BoardThreadDetail>(`${this.apiUrl}/threads/${encodeURIComponent(id)}`, { title, body });
  }

  updatePost(id: string, body: string): Observable<BoardPost> {
    return this.http.put<BoardPost>(`${this.apiUrl}/posts/${encodeURIComponent(id)}`, { body });
  }

  deleteThread(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/threads/${encodeURIComponent(id)}`);
  }

  deletePost(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/posts/${encodeURIComponent(id)}`);
  }

  flagThread(id: string, reason: string | null): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/threads/${encodeURIComponent(id)}/flags`, { reason });
  }

  flagPost(id: string, reason: string | null): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/posts/${encodeURIComponent(id)}/flags`, { reason });
  }
}
