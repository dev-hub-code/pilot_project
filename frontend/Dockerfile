# Production image of the web app (Next.js standalone server). Build from frontend/: docker build -t sealease-frontend .
FROM node:24-alpine AS deps
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci

FROM node:24-alpine AS build
WORKDIR /src
COPY --from=deps /src/node_modules node_modules
COPY . .
ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build

FROM node:24-alpine
WORKDIR /app
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 HOSTNAME=0.0.0.0 PORT=3000
COPY --from=build --chown=node:node /src/.next/standalone ./
COPY --from=build --chown=node:node /src/.next/static .next/static
COPY --from=build --chown=node:node /src/public public
USER node
EXPOSE 3000
CMD ["node", "server.js"]
