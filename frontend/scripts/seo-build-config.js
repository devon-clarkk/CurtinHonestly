/**
 * Shared SEO build settings for set-env and pre-build scripts.
 * Prod only: prerender, sitemap, and indexable meta tags.
 */
const fs = require('fs');
const path = require('path');

const envPath = path.resolve(__dirname, '../../.env');

function readEnvFile() {
  const values = {};
  if (!fs.existsSync(envPath)) {
    return values;
  }

  for (const line of fs.readFileSync(envPath, 'utf8').split(/\r?\n/)) {
    const [key, ...valueParts] = line.split('=');
    if (key && valueParts.length > 0) {
      values[key.trim()] = valueParts.join('=').trim().replace(/"/g, '');
    }
  }

  return values;
}

function isDevApiUrl(apiUrl) {
  const normalized = apiUrl.toLowerCase();
  return (
    normalized.includes('localhost') ||
    normalized.includes('127.0.0.1') ||
    normalized.includes('be-curtinhonestly-dev') ||
    /[^a-z]dev[^a-z]/.test(normalized) ||
    normalized.includes('-dev.')
  );
}

function defaultSiteUrl(apiUrl) {
  const normalized = apiUrl.toLowerCase();
  if (normalized.includes('localhost') || normalized.includes('127.0.0.1')) {
    return 'http://localhost:4200';
  }
  if (isDevApiUrl(apiUrl)) {
    return 'https://nice-pebble-059fa6b00.7.azurestaticapps.net';
  }
  return 'https://www.curtinhonestly.com';
}

function resolveSeoEnabled(apiUrl) {
  const explicit = process.env.SEO_ENABLED;
  if (explicit !== undefined && explicit !== '') {
    return explicit === 'true' || explicit === '1';
  }
  return !isDevApiUrl(apiUrl);
}

function resolveSeoBuildConfig() {
  const fileEnv = readEnvFile();
  const apiUrl = process.env.API_URL || fileEnv.API_URL || 'http://localhost:8080';
  const production =
    process.env.NODE_ENV === 'production' ||
    process.env.VERCEL_ENV === 'production' ||
    fileEnv.NODE_ENV === 'production';

  let siteUrl = process.env.SITE_URL || fileEnv.SITE_URL;
  if (!siteUrl) {
    siteUrl = defaultSiteUrl(apiUrl);
  }

  return {
    apiUrl,
    siteUrl: siteUrl.replace(/\/$/, ''),
    production,
    seoEnabled: resolveSeoEnabled(apiUrl),
  };
}

module.exports = {
  resolveSeoBuildConfig,
  isDevApiUrl,
};
