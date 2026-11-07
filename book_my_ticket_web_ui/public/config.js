// Local development runtime configuration. In containers this file is regenerated
// from environment variables by docker/40-runtime-config.sh (one image for all environments).
window.__BMT_CONFIG__ = {
  apiBaseUrl: '',                                        // '' = same origin (/api proxied to the gateway)
  authUrl: 'http://localhost:8180/realms/bmtickets',     // OIDC issuer (Keycloak locally, Cognito in AWS)
  clientId: 'bmtickets-web',
  paymentMode: 'fake',                                   // 'fake' (dev webhook simulator) | 'stripe'
  stripePublishableKey: '',
  defaultCity: 'Mumbai',
  cities: ['Mumbai', 'Delhi', 'Bengaluru', 'Hyderabad', 'Chennai', 'Pune', 'Kolkata']
};
