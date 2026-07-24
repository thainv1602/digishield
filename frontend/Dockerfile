# syntax=docker/dockerfile:1
# Frontend image for self-hosted clusters (Jetson k3s). Built by cd.yml with
# the REPO ROOT as context (gen:api reads ../docs/DigiShield_openapi.yaml):
#   docker build -f frontend/Dockerfile .
# Vite bakes VITE_* into the bundle at build time. Cognito vars are deliberately
# NOT passed: without them the app falls back to the demo login, which matches
# the API's dev,pgdemo profile on the Jetson cluster.

# ---- Build stage ---------------------------------------------------------
FROM node:20-alpine AS build
WORKDIR /app/frontend

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

# orval reads the OpenAPI spec from ../docs (relative to frontend/).
COPY docs/DigiShield_openapi.yaml /app/docs/DigiShield_openapi.yaml
COPY frontend/ ./

ARG VITE_API_BASE_URL=/api/v1
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run gen:api && npm run build

# ---- Runtime stage -------------------------------------------------------
FROM nginx:1.27-alpine
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/frontend/dist /usr/share/nginx/html
EXPOSE 80
