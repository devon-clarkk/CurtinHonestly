import { Routes } from '@angular/router';
import { UnitListComponent } from './components/unit-list/unit-list.component';
import { UnitDetailComponent } from './components/unit-detail/unit-detail.component';

export const routes: Routes = [
  // The home page showing all units
  { path: '', component: UnitListComponent },
  
  // The detail page for a specific unit (e.g., /units/COMP1000)
  // Beginners: the ':code' part is a placeholder for the actual unit code
  { path: 'units/:code', component: UnitDetailComponent },
  
  // Catch-all route to redirect back home if the URL is wrong
  { path: '**', redirectTo: '' }
];
