import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SeoService } from '../../services/seo.service';

// Rendered for any unmatched route (the wildcard '**'). On Azure Static Web
// Apps the SPA fallback serves this at HTTP 200, so this is a client-rendered
// "not found" page rather than a true 404 status — normal for a static SPA.
@Component({
  selector: 'app-not-found',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './not-found.component.html',
  styleUrl: './not-found.component.css'
})
export class NotFoundComponent implements OnInit {
  private seoService = inject(SeoService);

  ngOnInit(): void {
    this.seoService.noIndex('Page not found | CurtinHonestly');
  }
}
