#!/usr/bin/python3
import json
import requests
import re
import os

BASE_URL = "http://localhost:8080"
HTTP_FILE = "/app/src/test/java/com/curtinhonestly/backend/httprequest/populate_units.http"
ADMIN_EMAIL = "admin@student.curtin.edu.au"
ADMIN_PASSWORD = "adminpassword"

def authenticate():
    print(f"Authenticating as {ADMIN_EMAIL}...")
    try:
        # Try register
        r = requests.post(f"{BASE_URL}/auth/register", json={"email": ADMIN_EMAIL, "password": ADMIN_PASSWORD})
        if r.status_code == 200:
            return r.json().get("token")
        
        # Try login
        r = requests.post(f"{BASE_URL}/auth/login", json={"email": ADMIN_EMAIL, "password": ADMIN_PASSWORD})
        if r.status_code == 200:
            return r.json().get("token")
        
        print(f"Auth failed: {r.text}")
    except Exception as e:
        print(f"Auth error: {e}")
    return None

def clean_json(s):
    # Handle the {{admin_token}} and other headers that might be caught
    # We find the first { that is followed by "code"
    match = re.search(r'\{\s*"code".*\}', s, re.DOTALL)
    if not match: return None
    
    s = match.group(0)
    # Fix common syntax errors in the .http file (like missing commas between fields)
    s = re.sub(r'("|\d|true|false|null)\n\s*"', r'\1,\n"', s)
    # Remove trailing commas before closing braces
    s = re.sub(r',\s*\}', '}', s)
    s = re.sub(r',\s*\]', ']', s)
    return s

def populate():
    token = authenticate()
    if not token: return

    print("Successfully authenticated.")
    
    if not os.path.exists(HTTP_FILE):
        print(f"File not found: {HTTP_FILE}")
        return

    with open(HTTP_FILE, 'r') as f:
        content = f.read()

    sections = content.split('###')
    headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
    
    count = 0
    for section in sections:
        if 'POST' in section and '/units' in section:
            try:
                cleaned = clean_json(section)
                if cleaned:
                    data = json.loads(cleaned)
                    code = data.get('code', 'UNKNOWN')
                    
                    print(f"Inserting {code}... ", end="", flush=True)
                    r = requests.post(f"{BASE_URL}/units", json=data, headers=headers)
                    if r.status_code in [200, 201]:
                        print("OK")
                        count += 1
                    else:
                        print(f"FAILED ({r.status_code}): {r.text}")
            except Exception as e:
                print(f"Error processing section: {e}")

    print(f"\n--- Finished! Added {count} units. ---")

if __name__ == "__main__":
    print("--- Starting Database Population ---")
    populate()
    print("--- Population Finished ---")
