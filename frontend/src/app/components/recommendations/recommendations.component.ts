import { Component, inject, OnInit, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { RouterLink } from '@angular/router';
import { RecommendationService } from '../../services/recommendation.service';
import { SeoService } from '../../services/seo.service';
import { RecommendationItem, Recommendations } from '../../models/recommendation.model';

@Component({
  selector: 'app-recommendations',
  standalone: true,
  imports: [NgTemplateOutlet, RouterLink],
  templateUrl: './recommendations.component.html',
  styleUrl: './recommendations.component.css'
})
export class RecommendationsComponent implements OnInit {
  private recommendationService = inject(RecommendationService);
  private seoService = inject(SeoService);

  data = signal<Recommendations | null>(null);
  isLoading = signal(true);
  errorMessage = signal<string | null>(null);
  showAvoid = signal(false);

  ngOnInit() {
    this.seoService.noIndex('For you | CurtinHonestly');
    this.load();
  }

  load() {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.recommendationService.getForMe().subscribe({
      next: (result) => {
        this.data.set(result);
        this.isLoading.set(false);
      },
      error: () => {
        this.errorMessage.set('Recommendations could not be loaded.');
        this.isLoading.set(false);
      }
    });
  }

  toggleAvoid() {
    this.showAvoid.update(open => !open);
  }

  // Cold-start items are backed by review counts; personalised items by
  // the number of similar students who reviewed the unit.
  supportLabel(item: RecommendationItem, coldStart: boolean): string {
    const n = item.supportingStudents;
    if (coldStart) {
      return `${n} ${n === 1 ? 'review' : 'reviews'}`;
    }
    return `based on ${n} similar ${n === 1 ? 'student' : 'students'}`;
  }

  plural(count: number, singular: string, pluralForm: string): string {
    return count === 1 ? singular : pluralForm;
  }
}
