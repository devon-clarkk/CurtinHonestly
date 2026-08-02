import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      // The shell renders routerLink in both the nav and the footer, which needs
      // ActivatedRoute. Without this the whole spec fails with NG0201.
      providers: [provideRouter([])],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render the site nav', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    const navLinks = Array.from(compiled.querySelectorAll('#main-nav a')).map(a =>
      a.textContent?.trim()
    );
    expect(navLinks).toContain('Home');
    expect(navLinks).toContain('Compare');
  });

  it('should link to the legal pages from the footer', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    const footerLinks = Array.from(compiled.querySelectorAll('.footer-links a')).map(a =>
      a.getAttribute('href')
    );
    expect(footerLinks).toContain('/terms');
    expect(footerLinks).toContain('/privacy');
  });
});
