import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SeoService } from '../../services/seo.service';

/**
 * DRAFT - not reviewed by a lawyer.
 *
 * The content describes what the platform actually does (anonymity model,
 * moderation, account deletion behaviour, campaign draws) rather than generic
 * boilerplate, but it still needs a legal review before it can be relied on.
 * CONTACT_EMAIL_PLACEHOLDER must also be replaced before this ships.
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
