import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../../services/admin.service';
import { AdminNotificationEventSetting, AdminNotificationSettings } from '../../models/admin.model';

@Component({
  selector: 'app-notifications',
  imports: [FormsModule],
  templateUrl: './notifications.component.html',
  styleUrl: './notifications.component.css'
})
export class NotificationsComponent implements OnInit {
  private adminService = inject(AdminService);

  settings = signal<AdminNotificationSettings | null>(null);
  loading = signal(true);
  saving = signal(false);
  testing = signal(false);
  errorMessage = signal<string | null>(null);
  successMessage = signal<string | null>(null);

  // Editable copy of what the server sent. Saving sends the whole set back.
  recipientEmail = '';
  events = signal<AdminNotificationEventSetting[]>([]);

  enabledCount = computed(() => this.events().filter((e) => e.enabled).length);

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.loading.set(true);
    this.adminService.getNotificationSettings().subscribe({
      next: (data) => {
        this.apply(data);
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('Failed to load notification settings.');
        this.loading.set(false);
      }
    });
  }

  toggle(event: AdminNotificationEventSetting): void {
    this.events.update((list) => list.map((e) => (e.event === event.event ? { ...e, enabled: !e.enabled } : e)));
  }

  save(): void {
    this.clearMessages();
    this.saving.set(true);
    const enabledEvents = this.events().filter((e) => e.enabled).map((e) => e.event);
    this.adminService.updateNotificationSettings({ recipientEmail: this.recipientEmail.trim(), enabledEvents }).subscribe({
      next: (data) => {
        this.apply(data);
        this.saving.set(false);
        this.successMessage.set('Notification settings saved.');
      },
      error: (err) => {
        this.saving.set(false);
        this.errorMessage.set(err.error?.error || 'Failed to save notification settings.');
      }
    });
  }

  // Sends to whatever the server currently holds, not the unsaved form, so the
  // hint next to the button says to save first.
  sendTest(): void {
    this.clearMessages();
    this.testing.set(true);
    this.adminService.sendTestNotification().subscribe({
      next: (result) => {
        this.testing.set(false);
        this.successMessage.set(
          this.settings()?.mailConfigured
            ? `Test email sent to ${result.sentTo}.`
            : `Test alert for ${result.sentTo} was written to the backend log (SMTP is not configured here).`
        );
      },
      error: (err) => {
        this.testing.set(false);
        this.errorMessage.set(err.error?.error || 'Failed to send the test email.');
      }
    });
  }

  private apply(data: AdminNotificationSettings): void {
    this.settings.set(data);
    this.recipientEmail = data.recipientEmail ?? '';
    this.events.set(data.events.map((e) => ({ ...e })));
  }

  private clearMessages(): void {
    this.errorMessage.set(null);
    this.successMessage.set(null);
  }
}
