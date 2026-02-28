const fs = require('fs');
const path = require('path');

// 1. Path to the root .env file
const envPath = path.resolve(__dirname, '../.env');
const targetPath = path.resolve(__dirname, 'src/environments/environment.ts');

// 2. Default values
let apiUrl = 'http://localhost:8080';
let production = false;

// 3. Simple .env parser
if (fs.existsSync(envPath)) {
  const envContent = fs.readFileSync(envPath, 'utf8');
  const lines = envContent.split('');

  lines.forEach(line => {
    const [key, value] = line.split('=');
    if (key && value) {
      const trimmedKey = key.trim();
      const trimmedValue = value.trim().replace(/"/g, ''); // Remove quotes if any

      if (trimmedKey === 'API_URL') {
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
    console.log(`Generated environment.ts at ${targetPath} with API_URL: ${apiUrl}`);
  }
});
