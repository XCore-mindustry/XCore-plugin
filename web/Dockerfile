FROM oven/bun:1.4.0 AS builder
WORKDIR /workspace/web
COPY web/package.json web/bun.lock ./
RUN bun install --frozen-lockfile
COPY protocol/typescript /workspace/protocol/typescript
COPY web .
RUN bun run build

FROM node:24-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production HOSTNAME=0.0.0.0 PORT=3000
COPY --from=builder --chown=node:node /workspace/web/.next/standalone ./
COPY --from=builder --chown=node:node /workspace/web/.next/static ./web/.next/static
COPY --from=builder --chown=node:node /workspace/web/public ./web/public
USER node
EXPOSE 3000
CMD ["node", "web/server.js"]
