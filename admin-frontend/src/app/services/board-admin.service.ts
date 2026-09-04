import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  BoardAdminFlaggedItem,
  BoardAdminPage,
  BoardAdminPost,
  BoardAdminThread
} from '../models/board-admin.model';

@Injectable({ providedIn: 'root' })
export class BoardAdminService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/admin/boards`;

  listFlagged(): Observable<BoardAdminFlaggedItem[]> {
    return this.http.get<BoardAdminFlaggedItem[]>(`${this.apiUrl}/flags`);
  }

  listThreads(page = 0, size = 20): Observable<BoardAdminPage<BoardAdminThread>> {
    return this.http.get<BoardAdminPage<BoardAdminThread>>(`${this.apiUrl}/threads`, {
      params: { page: String(page), size: String(size) }
    });
  }

  listPosts(page = 0, size = 20): Observable<BoardAdminPage<BoardAdminPost>> {
    return this.http.get<BoardAdminPage<BoardAdminPost>>(`${this.apiUrl}/posts`, {
      params: { page: String(page), size: String(size) }
    });
  }

  lockThread(id: string): Observable<BoardAdminThread> {
    return this.http.patch<BoardAdminThread>(`${this.apiUrl}/threads/${id}/lock`, {});
  }

  unlockThread(id: string): Observable<BoardAdminThread> {
    return this.http.patch<BoardAdminThread>(`${this.apiUrl}/threads/${id}/unlock`, {});
  }

  pinThread(id: string): Observable<BoardAdminThread> {
    return this.http.patch<BoardAdminThread>(`${this.apiUrl}/threads/${id}/pin`, {});
  }

  unpinThread(id: string): Observable<BoardAdminThread> {
    return this.http.patch<BoardAdminThread>(`${this.apiUrl}/threads/${id}/unpin`, {});
  }

  // Remove = soft delete plus clear flags.
  removeThread(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/threads/${id}`);
  }

  removePost(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/posts/${id}`);
  }

  dismissThreadFlags(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/threads/${id}/flags`);
  }

  dismissPostFlags(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/posts/${id}/flags`);
  }
}
