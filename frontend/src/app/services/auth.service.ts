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

export interface VerifyStudentRequest {
  studentEmail: string;
  password: string;
}

export interface JwtResponse {
  token: string;
  verifiedStudent: boolean;
}

export interface AccountStatus {
  verifiedStudent: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/auth`;

  isLoggedIn = signal<boolean>(this.hasStoredToken());
  verifiedStudent = signal<boolean>(this.getStoredVerifiedStudent());

  login(request: LoginRequest): Observable<JwtResponse> {
    return this.http.post<JwtResponse>(`${this.apiUrl}/login`, request).pipe(
      tap(response => this.persistSession(response.token, response.verifiedStudent))
    );
  }

  register(request: RegisterRequest): Observable<JwtResponse> {
    return this.http.post<JwtResponse>(`${this.apiUrl}/register`, request).pipe(
      tap(response => this.persistSession(response.token, response.verifiedStudent))
    );
  }

  verifyStudent(request: VerifyStudentRequest): Observable<JwtResponse> {
    return this.http.post<JwtResponse>(`${this.apiUrl}/verify-student`, request).pipe(
      tap(response => this.persistSession(response.token, response.verifiedStudent))
    );
  }

  refreshAccountStatus(): Observable<AccountStatus> {
    return this.http.get<AccountStatus>(`${this.apiUrl}/me`).pipe(
      tap(status => {
        localStorage.setItem('verified_student', String(status.verifiedStudent));
        this.verifiedStudent.set(status.verifiedStudent);
      })
    );
  }

  logout() {
    localStorage.removeItem('auth_token');
    localStorage.removeItem('verified_student');
    this.isLoggedIn.set(false);
    this.verifiedStudent.set(false);
  }

  private persistSession(token: string, verifiedStudent: boolean) {
    localStorage.setItem('auth_token', token);
    localStorage.setItem('verified_student', String(verifiedStudent));
    this.isLoggedIn.set(true);
    this.verifiedStudent.set(verifiedStudent);
  }

  private hasStoredToken(): boolean {
    if (typeof localStorage === 'undefined') {
      return false;
    }
    return !!localStorage.getItem('auth_token');
  }

  private getStoredVerifiedStudent(): boolean {
    if (typeof localStorage === 'undefined') {
      return false;
    }
    return localStorage.getItem('verified_student') === 'true';
  }
}
