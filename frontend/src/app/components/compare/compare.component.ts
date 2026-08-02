import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, forkJoin, of } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, switchMap } from 'rxjs/operators';
import { UnitService } from '../../services/unit.service';
import { SeoService } from '../../services/seo.service';
import { UnitDetails, UnitSummary } from '../../models/unit.model';
import { IconComponent } from '../icon/icon.component';

const MAX_COMPARE = 4;

@Component({
  selector: 'app-compare',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, IconComponent],
  templateUrl: './compare.component.html',
  styleUrl: './compare.component.css'
})
export class CompareComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private unitService = inject(UnitService);
  private seoService = inject(SeoService);

  readonly maxCompare = MAX_COMPARE;

  units = signal<UnitDetails[]>([]);
  missingCodes = signal<string[]>([]);
  isLoading = signal(false);
  errorMessage = signal<string | null>(null);

  // Picker state. The selection lives in the URL so a comparison stays
  // shareable; these signals mirror it for rendering.
  selectedCodes = signal<string[]>([]);
  searchTerm = '';
  searchResults = signal<UnitSummary[]>([]);
  isSearching = signal(false);

  private searchInput$ = new Subject<string>();

  ngOnInit(): void {
    // Parameterised comparisons are kept out of the index deliberately: the
    // combination space is effectively unbounded, so letting crawlers explore it
    // produces near-duplicate thin pages. Targeted "X vs Y" pages are the SEO
    // play, and they would be prerendered routes rather than this query-string form.
    this.seoService.noIndex('Compare units | CurtinHonestly');

    this.route.queryParamMap.subscribe(params => {
      const raw = params.get('units') || '';
      const codes = [...new Set(raw.split(',').map(c => c.trim().toUpperCase()).filter(Boolean))]
        .slice(0, MAX_COMPARE);

      this.selectedCodes.set(codes);
      this.loadSelected(codes);
    });

    this.searchInput$
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        switchMap(term => {
          const trimmed = term.trim();
          if (trimmed.length < 2) {
            this.isSearching.set(false);
            return of(null);
          }
          this.isSearching.set(true);
          return this.unitService
            .getUnits(0, 8, trimmed)
            .pipe(catchError(() => of(null)));
        })
      )
      .subscribe(page => {
        this.isSearching.set(false);
        this.searchResults.set(page ? page.content : []);
      });
  }

  private loadSelected(codes: string[]): void {
    this.missingCodes.set([]);
    this.errorMessage.set(null);

    if (codes.length < 2) {
      this.units.set([]);
      this.isLoading.set(false);
      return;
    }

    this.isLoading.set(true);
    forkJoin(
      codes.map(code =>
        this.unitService.getUnitByCode(code).pipe(catchError(() => of(null)))
      )
    ).subscribe(results => {
      this.isLoading.set(false);
      const loaded = results.filter((u): u is UnitDetails => u !== null);
      const missing = codes.filter((_code, i) => results[i] === null);

      // Fewer than 2 units actually loaded (e.g. every fetch failed). A
      // comparison table with 0-1 columns isn't useful, so treat it as an
      // error rather than rendering an empty/single-column table.
      if (loaded.length < 2) {
        this.units.set([]);
        this.errorMessage.set(
          missing.length > 0
            ? `Couldn't load enough units to compare (missing: ${missing.join(', ')}).`
            : 'Pick at least 2 units to compare them.'
        );
        return;
      }

      this.units.set(loaded);
      this.missingCodes.set(missing);
    });
  }

  onSearchChange(term: string): void {
    this.searchInput$.next(term);
  }

  isSelected(code: string): boolean {
    return this.selectedCodes().includes(code.toUpperCase());
  }

  canAddMore(): boolean {
    return this.selectedCodes().length < MAX_COMPARE;
  }

  addUnit(code: string): void {
    const upper = code.toUpperCase();
    if (this.isSelected(upper) || !this.canAddMore()) {
      return;
    }
    this.applySelection([...this.selectedCodes(), upper]);
    this.searchTerm = '';
    this.searchResults.set([]);
  }

  removeUnit(code: string): void {
    this.applySelection(this.selectedCodes().filter(c => c !== code.toUpperCase()));
  }

  clearSelection(): void {
    this.applySelection([]);
  }

  /**
   * Selection is written to the query string rather than held in memory, so the
   * URL is shareable and the back button steps through comparisons. The
   * queryParamMap subscription above then drives the reload.
   */
  private applySelection(codes: string[]): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { units: codes.length > 0 ? codes.join(',') : null },
      queryParamsHandling: 'merge',
    });
  }

  examPercentage(unit: UnitDetails): number {
    if (!unit.reviews || unit.reviews.length === 0) {
      return 0;
    }
    const withExam = unit.reviews.filter(r => r.hasExam).length;
    return Math.round((withExam / unit.reviews.length) * 100);
  }
}
