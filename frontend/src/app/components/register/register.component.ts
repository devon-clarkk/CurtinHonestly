import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './register.component.html',
  styleUrl: './register.component.css'
})
export class RegisterComponent {
  private authService = inject(AuthService);
  private router = inject(Router);

  email = '';
  password = '';
  confirmPassword = '';
  errorMessage = signal<string | null>(null);
  successMessage = signal<string | null>(null);
  isLoading = signal(false);

  onSubmit() {
    if (this.password !== this.confirmPassword) {
      this.errorMessage.set('Passwords do not match.');
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.authService.register({ email: this.email, password: this.password }).subscribe({
      next: (response) => {
        this.isLoading.set(false);
        const message = response.verifiedStudent
          ? 'Registration successful! You are verified as a Curtin student.'
          : 'Registration successful! Verify your student email from your account to show the verified badge on reviews.';
        this.successMessage.set(message);
        setTimeout(() => this.router.navigate(['/']), 2000);
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(err.error?.error || 'Registration failed. Please try again.');
      }
    });
  }
}
