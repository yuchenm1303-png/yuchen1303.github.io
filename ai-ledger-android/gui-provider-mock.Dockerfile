FROM node:22-alpine

WORKDIR /app

ENV NODE_ENV=production
ENV GUI_PROVIDER_PORT=9100

COPY package.json package-lock.json ./
COPY scripts/gui-provider-mock.js ./scripts/gui-provider-mock.js

EXPOSE 9100

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD node -e "fetch('http://127.0.0.1:' + (process.env.PORT || process.env.GUI_PROVIDER_PORT || 9100)).then(r => process.exit(r.ok ? 0 : 1)).catch(() => process.exit(1))"

USER node

CMD ["npm", "run", "gui-provider:mock"]
