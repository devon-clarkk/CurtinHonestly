import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SeoService } from '../../services/seo.service';

/**
 * Not reviewed by a lawyer. The content describes what the platform actually
 * does (anonymity model, moderation, account deletion behaviour, campaign draws,
 * aggregated data use) rather than generic boilerplate, but a legal review is
 * still worth getting.
 *
 * contact@curtinhonestly.com must be a real, monitored mailbox. A terms page
 * pointing at an address nobody reads is worse than no address.
 */
@Component({
  selector: 'app-terms',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './terms.component.html',
  styleUrl: './legal.css'
})
export class TermsComponent implements OnInit {
  private seoService = inject(SeoService);

  ngOnInit(): void {
    this.seoService.noIndex('Terms and Conditions | CurtinHonestly');
  }
}
