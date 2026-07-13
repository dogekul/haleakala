FROM node:20-alpine
WORKDIR /app
COPY server.mjs .
EXPOSE 8090
HEALTHCHECK --interval=10s --timeout=3s --retries=10 CMD wget -qO- http://127.0.0.1:8090/health >/dev/null || exit 1
CMD ["node","server.mjs"]
