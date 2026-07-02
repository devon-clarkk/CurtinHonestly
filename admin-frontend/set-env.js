const fs = require('fs');
const path = require('path');

const envPath = path.resolve(__dirname, '../.env');
const targetPath = path.resolve(__dirname, 'src/environments/environment.ts');

let apiUrl = process.env.API_URL || 'http://localhost:8080';
let production = process.env.NODE_ENV === 'production';

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
  apiUrl: '${apiUrl}'
};
`;

fs.writeFileSync(targetPath, envConfigFile);
console.log(`Generated environment.ts with API_URL: ${apiUrl}`);
