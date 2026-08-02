import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { UnitService } from '../../services/unit.service';
import { SeoService } from '../../services/seo.service';
import { UnitDetails } from '../../models/unit.model';

const MAX_COMPARE = 4;

@Component({
  selector: 'app-compare',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './compare.component.html',
  styleUrl: './compare.component.css'
})
export class CompareComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private unitService = inject(UnitService);
  private seoService = inject(SeoService);

  units = signal<UnitDetails[]>([]);
  missingCodes = signal<string[]>([]);
  isLoading = signal(true);
  errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.seoService.noIndex('Compare units | CurtinHonestly');

    const raw = this.route.snapshot.queryParamMap.get('units') || '';
    const codes = [...new Set(raw.split(',').map(c => c.trim()).filter(Boolean))].slice(0, MAX_COMPARE);

    if (codes.length < 2) {
      this.isLoading.set(false);
      this.errorMessage.set('Select at least 2 units from the catalog to compare them.');
      return;
    }

    forkJoin(
      codes.map(code =>
        this.unitService.getUnitByCode(code).pipe(catchError(() => of(null)))
      )
    ).subscribe(results => {
      this.isLoading.set(false);
      const loaded = results.filter((u): u is UnitDetails => u !== null);
      const missing = codes.filter((code, i) => results[i] === null);

      // Fewer than 2 units actually loaded (e.g. every fetch failed) — a
      // comparison table with 0-1 columns isn't useful, so treat it as an
      // error rather than rendering an empty/single-column table.
      if (loaded.length < 2) {
        this.errorMessage.set(
          missing.length > 0
            ? `Couldn't load enough units to compare (missing: ${missing.join(', ')}).`
            : 'Select at least 2 units from the catalog to compare them.'
        );
        return;
      }

      this.units.set(loaded);
      this.missingCodes.set(missing);
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
