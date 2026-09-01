import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SeoService } from '../../services/seo.service';

/**
 * The terms and privacy pages both name contact@curtinhonestly.com, so an
 * address without a page behind it was a loose end. This one also routes the
 * cases that arrive by email rather than through the site: a staff member named
 * in a review, a correction to unit data, a privacy request.
 */
@Component({
  selector: 'app-contact',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './contact.component.html',
  styleUrl: '../legal/legal.css'
})
export class ContactComponent implements OnInit {
  private seoService = inject(SeoService);

  ngOnInit(): void {
    this.seoService.updateInfoPage({
      title: 'Contact',
      description:
        'How to reach Curtin Honestly: reporting a review, corrections to unit information, ' +
        'privacy requests, and enquiries from student societies and media.',
      path: '/contact',
    });
  }
}
