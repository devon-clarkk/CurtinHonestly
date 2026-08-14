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

export interface MessageResponse {
  message: string;
}

export interface CampaignProgress {
  qualifyingReviews: number;
  requiredReviews: number;
  entriesEarned: number;
  maxEntries: number;
  requireVerifiedStudent: boolean;
  minLikesReceived: number;
  minLikesGiven: number;
  likesGiven: number;
}

export interface CampaignMembership {
  name: string;
  prizeDescription: string | null;
  endsAt: string | null;
  progress: CampaignProgress | null;
}

export interface AccountStatus {
  email: string;
  verifiedStudent: boolean;
  // A user can be enrolled in several campaigns at once (multiple draws per link).
  campaigns: CampaignMembership[];
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

  // Registering no longer returns a session. The endpoint answers identically
  // whether or not the address already had an account, so it cannot be used to
  // find out who has signed up (security audit finding #7), and a response that
  // carried a token in only one of those two cases would be exactly that tell.
  // Signup therefore completes as register-then-login: for a genuinely new
  // account the follow-up login succeeds with the password just chosen, so the
  // user still lands signed in from a single form submit.
  register(request: RegisterRequest): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.apiUrl}/register`, request);
  }

  // Requests a confirmation email to the student address. Verification only
  // completes when the emailed link is opened (confirmStudentVerification).
  verifyStudent(request: VerifyStudentRequest): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.apiUrl}/verify-student`, request);
  }

  // Join a campaign after signup with a referral link / campaign slug / promo code.
  // Returns the refreshed account (new campaigns + any credited entries).
  enrolInCampaign(code: string): Observable<AccountStatus> {
    return this.http.post<AccountStatus>(`${this.apiUrl}/me/campaigns`, { code });
  }

  // Completes verification from the emailed link; logs the user in as verified.
  // POST with the token in the body, never a GET query param: this call consumes
  // a single-use token and returns a session, and a token in a URL leaks through
  // Referer headers, browser history, and proxy logs (security audit finding #5).
  confirmStudentVerification(token: string): Observable<JwtResponse> {
    return this.http.post<JwtResponse>(`${this.apiUrl}/verify-student/confirm`, { token }).pipe(
      tap(response => this.persistSession(response.token, response.verifiedStudent, null))
    );
  }

  // Requests a password-reset email (enumeration-safe: same response either way).
  forgotPassword(email: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.apiUrl}/forgot-password`, { email });
  }

  // Completes a password reset from the emailed link.
  resetPassword(token: string, newPassword: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.apiUrl}/reset-password`, { token, newPassword });
  }

  updateEmail(request: UpdateEmailRequest): Observable<JwtResponse> {
    return this.http.patch<JwtResponse>(`${this.apiUrl}/me`, request).pipe(
      tap(response => this.persistSession(response.token, response.verifiedStudent, request.newEmail))
    );
  }

  // By default, deleting an account anonymizes (detaches) the user's reviews
  // rather than deleting them. Pass deleteReviews=true for full removal.
  deleteAccount(password: string, deleteReviews: boolean = false): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/me`, { body: { password, deleteReviews } });
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

  private persistSession(token: string, verifiedStudent: boolean, email: string | null) {
    localStorage.setItem('auth_token', token);
    localStorage.setItem('verified_student', String(verifiedStudent));
    this.isLoggedIn.set(true);
    this.verifiedStudent.set(verifiedStudent);
    if (email) {
      localStorage.setItem('user_email', email);
      this.email.set(email);
    }
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
