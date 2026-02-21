import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { UnitService } from '../../services/unit.service';
import { UnitSummary } from '../../models/unit.model';
import { Observable, map } from 'rxjs';

/**
 * This component displays a grid of unit summaries fetched from the backend.
 * Beginners: We use CommonModule for basic Angular directives like *ngFor and *ngIf.
 */
@Component({
  selector: 'app-unit-list',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './unit-list.component.html',
  styleUrl: './unit-list.component.css'
})
export class UnitListComponent implements OnInit {
  // Use 'inject' to get the UnitService - this is the modern way in Angular!
  private unitService = inject(UnitService);
  
  // An Observable that will hold our list of units once they load
  units$: Observable<UnitSummary[]> | undefined;

  // This runs when the component starts up
  ngOnInit(): void {
    // We get the first page of units (0) and extract just the content array
    this.units$ = this.unitService.getUnits(0, 12).pipe(
      map(page => page.content.map(unit => ({
        ...unit,
        // If the backend sends 85 instead of 0.85, we divide by 100
        wouldTakeAgainRatio: unit.wouldTakeAgainRatio > 1 ? unit.wouldTakeAgainRatio / 100 : unit.wouldTakeAgainRatio
      })))
    );
  }

  // A helper function to generate star icons based on rating
  getStars(rating: number): string {
    const fullStars = Math.max(0, Math.min(5, Math.round(rating || 0)));
    return '★'.repeat(fullStars) + '☆'.repeat(5 - fullStars);
  }
}
