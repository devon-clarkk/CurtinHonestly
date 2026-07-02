import { Routes } from '@angular/router';
import { UnitListComponent } from './components/unit-list/unit-list.component';
import { UnitDetailComponent } from './components/unit-detail/unit-detail.component';
import { LoginComponent } from './components/login/login.component';
import { RegisterComponent } from './components/register/register.component';
import { AccountComponent } from './components/account/account.component';
import { MyReviewsComponent } from './components/my-reviews/my-reviews.component';
import { authGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: '', component: UnitListComponent },
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  { path: 'account', component: AccountComponent, canActivate: [authGuard] },
  { path: 'my-reviews', component: MyReviewsComponent, canActivate: [authGuard] },
  { path: 'units/:code', component: UnitDetailComponent },
  { path: '**', redirectTo: '' }
];
