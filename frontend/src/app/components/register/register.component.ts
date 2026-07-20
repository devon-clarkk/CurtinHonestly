import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { SeoService } from '../../services/seo.service';
import { CampaignService } from '../../services/campaign.service';

const CAMPAIGN_REF_KEY = 'campaign_ref';
const CAMPAIGN_CODE_KEY = 'campaign_code';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './register.component.html',
  styleUrl: './register.component.css'
})
export class RegisterComponent implements OnInit {
  private authService = inject(AuthService);
  private campaignService = inject(CampaignService);
  private router = inject(Router);
  private seoService = inject(SeoService);
  private route = inject(ActivatedRoute);

  email = '';
  password = '';
  confirmPassword = '';
  promoCode = '';
  ref = '';
  campaignMessage = signal<string | null>(null);
  errorMessage = signal<string | null>(null);
  successMessage = signal<string | null>(null);
  isLoading = signal(false);

  ngOnInit() {
    this.seoService.noIndex('Register | CurtinHonestly');

    this.route.queryParamMap.subscribe(params => {
      const refParam = params.get('ref') ?? localStorage.getItem(CAMPAIGN_REF_KEY) ?? '';
      const codeParam = params.get('code') ?? localStorage.getItem(CAMPAIGN_CODE_KEY) ?? '';

      if (params.get('ref')) {
        localStorage.setItem(CAMPAIGN_REF_KEY, refParam);
      }
      if (params.get('code')) {
        localStorage.setItem(CAMPAIGN_CODE_KEY, codeParam);
      }

      this.ref = refParam;
      if (codeParam) {
        this.promoCode = codeParam;
      }

      if (refParam || codeParam) {
        this.validateCampaign(refParam, codeParam);
      }
    });
  }

  onSubmit() {
    if (this.password !== this.confirmPassword) {
      this.errorMessage.set('Passwords do not match.');
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.authService.register({
      email: this.email,
      password: this.password,
      ref: this.ref || undefined,
      promoCode: this.promoCode || undefined
    }).subscribe({
      next: (response) => {
        this.isLoading.set(false);
        localStorage.removeItem(CAMPAIGN_REF_KEY);
        localStorage.removeItem(CAMPAIGN_CODE_KEY);

        const message = response.verifiedStudent
          ? 'Registration successful! You are verified as a Curtin student.'
          : 'Registration successful! Verify your student email from your account to show the verified badge on reviews.';
        this.successMessage.set(message);
        setTimeout(() => this.router.navigate(['/']), 2000);
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(err.error?.error || 'Registration failed. Please try again.');
      }
    });
  }

  onPromoCodeChange() {
    if (this.ref || this.promoCode) {
      this.validateCampaign(this.ref, this.promoCode);
    } else {
      this.campaignMessage.set(null);
    }
  }

  private validateCampaign(ref: string, code: string) {
    if (!ref && !code) {
      return;
    }

    this.campaignService.validate(ref || undefined, code || undefined).subscribe({
      next: (result) => {
        if (result.valid) {
          const prize = result.prizeDescription ? ` Prize: ${result.prizeDescription}.` : '';
          this.campaignMessage.set(`You're signing up for ${result.campaignName}.${prize}`);
          this.errorMessage.set(null);
        } else {
          this.campaignMessage.set(null);
        }
      }
    });
  }
}
