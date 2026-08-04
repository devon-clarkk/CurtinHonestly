import { environment } from '../environments/environment';

const GA_MEASUREMENT_ID = 'G-3H2M1W3GSR';

declare global {
  interface Window {
    dataLayer: unknown[];
    gtag: (...args: unknown[]) => void;
  }
}

/** Loads Google Analytics only on prod builds (same gate as SEO indexing). */
export function initGoogleAnalytics(): void {
  if (!environment.seoEnabled) {
    return;
  }

  window.dataLayer = window.dataLayer || [];
  // gtag.js only interprets dataLayer entries that are `arguments` objects; a rest
  // parameter pushes a plain Array, which it silently ignores. Keep `arguments`.
  window.gtag = function gtag() {
    window.dataLayer.push(arguments);
  };
  window.gtag('js', new Date());
  window.gtag('config', GA_MEASUREMENT_ID);

  const script = document.createElement('script');
  script.async = true;
  script.src = `https://www.googletagmanager.com/gtag/js?id=${GA_MEASUREMENT_ID}`;
  document.head.appendChild(script);
}
