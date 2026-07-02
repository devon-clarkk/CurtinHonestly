const fs = require('fs');
const path = require('path');

// 1. Paths
const envPath = path.resolve(__dirname, '../.env');
const targetPath = path.resolve(__dirname, 'src/environments/environment.ts');

// 2. Initial values (Priority to Vercel/System environment variables)
let apiUrl = process.env.API_URL || 'http://localhost:8080';
let production = process.env.NODE_ENV === 'production' || process.env.VERCEL_ENV === 'production';

// 3. .env parser (Used for local development)
if (fs.existsSync(envPath)) {
  const envContent = fs.readFileSync(envPath, 'utf8');

  // FIX: Split by new line (\n), not by empty character ('')
  const lines = envContent.split(/\r?\n/);

  lines.forEach(line => {
    const [key, ...valueParts] = line.split('=');
    if (key && valueParts.length > 0) {
      const trimmedKey = key.trim();
      const trimmedValue = valueParts.join('=').trim().replace(/"/g, '');

      // Only override if not already set by the system (Vercel)
      if (trimmedKey === 'API_URL' && !process.env.API_URL) {
        apiUrl = trimmedValue;
      }
      if (trimmedKey === 'NODE_ENV' && trimmedValue === 'production') {
        production = true;
      }
    }
  });
}

// 4. Generate the environment.ts file content
const envConfigFile = `export const environment = {
  production: ${production},
  apiUrl: '${apiUrl}'
};
`;

// 5. Write to the file
fs.writeFile(targetPath, envConfigFile, function (err) {
  if (err) {
    console.error('Error while generating environment.ts:', err);
  } else {
    console.log(`Generated environment.ts with API_URL: ${apiUrl}`);
  }
});
