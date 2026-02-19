import { Routes } from '@angular/router';
import { UnitListComponent } from './components/unit-list/unit-list.component';

export const routes: Routes = [
  { path: '', component: UnitListComponent },
  { path: '**', redirectTo: '' }
];
