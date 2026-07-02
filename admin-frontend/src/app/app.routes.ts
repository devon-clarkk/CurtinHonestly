import { Routes } from '@angular/router';
import { adminGuard } from './guards/admin.guard';
import { LoginComponent } from './pages/login/login.component';
import { OverviewComponent } from './pages/overview/overview.component';
import { AnalyticsComponent } from './pages/analytics/analytics.component';
import { OperationsComponent } from './pages/operations/operations.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: '', component: OverviewComponent, canActivate: [adminGuard] },
  { path: 'analytics', component: AnalyticsComponent, canActivate: [adminGuard] },
  { path: 'operations', component: OperationsComponent, canActivate: [adminGuard] },
  { path: '**', redirectTo: '' }
];
