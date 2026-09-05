const fs = require('fs');
const path = require('path');

const envPath = path.resolve(__dirname, '../.env');
const targetPath = path.resolve(__dirname, 'src/environments/environment.ts');

let apiUrl = process.env.API_URL || 'http://localhost:8080';
let production = process.env.NODE_ENV === 'production';
// Board moderation pages follow the public boards flag. Off unless the build sets
// BOARDS_ENABLED=true; the prod admin workflow does not.
const boardsEnabled = process.env.BOARDS_ENABLED === 'true';

if (fs.existsSync(envPath)) {
  const lines = fs.readFileSync(envPath, 'utf8').split(/\r?\n/);
  lines.forEach((line) => {
    const [key, ...valueParts] = line.split('=');
    if (key && valueParts.length > 0) {
      const trimmedKey = key.trim();
      const trimmedValue = valueParts.join('=').trim().replace(/"/g, '');
      if (trimmedKey === 'API_URL' && !process.env.API_URL) apiUrl = trimmedValue;
      if (trimmedKey === 'NODE_ENV' && trimmedValue === 'production') production = true;
    }
  });
}

const envConfigFile = `export const environment = {
  production: ${production},
  apiUrl: '${apiUrl}',
  boardsEnabled: ${boardsEnabled}
};
`;

fs.writeFileSync(targetPath, envConfigFile);
console.log(`Generated environment.ts with API_URL: ${apiUrl}, BOARDS ${boardsEnabled ? 'enabled' : 'disabled'}`);
