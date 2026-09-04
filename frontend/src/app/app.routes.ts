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
import { clubGuard } from './guards/club.guard';

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
  // Personalised recommendations. Signed-in only and reached from the nav, so
  // lazy: anonymous visitors never download it.
  {
    path: 'for-you',
    loadComponent: () => import('./components/recommendations/recommendations.component').then(m => m.RecommendationsComponent),
    canActivate: [authGuard]
  },
  // Community boards. Client-rendered and noindex for now, entered from the nav
  // or a unit page, so lazy like the other secondary pages. The three routes
  // share the components under components/boards.
  {
    path: 'boards',
    loadComponent: () => import('./components/boards/boards-landing/boards-landing.component').then(m => m.BoardsLandingComponent)
  },
  {
    path: 'boards/units/:code',
    loadComponent: () => import('./components/boards/unit-board/unit-board.component').then(m => m.UnitBoardComponent)
  },
  {
    path: 'boards/threads/:id',
    loadComponent: () => import('./components/boards/thread-view/thread-view.component').then(m => m.ThreadViewComponent)
  },
  // Club study sessions and events. Client-rendered and noindex for now, like
  // the boards, and lazy for the same reason: entered from the nav, a unit
  // page or the home strip rather than being anyone's first page.
  {
    path: 'events',
    loadComponent: () => import('./components/events/events-page/events-page.component').then(m => m.EventsPageComponent)
  },
  {
    path: 'events/:id',
    loadComponent: () => import('./components/events/event-detail/event-detail.component').then(m => m.EventDetailComponent)
  },
  {
    path: 'clubs',
    loadComponent: () => import('./components/clubs/clubs-directory/clubs-directory.component').then(m => m.ClubsDirectoryComponent)
  },
  {
    path: 'clubs/:slug',
    loadComponent: () => import('./components/clubs/club-profile/club-profile.component').then(m => m.ClubProfileComponent)
  },
  // The club portal: club member accounts (ROLE_CLUB) and admins only.
  {
    path: 'club',
    loadComponent: () => import('./components/club-portal/club-portal.component').then(m => m.ClubPortalComponent),
    canActivate: [clubGuard]
  },
  { path: 'units/:code', component: UnitDetailComponent },
  {
    path: '**',
    loadComponent: () => import('./components/not-found/not-found.component').then(m => m.NotFoundComponent)
  }
];
