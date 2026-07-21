import { Component, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../../services/admin.service';
import { AdminReview, FlaggedReviewAdmin, UnitRequestAdmin, UserAdmin } from '../../models/admin.model';

@Component({
  selector: 'app-operations',
  imports: [FormsModule, DatePipe],
  templateUrl: './operations.component.html',
  styleUrl: './operations.component.css'
})
export class OperationsComponent implements OnInit {
  private adminService = inject(AdminService);

  users = signal<UserAdmin[]>([]);
  reviews = signal<AdminReview[]>([]);
  unitRequests = signal<UnitRequestAdmin[]>([]);
  flaggedReviews = signal<FlaggedReviewAdmin[]>([]);
  errorMessage = signal<string | null>(null);
  successMessage = signal<string | null>(null);

  newEmail = '';
  newPassword = '';
  newUserIsAdmin = false;

  ngOnInit(): void {
    this.refreshUsers();
    this.refreshReviews();
    this.refreshUnitRequests();
    this.refreshFlaggedReviews();
  }

  refreshUsers(): void {
    this.adminService.listUsers().subscribe({
      next: (data) => this.users.set(data),
      error: () => this.errorMessage.set('Failed to load users.')
    });
  }

  refreshReviews(): void {
    this.adminService.listReviews().subscribe({
      next: (page) => this.reviews.set(page.content),
      error: () => this.errorMessage.set('Failed to load reviews.')
    });
  }

  createUser(): void {
    this.clearMessages();
    this.adminService.createUser(this.newEmail, this.newPassword, this.newUserIsAdmin).subscribe({
      next: () => {
        this.successMessage.set('User created.');
        this.newEmail = '';
        this.newPassword = '';
        this.newUserIsAdmin = false;
        this.refreshUsers();
      },
      error: (err) => this.errorMessage.set(err.error?.error || 'Failed to create user.')
    });
  }

  toggleBan(user: UserAdmin): void {
    this.clearMessages();
    const action = user.banned ? this.adminService.unbanUser(user.id) : this.adminService.banUser(user.id);
    action.subscribe({
      next: () => {
        this.successMessage.set(user.banned ? 'User unbanned.' : 'User banned.');
        this.refreshUsers();
      },
      error: () => this.errorMessage.set('Failed to update user ban status.')
    });
  }

  deleteUser(user: UserAdmin): void {
    if (!confirm(`Delete ${user.email}? This cannot be undone.`)) return;
    this.clearMessages();
    this.adminService.deleteUser(user.id).subscribe({
      next: () => {
        this.successMessage.set('User deleted.');
        this.refreshUsers();
      },
      error: () => this.errorMessage.set('Failed to delete user.')
    });
  }

  deleteReview(review: AdminReview): void {
    if (!confirm(`Delete review for ${review.unitCode}?`)) return;
    this.clearMessages();
    this.adminService.deleteReview(review.id).subscribe({
      next: () => {
        this.successMessage.set('Review deleted.');
        this.refreshReviews();
      },
      error: () => this.errorMessage.set('Failed to delete review.')
    });
  }

  refreshUnitRequests(): void {
    this.adminService.listUnitRequests().subscribe({
      next: (data) => this.unitRequests.set(data),
      error: () => this.errorMessage.set('Failed to load unit requests.')
    });
  }

  deleteUnitRequest(request: UnitRequestAdmin): void {
    this.clearMessages();
    this.adminService.deleteUnitRequest(request.id).subscribe({
      next: () => {
        this.successMessage.set('Unit request dismissed.');
        this.refreshUnitRequests();
      },
      error: () => this.errorMessage.set('Failed to dismiss unit request.')
    });
  }

  refreshFlaggedReviews(): void {
    this.adminService.listFlaggedReviews().subscribe({
      next: (data) => this.flaggedReviews.set(data),
      error: () => this.errorMessage.set('Failed to load flagged reviews.')
    });
  }

  deleteFlaggedReview(flagged: FlaggedReviewAdmin): void {
    if (!confirm(`Delete review for ${flagged.unitCode}?`)) return;
    this.clearMessages();
    this.adminService.deleteReview(flagged.reviewId).subscribe({
      next: () => {
        this.successMessage.set('Review deleted.');
        this.refreshFlaggedReviews();
        this.refreshReviews();
      },
      error: () => this.errorMessage.set('Failed to delete review.')
    });
  }

  dismissFlags(flagged: FlaggedReviewAdmin): void {
    this.clearMessages();
    this.adminService.dismissReviewFlags(flagged.reviewId).subscribe({
      next: () => {
        this.successMessage.set('Flags dismissed — review kept.');
        this.refreshFlaggedReviews();
      },
      error: () => this.errorMessage.set('Failed to dismiss flags.')
    });
  }

  isAdmin(user: UserAdmin): boolean {
    return user.roles.includes('ROLE_ADMIN');
  }

  private clearMessages(): void {
    this.errorMessage.set(null);
    this.successMessage.set(null);
  }
}
