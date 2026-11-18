FROM node:24-alpine AS build
WORKDIR /app
COPY auction-frontend/package*.json ./
RUN npm ci
COPY auction-frontend/ ./
RUN npm test && npm run build

FROM nginxinc/nginx-unprivileged:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 8080
