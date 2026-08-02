import { Component, OnInit, inject } from '@angular/core';
import { SeoService } from '../../services/seo.service';

/**
 * DRAFT - not reviewed by a lawyer.
 *
 * Written against what the code actually collects: the User entity fields, the
 * review/tip/report models, the three browser-storage keys, and the
 * anonymize-on-delete behaviour. If any of those change, this page has to change
 * with them - it makes specific factual claims, including that no third-party
 * analytics or tracking scripts run on the site.
 *
 * contact@curtinhonestly.com must be a real, monitored mailbox: the Australian
 * Privacy Principles require a working channel for access and correction
 * requests, so a dead address is a compliance problem, not a cosmetic one.
 */
@Component({
  selector: 'app-privacy',
  standalone: true,
  imports: [],
  templateUrl: './privacy.component.html',
  styleUrl: './legal.css'
})
export class PrivacyComponent implements OnInit {
  private seoService = inject(SeoService);

  ngOnInit(): void {
    this.seoService.noIndex('Privacy Policy | CurtinHonestly');
  }
}
