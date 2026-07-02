import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-account',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './account.component.html',
  styleUrl: './account.component.css'
})
export class AccountComponent implements OnInit {
  protected authService = inject(AuthService);
  private router = inject(Router);

  studentEmail = '';
  password = '';
  errorMessage = signal<string | null>(null);
  successMessage = signal<string | null>(null);
  isLoading = signal(false);

  ngOnInit() {
    if (!this.authService.isLoggedIn()) {
      this.router.navigate(['/login']);
      return;
    }

    this.authService.refreshAccountStatus().subscribe({
      error: () => this.authService.logout()
    });
  }

  onVerify() {
    if (!this.studentEmail.endsWith('@student.curtin.edu.au')) {
      this.errorMessage.set('Please enter a valid @student.curtin.edu.au email.');
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    this.authService.verifyStudent({
      studentEmail: this.studentEmail,
      password: this.password
    }).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.successMessage.set('Your account is now verified as a Curtin student.');
        this.studentEmail = '';
        this.password = '';
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(err.error?.error || 'Verification failed. Please try again.');
      }
    });
  }
}
