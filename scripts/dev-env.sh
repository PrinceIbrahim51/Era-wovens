#!/usr/bin/env bash
# Creates a local .env from .env.example with freshly generated random secrets.
# Never overwrites an existing .env.
set -euo pipefail
cd "$(dirname "$0")/.."
if [ -f .env ]; then echo ".env already exists; leaving it unchanged"; exit 0; fi
rand() { openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | head -c 40; }
while IFS= read -r line; do
  if [[ "$line" =~ ^([A-Z0-9_]+)=replace_me$ ]] || [[ "$line" =~ ^([A-Z0-9_]+)=replace_with_32_plus_random_chars$ ]]; then
    echo "${BASH_REMATCH[1]}=$(rand)"
  elif [[ "$line" =~ ^(RAZORPAY_KEY_ID|RAZORPAY_KEY_SECRET|RAZORPAY_WEBHOOK_SECRET)= ]]; then
    echo "${BASH_REMATCH[1]}="
  else
    echo "$line"
  fi
done < .env.example > .env
chmod 600 .env
echo "Wrote .env with random local secrets."
echo "To enable online payments add Razorpay TEST keys and set RAZORPAY_ENABLED=true."
