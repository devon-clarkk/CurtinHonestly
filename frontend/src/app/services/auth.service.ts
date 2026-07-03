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
  ref?: string;
  promoCode?: string;
}

export interface VerifyStudentRequest {
  studentEmail: string;
  password: string;
}

export interface UpdateEmailRequest {
  newEmail: string;
  password: string;
}

export interface JwtResponse {
  token: string;
  verifiedStudent: boolean;
}

export interface CampaignProgress {
  qualifyingReviews: number;
  requiredReviews: number;
  entriesEarned: number;
  maxEntries: number;
  requireVerifiedStudent: boolean;
}

export interface AccountStatus {
  email: string;
  verifiedStudent: boolean;
  campaignName: string | null;
  campaignPrizeDescription: string | null;
  campaignEndsAt: string | null;
  campaignProgress: CampaignProgress | null;
  campaignEntries: CampaignEntrySummary[];
}

export interface CampaignEntrySummary {
  entryToken: string;
  campaignName: string;
  unitCode: string;
  createdAt: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/auth`;

  isLoggedIn = signal<boolean>(this.hasStoredToken());
  verifiedStudent = signal<boolean>(this.getStoredVerifiedStudent());
  email = signal<string | null>(this.getStoredEmail());

  login(request: LoginRequest): Observable<JwtResponse> {
    return this.http.post<JwtResponse>(`${this.apiUrl}/login`, request).pipe(
      tap(response => this.persistSession(response.token, response.verifiedStudent, request.email))
    );
  }

  register(request: RegisterRequest): Observable<JwtResponse> {
    return this.http.post<JwtResponse>(`${this.apiUrl}/register`, request).pipe(
      tap(response => this.persistSession(response.token, response.verifiedStudent, request.email))
    );
  }

  verifyStudent(request: VerifyStudentRequest): Observable<JwtResponse> {
    return this.http.post<JwtResponse>(`${this.apiUrl}/verify-student`, request).pipe(
      tap(response => {
        this.persistSession(response.token, response.verifiedStudent, request.studentEmail);
      })
    );
  }

  updateEmail(request: UpdateEmailRequest): Observable<JwtResponse> {
    return this.http.patch<JwtResponse>(`${this.apiUrl}/me`, request).pipe(
      tap(response => this.persistSession(response.token, response.verifiedStudent, request.newEmail))
    );
  }

  deleteAccount(password: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/me`, { body: { password } });
  }

  refreshAccountStatus(): Observable<AccountStatus> {
    return this.http.get<AccountStatus>(`${this.apiUrl}/me`).pipe(
      tap(status => {
        localStorage.setItem('verified_student', String(status.verifiedStudent));
        localStorage.setItem('user_email', status.email);
        this.verifiedStudent.set(status.verifiedStudent);
        this.email.set(status.email);
      })
    );
  }

  logout() {
    localStorage.removeItem('auth_token');
    localStorage.removeItem('verified_student');
    localStorage.removeItem('user_email');
    this.isLoggedIn.set(false);
    this.verifiedStudent.set(false);
    this.email.set(null);
  }

  private persistSession(token: string, verifiedStudent: boolean, email: string) {
    localStorage.setItem('auth_token', token);
    localStorage.setItem('verified_student', String(verifiedStudent));
    localStorage.setItem('user_email', email);
    this.isLoggedIn.set(true);
    this.verifiedStudent.set(verifiedStudent);
    this.email.set(email);
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

  private getStoredEmail(): string | null {
    if (typeof localStorage === 'undefined') {
      return null;
    }
    return localStorage.getItem('user_email');
  }
}
