#!/usr/bin/env bash
# End-to-end happy path against the local docker-compose stack (fake payment provider).
# Requires: curl, jq.   Run after: docker compose up -d --build  (wait ~60s for services to register)
set -euo pipefail
GW=http://localhost:8080
KC=http://localhost:8180/realms/bmtickets/protocol/openid-connect/token

token() { curl -s -X POST "$KC" -d grant_type=password -d client_id=bmtickets-web -d username="$1" -d password="$2" | jq -r .access_token; }
ADMIN=$(token admin admin123)
ALICE=$(token alice alice123)

echo "1) admin creates venue, event and show"
VENUE=$(curl -s -X POST $GW/api/v1/catalog/venues -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -d '{"name":"PVR Phoenix","city":"Mumbai","address":"Lower Parel","capacity":60}' | jq -r .id)
EVENT=$(curl -s -X POST $GW/api/v1/catalog/events -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -d '{"title":"Interstellar (Re-release)","category":"MOVIE","language":"English","durationMinutes":169}' | jq -r .id)
START=$(date -u -d '+2 days' +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -v+2d +%Y-%m-%dT%H:%M:%SZ)
SHOW=$(curl -s -X POST $GW/api/v1/catalog/shows -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -d "{\"eventId\":\"$EVENT\",\"venueId\":\"$VENUE\",\"startTime\":\"$START\",\"currency\":\"INR\",\"maxSeatsPerBooking\":6,
       \"priceByCategory\":{\"GOLD\":350.00,\"RECLINER\":750.00},
       \"sections\":[{\"category\":\"GOLD\",\"rows\":[\"A\",\"B\",\"C\"],\"seatsPerRow\":15},{\"category\":\"RECLINER\",\"rows\":[\"D\"],\"seatsPerRow\":15}]}" | jq -r .id)
echo "   show=$SHOW"; sleep 3

echo "2) seat map (public)"; curl -s $GW/api/v1/shows/$SHOW/seats | jq '{available, sample: .seats[0:3]}'

echo "3) alice books A-1, A-2 (twice with the same Idempotency-Key => one booking)"
KEY=$(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)
BODY="{\"showId\":\"$SHOW\",\"seats\":[\"A-1\",\"A-2\"]}"
R1=$(curl -s -i -X POST $GW/api/v1/bookings -H "Authorization: Bearer $ALICE" -H "Idempotency-Key: $KEY" -H 'Content-Type: application/json' -d "$BODY")
R2=$(curl -s -i -X POST $GW/api/v1/bookings -H "Authorization: Bearer $ALICE" -H "Idempotency-Key: $KEY" -H 'Content-Type: application/json' -d "$BODY")
echo "$R2" | grep -i idempotent-replayed
BOOKING=$(echo "$R1" | sed -n '/^{/,$p' | jq -r .id); echo "   booking=$BOOKING"

echo "4) admin tries the same seats => 409 SEATS_UNAVAILABLE"
curl -s -X POST $GW/api/v1/bookings -H "Authorization: Bearer $ADMIN" -H "Idempotency-Key: $(cat /proc/sys/kernel/random/uuid 2>/dev/null || uuidgen)" \
  -H 'Content-Type: application/json' -d "$BODY" | jq '{status, code, unavailableSeats}'

echo "5) simulate the Stripe webhook (fake provider) and wait for the saga"
curl -s -X POST $GW/api/v1/payments/dev/bookings/$BOOKING/succeed -H "Authorization: Bearer $ALICE" -o /dev/null -w "   %{http_code}\n"
sleep 3
curl -s $GW/api/v1/bookings/$BOOKING -H "Authorization: Bearer $ALICE" | jq '{status, seats, totalAmount, ticketCode}'
echo "   ticket e-mail: docker compose logs notification-service | grep EMAIL"

echo "6) rate limiting: burst booking calls => some 429"
for i in $(seq 1 12); do
  curl -s -o /dev/null -w "%{http_code} " -X POST $GW/api/v1/bookings -H "Authorization: Bearer $ALICE" \
    -H "Idempotency-Key: $(cat /proc/sys/kernel/random/uuid 2>/dev/null || uuidgen)" -H 'Content-Type: application/json' -d "$BODY"
done; echo
