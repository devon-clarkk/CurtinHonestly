import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { UnitService } from '../../services/unit.service';
import { UnitSummary } from '../../models/unit.model';
import { Observable, map } from 'rxjs';

@Component({
  selector: 'app-unit-list',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './unit-list.component.html',
  styleUrl: './unit-list.component.css'
})
export class UnitListComponent implements OnInit {
  private unitService = inject(UnitService);
  units$: Observable<UnitSummary[]> | undefined;

  ngOnInit(): void {
    this.units$ = this.unitService.getUnits().pipe(
      map(page => page.content)
    );
  }

  getStars(rating: number): string {
    return '★'.repeat(Math.round(rating)) + '☆'.repeat(5 - Math.round(rating));
  }
}
