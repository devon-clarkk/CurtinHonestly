import { Injectable, signal } from '@angular/core';

interface JwtPayload {
  sub?: string;
  roles?: string[];
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly tokenKey = 'admin_auth_token';
  private readonly emailKey = 'admin_user_email';

  currentUser = signal<string | null>(this.getStoredEmail());
  isAdmin = signal(this.hasAdminRole());

  saveToken(token: string, email: string): void {
    localStorage.setItem(this.tokenKey, token);
    localStorage.setItem(this.emailKey, email);
    this.currentUser.set(email);
    this.isAdmin.set(this.hasAdminRole());
  }

  logout(): void {
    localStorage.removeItem(this.tokenKey);
    localStorage.removeItem(this.emailKey);
    this.currentUser.set(null);
    this.isAdmin.set(false);
  }

  getToken(): string | null {
    if (typeof localStorage === 'undefined') return null;
    return localStorage.getItem(this.tokenKey);
  }

  isLoggedIn(): boolean {
    return !!this.getToken();
  }

  private getStoredEmail(): string | null {
    if (typeof localStorage === 'undefined') return null;
    return localStorage.getItem(this.emailKey);
  }

  private hasAdminRole(): boolean {
    const payload = this.decodeToken();
    return payload?.roles?.includes('ROLE_ADMIN') ?? false;
  }

  private decodeToken(): JwtPayload | null {
    const token = this.getToken();
    if (!token) return null;
    try {
      const body = token.split('.')[1];
      const json = atob(body.replace(/-/g, '+').replace(/_/g, '/'));
      return JSON.parse(json) as JwtPayload;
    } catch {
      return null;
    }
  }
}
