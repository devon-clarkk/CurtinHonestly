import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  imports: [FormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css'
})
export class LoginComponent {
  private http = inject(HttpClient);
  private auth = inject(AuthService);
  private router = inject(Router);

  email = '';
  password = '';
  errorMessage = signal<string | null>(null);
  isLoading = signal(false);

  onSubmit(): void {
    if (!this.email.endsWith('@student.curtin.edu.au')) {
      this.errorMessage.set('Only @student.curtin.edu.au emails are allowed.');
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.http
      .post<{ token: string }>(`${environment.apiUrl}/auth/login`, {
        email: this.email,
        password: this.password
      })
      .subscribe({
        next: (response) => {
          this.auth.saveToken(response.token, this.email);
          if (!this.auth.isAdmin()) {
            this.auth.logout();
            this.errorMessage.set('This account does not have admin access.');
            this.isLoading.set(false);
            return;
          }
          this.isLoading.set(false);
          this.router.navigate(['/']);
        },
        error: (err) => {
          this.isLoading.set(false);
          this.errorMessage.set(err.error?.error || 'Login failed.');
        }
      });
  }
}
