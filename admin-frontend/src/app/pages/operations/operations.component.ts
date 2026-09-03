import { Component, computed, HostListener, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../../services/admin.service';
import { AdminReview, FlaggedReviewAdmin, UnitRequestAdmin, UserAdmin } from '../../models/admin.model';
import { tagLabel, termLabel } from '../../utils/labels';

const REVIEW_PAGE_SIZE = 20;
const EXCERPT_LENGTH = 110;

@Component({
  selector: 'app-operations',
  imports: [FormsModule, DatePipe],
  templateUrl: './operations.component.html',
  styleUrl: './operations.component.css'
})
export class OperationsComponent implements OnInit {
  private adminService = inject(AdminService);

  users = signal<UserAdmin[]>([]);
  userFilter = signal('');
  filteredUsers = computed(() => {
    const needle = this.userFilter().trim().toLowerCase();
    const users = this.users();
    return needle ? users.filter((u) => u.email.toLowerCase().includes(needle)) : users;
  });

  reviews = signal<AdminReview[]>([]);
  reviewPage = signal(0);
  reviewTotalPages = signal(0);
  reviewTotalElements = signal(0);
  selectedReview = signal<AdminReview | null>(null);
  reviewDetailLoading = signal(false);

  unitRequests = signal<UnitRequestAdmin[]>([]);
  flaggedReviews = signal<FlaggedReviewAdmin[]>([]);
  errorMessage = signal<string | null>(null);
  successMessage = signal<string | null>(null);

  newEmail = '';
  newPassword = '';
  newUserIsAdmin = false;

  readonly tagLabel = tagLabel;
  readonly termLabel = termLabel;

  ngOnInit(): void {
    this.refreshUsers();
    this.refreshReviews();
    this.refreshUnitRequests();
    this.refreshFlaggedReviews();
  }

  // Users

  refreshUsers(): void {
    this.adminService.listUsers().subscribe({
      next: (data) => this.users.set(data),
      error: () => this.errorMessage.set('Failed to load users.')
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

  toggleVerified(user: UserAdmin): void {
    const question = user.verifiedStudent
      ? `Remove verified status from ${user.email}?`
      : `Mark ${user.email} as a verified student? Use this when the verification email did not arrive.`;
    if (!confirm(question)) return;
    this.clearMessages();
    const action = user.verifiedStudent
      ? this.adminService.unverifyUser(user.id)
      : this.adminService.verifyUser(user.id);
    action.subscribe({
      next: (updated) => {
        this.successMessage.set(updated.verifiedStudent ? 'User marked as verified.' : 'Verified status removed.');
        this.replaceUser(updated);
      },
      error: () => this.errorMessage.set('Failed to update verification status.')
    });
  }

  toggleBan(user: UserAdmin): void {
    this.clearMessages();
    const action = user.banned ? this.adminService.unbanUser(user.id) : this.adminService.banUser(user.id);
    action.subscribe({
      next: (updated) => {
        this.successMessage.set(updated.banned ? 'User banned.' : 'User unbanned.');
        this.replaceUser(updated);
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

  isAdmin(user: UserAdmin): boolean {
    return user.roles.includes('ROLE_ADMIN');
  }

  // Reviews

  refreshReviews(page = this.reviewPage()): void {
    this.adminService.listReviews(page, REVIEW_PAGE_SIZE).subscribe({
      next: (result) => {
        this.reviews.set(result.content);
        this.reviewPage.set(result.number);
        this.reviewTotalPages.set(result.totalPages);
        this.reviewTotalElements.set(result.totalElements);
      },
      error: () => this.errorMessage.set('Failed to load reviews.')
    });
  }

  previousReviewPage(): void {
    if (this.reviewPage() > 0) this.refreshReviews(this.reviewPage() - 1);
  }

  nextReviewPage(): void {
    if (this.reviewPage() + 1 < this.reviewTotalPages()) this.refreshReviews(this.reviewPage() + 1);
  }

  // Show the row we already have straight away, then refresh it from the
  // detail endpoint so flag and like counts are current.
  openReview(review: AdminReview): void {
    this.selectedReview.set(review);
    this.reviewDetailLoading.set(true);
    this.adminService.getReview(review.id).subscribe({
      next: (fresh) => {
        if (this.selectedReview()?.id === fresh.id) this.selectedReview.set(fresh);
        this.reviewDetailLoading.set(false);
      },
      error: () => this.reviewDetailLoading.set(false)
    });
  }

  openReviewById(reviewId: string): void {
    this.reviewDetailLoading.set(true);
    this.adminService.getReview(reviewId).subscribe({
      next: (review) => {
        this.selectedReview.set(review);
        this.reviewDetailLoading.set(false);
      },
      error: () => {
        this.reviewDetailLoading.set(false);
        this.errorMessage.set('Failed to load that review.');
      }
    });
  }

  onReviewRowKeydown(event: KeyboardEvent, review: AdminReview): void {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      this.openReview(review);
    }
  }

  closeReview(): void {
    this.selectedReview.set(null);
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.selectedReview()) this.closeReview();
  }

  deleteReview(review: AdminReview): void {
    if (!confirm(`Delete review for ${review.unitCode}?`)) return;
    this.clearMessages();
    this.adminService.deleteReview(review.id).subscribe({
      next: () => {
        this.successMessage.set('Review deleted.');
        if (this.selectedReview()?.id === review.id) this.closeReview();
        this.refreshReviews();
        this.refreshFlaggedReviews();
      },
      error: () => this.errorMessage.set('Failed to delete review.')
    });
  }

  excerpt(text: string | null): string {
    if (!text) return '';
    const collapsed = text.replace(/\s+/g, ' ').trim();
    return collapsed.length > EXCERPT_LENGTH ? `${collapsed.slice(0, EXCERPT_LENGTH - 1).trim()}...` : collapsed;
  }

  stars(rating: number): string {
    return '★'.repeat(rating) + '☆'.repeat(Math.max(0, 5 - rating));
  }

  unitUrl(code: string): string {
    return `https://www.curtinhonestly.com/units/${encodeURIComponent(code)}`;
  }

  // Unit requests

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

  // Flags

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
        this.successMessage.set('Flags dismissed, review kept.');
        this.refreshFlaggedReviews();
      },
      error: () => this.errorMessage.set('Failed to dismiss flags.')
    });
  }

  private replaceUser(updated: UserAdmin): void {
    this.users.update((users) => users.map((u) => (u.id === updated.id ? { ...u, ...updated } : u)));
  }

  private clearMessages(): void {
    this.errorMessage.set(null);
    this.successMessage.set(null);
  }
}
