import { Injectable, computed, inject, signal } from '@angular/core';
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
  /** Current roles, e.g. ["ROLE_USER", "ROLE_CLUB"]. Fresher than the token's claim. */
  roles: string[];
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

  /**
   * Roles of the signed-in account ("ROLE_USER", "ROLE_CLUB", "ROLE_ADMIN").
   * Read from the JWT's roles claim at sign-in, then refreshed from /auth/me
   * whenever the account status is reloaded, so a club grant made after
   * sign-in shows up without a new session. Empty when signed out.
   */
  roles = signal<string[]>(this.getStoredRoles());
  isClubMember = computed(() => this.roles().includes('ROLE_CLUB'));
  isAdmin = computed(() => this.roles().includes('ROLE_ADMIN'));

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
        if (Array.isArray(status.roles)) {
          localStorage.setItem('user_roles', JSON.stringify(status.roles));
          this.roles.set(status.roles);
        }
      })
    );
  }

  logout() {
    localStorage.removeItem('auth_token');
    localStorage.removeItem('verified_student');
    localStorage.removeItem('user_email');
    localStorage.removeItem('user_roles');
    this.isLoggedIn.set(false);
    this.verifiedStudent.set(false);
    this.email.set(null);
    this.roles.set([]);
  }

  private persistSession(token: string, verifiedStudent: boolean, email: string | null) {
    localStorage.setItem('auth_token', token);
    localStorage.setItem('verified_student', String(verifiedStudent));
    // A fresh token carries the account's current roles; drop any older
    // /auth/me snapshot so the two cannot disagree.
    localStorage.removeItem('user_roles');
    this.isLoggedIn.set(true);
    this.verifiedStudent.set(verifiedStudent);
    this.roles.set(AuthService.rolesFromToken(token));
    if (email) {
      localStorage.setItem('user_email', email);
      this.email.set(email);
    }
  }

  /**
   * The roles claim of a JWT, decoded without a library: the payload is the
   * middle base64url segment. The signature is not checked here; the API
   * checks it on every request and reads roles from the database, so a
   * tampered claim only changes what the nav shows, never what is allowed.
   */
  static rolesFromToken(token: string | null): string[] {
    if (!token) {
      return [];
    }
    try {
      const segment = token.split('.')[1] ?? '';
      const base64 = segment.replace(/-/g, '+').replace(/_/g, '/');
      const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
      // atob exists in every browser and in Node 16+, which covers SSR too.
      if (typeof atob !== 'function') {
        return [];
      }
      const payload = JSON.parse(atob(padded)) as { roles?: unknown };
      return Array.isArray(payload.roles) ? payload.roles.filter((r): r is string => typeof r === 'string') : [];
    } catch {
      return [];
    }
  }

  private getStoredRoles(): string[] {
    if (typeof localStorage === 'undefined') {
      return [];
    }
    const snapshot = localStorage.getItem('user_roles');
    if (snapshot) {
      try {
        const parsed = JSON.parse(snapshot) as unknown;
        if (Array.isArray(parsed)) {
          return parsed.filter((r): r is string => typeof r === 'string');
        }
      } catch {
        // Fall through to the token.
      }
    }
    return AuthService.rolesFromToken(localStorage.getItem('auth_token'));
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
