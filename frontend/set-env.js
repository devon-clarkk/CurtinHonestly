const fs = require('fs');
const path = require('path');
const { resolveSeoBuildConfig } = require('./scripts/seo-build-config');

const targetPath = path.resolve(__dirname, 'src/environments/environment.ts');
const { apiUrl, siteUrl, production, seoEnabled } = resolveSeoBuildConfig();
// Community boards ship dark until there is a user base to fill them. Off unless the
// build sets BOARDS_ENABLED=true (the dev Static Web App workflow does; prod does not).
const boardsEnabled = process.env.BOARDS_ENABLED === 'true';
// Personal recommendations (For you page, home teaser, unit page match panel) need a
// larger review base than the site has today. Off unless PERSONAL_RECS_ENABLED=true.
// The unit-to-unit strip (students also liked) is not gated by this.
const personalRecsEnabled = process.env.PERSONAL_RECS_ENABLED === 'true';

const envConfigFile = `export const environment = {
  production: ${production},
  apiUrl: '${apiUrl.replace(/'/g, "\\'")}',
  siteUrl: '${siteUrl.replace(/'/g, "\\'")}',
  seoEnabled: ${seoEnabled},
  boardsEnabled: ${boardsEnabled},
  personalRecsEnabled: ${personalRecsEnabled}
};
`;

fs.writeFileSync(targetPath, envConfigFile);
console.log(
  `Generated environment.ts: API_URL ${apiUrl}, SITE_URL ${siteUrl}, SEO ${seoEnabled ? 'enabled (prod)' : 'disabled (dev/local)'}, BOARDS ${boardsEnabled ? 'enabled' : 'disabled'}, PERSONAL RECS ${personalRecsEnabled ? 'enabled' : 'disabled'}`
);
