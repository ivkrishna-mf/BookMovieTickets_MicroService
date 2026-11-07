# syntax=docker/dockerfile:1.7
# ---------------------------------------------------------------- build (tests gate the image)
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
ARG SKIP_TESTS=false
RUN if [ "$SKIP_TESTS" != "true" ]; then npm run lint && npm test; fi
# The dev config.js is not shipped: the container serves /tmp/config.js generated at startup.
RUN npm run build && rm -f dist/config.js

# ---------------------------------------------------------------- runtime (non-root nginx, ~50 MB)
FROM nginxinc/nginx-unprivileged:1.27-alpine
# Defaults for local docker compose; override per environment (one immutable image for dev/stage/prod).
ENV API_UPSTREAM=http://api-gateway:8080 \
    CSP_CONNECT_SRC="http://localhost:8180 https://api.stripe.com" \
    BMT_API_BASE_URL="" \
    BMT_AUTH_URL=http://localhost:8180/realms/bmtickets \
    BMT_CLIENT_ID=bmtickets-web \
    BMT_PAYMENT_MODE=fake \
    BMT_STRIPE_PUBLISHABLE_KEY="" \
    BMT_DEFAULT_CITY=Mumbai \
    BMT_CITIES="Mumbai,Delhi,Bengaluru,Hyderabad,Chennai,Pune,Kolkata"
COPY nginx/*.template /etc/nginx/templates/
COPY --chmod=755 docker/15-resolver.envsh docker/40-runtime-config.sh /docker-entrypoint.d/
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://127.0.0.1:8080/healthz || exit 1
