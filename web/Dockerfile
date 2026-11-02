FROM oven/bun:1.4.0 AS builder
WORKDIR /workspace/xcore-web
COPY package.json bun.lock ./
RUN bun install --frozen-lockfile
COPY --from=xcore_protocol /typescript /workspace/xcore-protocol/typescript
COPY . .
RUN bun run build

FROM node:24-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production HOSTNAME=0.0.0.0 PORT=3000
COPY --from=builder --chown=node:node /workspace/xcore-web/.next/standalone ./
COPY --from=builder --chown=node:node /workspace/xcore-web/.next/static ./.next/static
COPY --from=builder --chown=node:node /workspace/xcore-web/public ./public
USER node
EXPOSE 3000
CMD ["node", "server.js"]
