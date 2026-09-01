import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SeoService } from '../../services/seo.service';

/**
 * The page that answers "who is behind this and why should I believe it".
 *
 * Written because the absence of one was the concrete objection: an assistant
 * asked to assess the site could not find who ran it, what "Verified Curtin
 * Student" meant, or how reviews were moderated, and discounted the site
 * accordingly. Every claim here is one the site can stand behind, including the
 * limits of what verification proves.
 *
 * Indexed, unlike the terms and privacy pages. This one is worth finding.
 */
@Component({
  selector: 'app-about',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './about.component.html',
  styleUrl: '../legal/legal.css'
})
export class AboutComponent implements OnInit {
  private seoService = inject(SeoService);

  ngOnInit(): void {
    this.seoService.updateInfoPage({
      title: 'About',
      description:
        'Curtin Honestly collects honest student reviews of Curtin University units. Who runs it, ' +
        'what verified means, how reviews are moderated, and how anonymous they are.',
      path: '/about',
    });
  }
}
