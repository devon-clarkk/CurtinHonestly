import { Routes } from '@angular/router';
import { UnitListComponent } from './components/unit-list/unit-list.component';
import { UnitDetailComponent } from './components/unit-detail/unit-detail.component';
import { LoginComponent } from './components/login/login.component';
import { RegisterComponent } from './components/register/register.component';

export const routes: Routes = [
  // The home page showing all units
  { path: '', component: UnitListComponent },
  
  // Auth pages
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  
  // The detail page for a specific unit (e.g., /units/COMP1000)
  // Beginners: the ':code' part is a placeholder for the actual unit code
  { path: 'units/:code', component: UnitDetailComponent },
  
  // Catch-all route to redirect back home if the URL is wrong
  { path: '**', redirectTo: '' }
];
