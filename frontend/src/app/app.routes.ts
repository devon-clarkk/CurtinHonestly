import { Routes } from '@angular/router';
import { UnitListComponent } from './components/unit-list/unit-list.component';
import { UnitDetailComponent } from './components/unit-detail/unit-detail.component';
import { LoginComponent } from './components/login/login.component';
import { RegisterComponent } from './components/register/register.component';
import { AccountComponent } from './components/account/account.component';
import { MyReviewsComponent } from './components/my-reviews/my-reviews.component';
import { VerifyStudentConfirmComponent } from './components/verify-student-confirm/verify-student-confirm.component';
import { ForgotPasswordComponent } from './components/forgot-password/forgot-password.component';
import { ResetPasswordComponent } from './components/reset-password/reset-password.component';
import { authGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: '', component: UnitListComponent },
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  { path: 'account', component: AccountComponent, canActivate: [authGuard] },
  { path: 'my-reviews', component: MyReviewsComponent, canActivate: [authGuard] },
  { path: 'verify-student/confirm', component: VerifyStudentConfirmComponent },
  { path: 'forgot-password', component: ForgotPasswordComponent },
  { path: 'reset-password', component: ResetPasswordComponent },
  // Lazy-loaded: these are low-traffic pages carrying a lot of static markup.
  // Importing them eagerly put 21 kB of legal copy into the initial bundle, which
  // every visitor to the homepage would download without ever opening them.
  {
    path: 'compare',
    loadComponent: () => import('./components/compare/compare.component').then(m => m.CompareComponent)
  },
  {
    path: 'about',
    loadComponent: () => import('./components/info/about.component').then(m => m.AboutComponent)
  },
  {
    path: 'contact',
    loadComponent: () => import('./components/info/contact.component').then(m => m.ContactComponent)
  },
  {
    path: 'terms',
    loadComponent: () => import('./components/legal/terms.component').then(m => m.TermsComponent)
  },
  {
    path: 'privacy',
    loadComponent: () => import('./components/legal/privacy.component').then(m => m.PrivacyComponent)
  },
  // Lazy for the same reason as compare and the legal pages: the initial bundle
  // is already over budget, and a hub page is entered from a link rather than
  // being the first thing anyone loads. Prerendering works fine on a lazy route.
  {
    path: 'faculty/:slug',
    loadComponent: () => import('./components/faculty/faculty.component').then(m => m.FacultyComponent)
  },
  { path: 'units/:code', component: UnitDetailComponent },
  {
    path: '**',
    loadComponent: () => import('./components/not-found/not-found.component').then(m => m.NotFoundComponent)
  }
];
