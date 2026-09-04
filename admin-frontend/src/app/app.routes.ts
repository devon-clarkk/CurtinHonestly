import { Routes } from '@angular/router';
import { adminGuard } from './guards/admin.guard';
import { LoginComponent } from './pages/login/login.component';
import { OverviewComponent } from './pages/overview/overview.component';
import { AnalyticsComponent } from './pages/analytics/analytics.component';
import { OperationsComponent } from './pages/operations/operations.component';
import { CampaignsComponent } from './pages/campaigns/campaigns.component';
import { BoardsComponent } from './pages/boards/boards.component';
import { ResourcesComponent } from './pages/resources/resources.component';
import { ClubsComponent } from './pages/clubs/clubs.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: '', component: OverviewComponent, canActivate: [adminGuard] },
  { path: 'analytics', component: AnalyticsComponent, canActivate: [adminGuard] },
  { path: 'operations', component: OperationsComponent, canActivate: [adminGuard] },
  { path: 'campaigns', component: CampaignsComponent, canActivate: [adminGuard] },
  { path: 'boards', component: BoardsComponent, canActivate: [adminGuard] },
  { path: 'resources', component: ResourcesComponent, canActivate: [adminGuard] },
  { path: 'clubs', component: ClubsComponent, canActivate: [adminGuard] },
  { path: '**', redirectTo: '' }
];
