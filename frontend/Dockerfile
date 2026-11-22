FROM node:24.16.0-alpine AS build

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginx:1.30.7-alpine@sha256:caf4e1e032a20f9c3c5e28fc7f25ffd768ad8677b4975c54193075997b3a97e3 AS runtime

RUN apk add --no-cache ca-certificates
ENV NGINX_ENVSUBST_FILTER="^RF_PROXY_"
COPY --chmod=755 nginx/15-ruta-fija-proxy.envsh /docker-entrypoint.d/15-ruta-fija-proxy.envsh
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist/ruta-fija-crm/browser /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -q -O - "http://127.0.0.1:${PORT:-80}/health" || exit 1
