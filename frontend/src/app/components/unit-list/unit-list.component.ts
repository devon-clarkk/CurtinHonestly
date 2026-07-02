import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { UnitService } from '../../services/unit.service';
import { SeoService } from '../../services/seo.service';
import { UnitSummary, Faculty, UnitLevel } from '../../models/unit.model';
import { Observable, map, BehaviorSubject, switchMap, debounceTime, distinctUntilChanged } from 'rxjs';

@Component({
  selector: 'app-unit-list',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  templateUrl: './unit-list.component.html',
  styleUrl: './unit-list.component.css'
})
export class UnitListComponent implements OnInit {
  private unitService = inject(UnitService);
  private seoService = inject(SeoService);
  
  // Filter state
  searchQuery = '';
  selectedFaculties: Faculty[] = [];
  selectedLevel?: UnitLevel;
  sortBy = 'code';

  // Options for dropdowns
  faculties = this.unitService.getFaculties();
  levels = this.unitService.getUnitLevels();
  sortOptions = [
    { value: 'code', label: 'Unit Code (A-Z)' },
    { value: 'code_desc', label: 'Unit Code (Z-A)' },
    { value: 'name', label: 'Name (A-Z)' },
    { value: 'name_desc', label: 'Name (Z-A)' },
    { value: 'most_reviewed', label: 'Most Reviewed' },
    { value: 'least_reviewed', label: 'Least Reviewed' },
    { value: 'highest_rated', label: 'Highest Rated' },
    { value: 'lowest_rated', label: 'Lowest Rated' },
    { value: 'highest_mark', label: 'Highest Avg Grade' },
    { value: 'lowest_mark', label: 'Lowest Avg Grade' },
    { value: 'lowest_workload', label: 'Lowest Workload' },
    { value: 'highest_workload', label: 'Highest Workload' }
  ];

  // Subject to trigger refreshes
  private filterSubject = new BehaviorSubject<void>(undefined);
  
  units$: Observable<UnitSummary[]> | undefined;

  ngOnInit(): void {
    this.seoService.updateHomePage();

    this.units$ = this.filterSubject.pipe(
      debounceTime(300), // Small wait to avoid too many requests while typing
      switchMap(() => this.unitService.getUnits(
        0, 100, // Load a large first page for now
        this.searchQuery,
        this.selectedFaculties,
        this.selectedLevel,
        this.sortBy
      )),
      map(page => page.content.map(unit => ({
        ...unit,
        wouldTakeAgainRatio: unit.wouldTakeAgainRatio > 1 ? unit.wouldTakeAgainRatio / 100 : unit.wouldTakeAgainRatio
      })))
    );
  }

  onFilterChange() {
    this.filterSubject.next();
  }

  toggleFaculty(faculty: Faculty) {
    const index = this.selectedFaculties.indexOf(faculty);
    if (index > -1) {
      this.selectedFaculties.splice(index, 1);
    } else {
      this.selectedFaculties.push(faculty);
    }
    this.onFilterChange();
  }

  isFacultySelected(faculty: Faculty): boolean {
    return this.selectedFaculties.includes(faculty);
  }

  resetFilters() {
    this.searchQuery = '';
    this.selectedFaculties = [];
    this.selectedLevel = undefined;
    this.sortBy = 'code';
    this.onFilterChange();
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
