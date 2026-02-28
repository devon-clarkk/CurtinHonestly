import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { UnitService } from '../../services/unit.service';
import { UnitDetails } from '../../models/unit.model';
import { Observable, switchMap, map } from 'rxjs';

/**
 * This component shows all the details for a single unit, including its reviews.
 * Beginners: We use 'ActivatedRoute' to get the unit code from the browser's URL.
 */
@Component({
  selector: 'app-unit-detail',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './unit-detail.component.html',
  styleUrl: './unit-detail.component.css'
})
export class UnitDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private unitService = inject(UnitService);

  // This will store all the unit information once it's fetched
  unit$: Observable<UnitDetails> | undefined;

  ngOnInit(): void {
    // 1. Listen to changes in the URL parameters (like /units/COMP1000)
    // 2. Use 'switchMap' to switch from the URL stream to the API data stream
    this.unit$ = this.route.paramMap.pipe(
      switchMap(params => {
        const code = params.get('code') || '';
        return this.unitService.getUnitByCode(code);
      }),
      map((unit: UnitDetails) => ({
        ...unit,
        // Ensure ratio is a decimal for the percentage pipe
        wouldTakeAgainRatio: unit.wouldTakeAgainRatio > 1 ? unit.wouldTakeAgainRatio / 100 : unit.wouldTakeAgainRatio
      }))
    );
  }

  getStarArray(rating: number): string[] {
    const stars: string[] = [];
    const fullStars = Math.floor(rating || 0);
    const hasHalfStar = (rating || 0) % 1 >= 0.5;

    for (let i = 1; i <= 5; i++) {
      if (i <= fullStars) {
        stars.push('full');
      } else if (i === fullStars + 1 && hasHalfStar) {
        stars.push('half');
      } else {
        stars.push('empty');
      }
    }
    return stars;
  }
}
