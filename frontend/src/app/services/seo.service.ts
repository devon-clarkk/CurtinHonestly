import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';
import { UnitDetails } from '../models/unit.model';
import { FacultyHub, facultyPagePath } from '../utils/faculty.util';
import { environment } from '../../environments/environment';
import {
  UnitLink,
  buildFacultyJsonLd,
  buildInfoPageJsonLd,
  buildHomeJsonLd,
  buildUnitJsonLd,
  facultyPageDescription,
  facultyPageTitle,
  homePageDescription,
  homePageTitle,
  serializeJsonLd,
  unitPageDescription,
  unitPagePath,
  unitPageTitle,
} from '../utils/unit-seo.utils';

@Injectable({
  providedIn: 'root',
})
export class SeoService {
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);
  private readonly document = inject(DOCUMENT);
  private readonly siteUrl = environment.siteUrl.replace(/\/$/, '');
  private readonly seoEnabled = environment.seoEnabled;

  updateHomePage(): void {
    if (!this.seoEnabled) {
      this.applyDevNoIndex('CurtinHonestly (Dev)');
      return;
    }

    const title = homePageTitle();
    const description = homePageDescription();
    const url = `${this.siteUrl}/`;

    this.title.setTitle(title);
    this.setIndexable();
    this.setDescription(description);
    this.setCanonical(url);
    this.setOpenGraph({
      title,
      description,
      url,
      type: 'website',
    });
    this.setJsonLd(buildHomeJsonLd(this.siteUrl));
  }

  updateUnitPage(unit: UnitDetails, requiredFor: string[] = []): void {
    if (!this.seoEnabled) {
      this.applyDevNoIndex(`${unit.code} (Dev)`);
      return;
    }

    const title = unitPageTitle(unit.code, unit.name, unit.numberOfReviews);
    const description = unitPageDescription(unit);
    const url = `${this.siteUrl}${unitPagePath(unit.code)}`;

    this.title.setTitle(title);
    this.setIndexable();
    this.setDescription(description);
    this.setCanonical(url);
    this.setOpenGraph({
      title,
      description,
      url,
      type: 'article',
    });
    this.setJsonLd(buildUnitJsonLd(unit, this.siteUrl, requiredFor));
  }

  updateFacultyPage(hub: FacultyHub, units: UnitLink[]): void {
    if (!this.seoEnabled) {
      this.applyDevNoIndex(`${hub.name} units (Dev)`);
      return;
    }

    const title = facultyPageTitle(hub.name);
    const description = facultyPageDescription(hub.name, units.length);
    const url = `${this.siteUrl}${facultyPagePath(hub.slug)}`;

    this.title.setTitle(title);
    this.setIndexable();
    this.setDescription(description);
    this.setCanonical(url);
    this.setOpenGraph({
      title,
      description,
      url,
      type: 'website',
    });
    this.setJsonLd(buildFacultyJsonLd(hub, units, this.siteUrl));
  }

  /**
   * An ordinary indexable content page: about, contact. These carry no data of
   * their own, so the caller supplies the copy and the path.
   */
  updateInfoPage(page: { title: string; description: string; path: string }): void {
    if (!this.seoEnabled) {
      this.applyDevNoIndex(`${page.title} (Dev)`);
      return;
    }

    const title = `${page.title} | CurtinHonestly`;
    const url = `${this.siteUrl}${page.path}`;

    this.title.setTitle(title);
    this.setIndexable();
    this.setDescription(page.description);
    this.setCanonical(url);
    this.setOpenGraph({ title, description: page.description, url, type: 'website' });
    this.setJsonLd(buildInfoPageJsonLd(this.siteUrl, page));
  }

  /** Call from any route that must never be indexed (auth, account pages). */
  noIndex(pageTitle: string): void {
    this.title.setTitle(pageTitle);
    this.meta.updateTag({ name: 'robots', content: 'noindex, nofollow' });
    this.removeCanonical();
    this.removeJsonLd();
  }

  private applyDevNoIndex(pageTitle: string): void {
    this.noIndex(pageTitle);
  }

  /**
   * index.html ships `noindex, nofollow` so the static shell is never indexable.
   * That shell is what Azure Static Web Apps serves for every URL it has no
   * prerendered file behind.
   * The two route families that are genuinely worth indexing say so themselves,
   * here, and both of them are prerendered, so the opt-in is in the HTML a
   * crawler receives rather than something it has to run JavaScript to discover.
   */
  private setIndexable(): void {
    this.meta.updateTag({ name: 'robots', content: 'index, follow' });
  }

  private setDescription(description: string): void {
    this.meta.updateTag({ name: 'description', content: description });
    this.meta.updateTag({ property: 'og:description', content: description });
    this.meta.updateTag({ name: 'twitter:description', content: description });
  }

  private setCanonical(url: string): void {
    let link = this.document.querySelector<HTMLLinkElement>('link[rel="canonical"]');
    if (!link) {
      link = this.document.createElement('link');
      link.setAttribute('rel', 'canonical');
      this.document.head.appendChild(link);
    }
    link.setAttribute('href', url);
  }

  private removeCanonical(): void {
    this.document.querySelector('link[rel="canonical"]')?.remove();
  }

  private setOpenGraph({
    title,
    description,
    url,
    type,
  }: {
    title: string;
    description: string;
    url: string;
    type: string;
  }): void {
    const image = `${this.siteUrl}/assets/images/logo.png`;

    this.meta.updateTag({ property: 'og:title', content: title });
    this.meta.updateTag({ property: 'og:description', content: description });
    this.meta.updateTag({ property: 'og:url', content: url });
    this.meta.updateTag({ property: 'og:type', content: type });
    this.meta.updateTag({ property: 'og:locale', content: 'en_AU' });
    this.meta.updateTag({ property: 'og:site_name', content: 'CurtinHonestly' });
    this.meta.updateTag({ property: 'og:image', content: image });
    this.meta.updateTag({ name: 'twitter:card', content: 'summary' });
    this.meta.updateTag({ name: 'twitter:title', content: title });
    this.meta.updateTag({ name: 'twitter:description', content: description });
    this.meta.updateTag({ name: 'twitter:image', content: image });
  }

  private setJsonLd(data: Record<string, unknown>): void {
    const id = 'seo-json-ld';
    let script = this.document.getElementById(id) as HTMLScriptElement | null;

    if (!script) {
      script = this.document.createElement('script');
      script.id = id;
      script.type = 'application/ld+json';
      this.document.head.appendChild(script);
    }

    script.textContent = serializeJsonLd(data);
  }

  private removeJsonLd(): void {
    this.document.getElementById('seo-json-ld')?.remove();
  }
}
