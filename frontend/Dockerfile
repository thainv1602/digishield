# syntax=docker/dockerfile:1
# Frontend image for self-hosted clusters (Jetson k3s). Built by cd.yml with
# the REPO ROOT as context (gen:api reads ../docs/DigiShield_openapi.yaml):
#   docker build -f frontend/Dockerfile .
# Vite bakes VITE_* into the bundle at build time. The Cognito vars are optional:
# when set (via build args, sourced from repo variables in cd.yml) the app uses
# the Cognito hosted UI for login; when empty the login screen shows a
# "not configured" message. The OAuth redirect_uri is derived from
# window.location.origin at runtime, so it is intentionally not baked here.

# ---- Build stage ---------------------------------------------------------
FROM node:20-alpine AS build
WORKDIR /app/frontend

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

# orval reads the OpenAPI spec from ../docs (relative to frontend/).
COPY docs/DigiShield_openapi.yaml /app/docs/DigiShield_openapi.yaml
COPY frontend/ ./

ARG VITE_API_BASE_URL=/api/v1
ARG VITE_COGNITO_AUTHORITY=""
ARG VITE_COGNITO_CLIENT_ID=""
ARG VITE_TENANT_ID=""
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL \
    VITE_COGNITO_AUTHORITY=$VITE_COGNITO_AUTHORITY \
    VITE_COGNITO_CLIENT_ID=$VITE_COGNITO_CLIENT_ID \
    VITE_TENANT_ID=$VITE_TENANT_ID
RUN npm run gen:api && npm run build

# ---- Runtime stage -------------------------------------------------------
FROM nginx:1.27-alpine
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/frontend/dist /usr/share/nginx/html
EXPOSE 80
