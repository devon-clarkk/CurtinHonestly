import { Component, inject, signal } from '@angular/core';
import { RouterOutlet, RouterLink, Router, NavigationEnd } from '@angular/router';
import { CommonModule } from '@angular/common';
import { filter } from 'rxjs';
import { AuthService } from './services/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, CommonModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  protected readonly title = signal('Curtin Honestly');
  authService = inject(AuthService);
  private router = inject(Router);
  mobileNavOpen = signal(false);

  constructor() {
    this.router.events
      .pipe(filter((event) => event instanceof NavigationEnd))
      .subscribe(() => this.mobileNavOpen.set(false));
  }

  toggleMobileNav() {
    this.mobileNavOpen.update((open) => !open);
  }

  closeMobileNav() {
    this.mobileNavOpen.set(false);
  }

  logout() {
    this.authService.logout();
    this.closeMobileNav();
  }
}
