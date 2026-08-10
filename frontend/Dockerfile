FROM node:20-alpine AS build
WORKDIR /workspace
ARG VITE_OPERATOR_NAME
ARG VITE_LEGAL_CONTACT_EMAIL
ENV VITE_OPERATOR_NAME=$VITE_OPERATOR_NAME
ENV VITE_LEGAL_CONTACT_EMAIL=$VITE_LEGAL_CONTACT_EMAIL
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.31-alpine
COPY nginx-main.conf /etc/nginx/nginx.conf
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=20s --timeout=3s --start-period=10s --retries=5 \
  CMD wget -q -O - http://127.0.0.1/healthz >/dev/null || exit 1
