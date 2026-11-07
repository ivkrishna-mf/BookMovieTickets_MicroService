#!/bin/sh
# Writes the browser runtime config from environment variables so a single image serves every environment.
set -eu

json_escape() { printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g'; }

cities_json=""
OLD_IFS=$IFS; IFS=','
for city in ${BMT_CITIES:-Mumbai}; do
  city="$(echo "$city" | sed -e 's/^ *//' -e 's/ *$//')"
  [ -n "$city" ] || continue
  cities_json="${cities_json:+$cities_json, }\"$(json_escape "$city")\""
done
IFS=$OLD_IFS

cat > /tmp/config.js <<EOF
window.__BMT_CONFIG__ = {
  apiBaseUrl: "$(json_escape "${BMT_API_BASE_URL:-}")",
  authUrl: "$(json_escape "${BMT_AUTH_URL}")",
  clientId: "$(json_escape "${BMT_CLIENT_ID}")",
  paymentMode: "$(json_escape "${BMT_PAYMENT_MODE:-fake}")",
  stripePublishableKey: "$(json_escape "${BMT_STRIPE_PUBLISHABLE_KEY:-}")",
  defaultCity: "$(json_escape "${BMT_DEFAULT_CITY:-Mumbai}")",
  cities: [${cities_json}]
};
EOF
echo "runtime-config: wrote /tmp/config.js (auth=${BMT_AUTH_URL}, payment=${BMT_PAYMENT_MODE:-fake})"
