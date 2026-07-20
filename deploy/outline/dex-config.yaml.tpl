issuer: https://outline.8.166.121.138.sslip.io/dex
storage:
  type: sqlite3
  config:
    file: /var/dex/dex.db
web:
  http: 0.0.0.0:5556
telemetry:
  http: 0.0.0.0:5558
oauth2:
  skipApprovalScreen: true
enablePasswordDB: true
staticClients:
  - id: outline
    name: Outline
    secret: __DEX_OIDC_CLIENT_SECRET__
    redirectURIs:
      - https://outline.8.166.121.138.sslip.io/auth/oidc.callback
staticPasswords:
  - email: outline-admin@zhilu.local
    hash: "__DEX_ADMIN_PASSWORD_HASH__"
    username: outline-admin
    userID: __DEX_ADMIN_USER_ID__
frontend:
  issuer: 智鹿 Outline
