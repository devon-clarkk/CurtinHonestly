const fs = require('fs');
const path = require('path');
const { resolveSeoBuildConfig } = require('./scripts/seo-build-config');

const targetPath = path.resolve(__dirname, 'src/environments/environment.ts');
const { apiUrl, siteUrl, production, seoEnabled } = resolveSeoBuildConfig();

const envConfigFile = `export const environment = {
  production: ${production},
  apiUrl: '${apiUrl.replace(/'/g, "\\'")}',
  siteUrl: '${siteUrl.replace(/'/g, "\\'")}',
  seoEnabled: ${seoEnabled}
};
`;

fs.writeFileSync(targetPath, envConfigFile);
console.log(
  `Generated environment.ts — API_URL: ${apiUrl}, SITE_URL: ${siteUrl}, SEO: ${seoEnabled ? 'enabled (prod)' : 'disabled (dev/local)'}`
);
