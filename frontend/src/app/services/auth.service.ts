import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';

export interface LoginRequest {
  email: string;
  password?: string;
}

export interface RegisterRequest {
  email: string;
  password?: string;
}

export interface JwtResponse {
  token: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/auth`;
  
  // Use a signal to track login status
  // Beginners: Signals are a great way to handle "state" that automatically updates the UI
  currentUser = signal<string | null>(this.getStoredEmail());

  login(request: LoginRequest): Observable<JwtResponse> {
    return this.http.post<JwtResponse>(`${this.apiUrl}/login`, request).pipe(
      tap(response => {
        this.saveToken(response.token, request.email);
      })
    );
  }

  register(request: RegisterRequest): Observable<any> {
    return this.http.post(`${this.apiUrl}/register`, request);
  }

  logout() {
    localStorage.removeItem('auth_token');
    localStorage.removeItem('user_email');
    this.currentUser.set(null);
  }

  private saveToken(token: string, email: string) {
    localStorage.setItem('auth_token', token);
    localStorage.setItem('user_email', email);
    this.currentUser.set(email);
  }

  private getStoredEmail(): string | null {
    if (typeof localStorage !== 'undefined') {
      return localStorage.getItem('user_email');
    }
    return null;
  }

  isLoggedIn(): boolean {
    return !!this.currentUser();
  }
}
