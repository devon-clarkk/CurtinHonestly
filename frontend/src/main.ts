import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';
import { initGoogleAnalytics } from './app/google-analytics';

initGoogleAnalytics();

bootstrapApplication(App, appConfig)
  .catch((err) => console.error(err));
