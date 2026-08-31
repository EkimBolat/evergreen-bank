<p align="center">
  <img src="./EverGreen_Bank_Logo.png" alt="Evergreen Bank Logo" width="200"/>
</p>

# 🌲 Evergreen Bank API

[![CI](https://github.com/EkimBolat/evergreen-bank/actions/workflows/ci.yml/badge.svg)](https://github.com/EkimBolat/evergreen-bank/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-6DB33F?logo=springboot&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)

A full-stack banking app — Spring Boot + PostgreSQL API, React frontend. Every deposit, withdrawal, and transfer earns **Nature Points**; once a customer earns enough, a real tree gets planted on their behalf.

**🔗 Live demo:** [my-evergreen-bank.onrender.com](https://my-evergreen-bank.onrender.com) — free-tier hosting, first request after a while can take ~30-50s to wake up.

---

## 🔀 Domain Flow

<p align="center">
  <img src="./evergreen-bank-architecture.png" alt="Evergreen Bank Domain Flow" width="800"/>
</p>

---

## ✨ Key Features

- 🔐 JWT auth with role-based access control (Customer / Admin) + TOTP two-factor
- 🏦 Accounts, transfers, scheduled/recurring transfers, monthly interest accrual
- 💳 Debit & credit cards — issue, block, cancel, statements, billing, late interest
- 🌱 Nature Points & tree certificates, with anti-abuse safeguards
- 🛡️ Brute-force lockout, daily/monthly withdrawal limits, idempotent requests
- 🛠️ Admin panel: customers, branches, account opening, audit log
- 🔔 Live in-app notifications over WebSocket
- 📄 PDF statement export
- 🐳 Dockerized, fully tested, documented via Swagger

---

## 🛠️ Tech Stack

**Backend:** Java 21 · Spring Boot 4.1.0 · PostgreSQL · Spring Data JPA · Spring Security (JWT) · WebSocket (STOMP) · Apache PDFBox

**Frontend:** React · TypeScript · Vite · Tailwind CSS · nginx (production)

**Infra:** Docker Compose (one command locally) · deployable split across hosts (frontend/backend/DB on different domains) via env-configurable CORS and API origin

---

## 🚀 Getting Started

```bash
git clone https://github.com/EkimBolat/evergreen-bank.git
cd evergreen-bank
```

Copy `.env.example`, set your own `DB_PASSWORD` and `JWT_SECRET`, then run:

```bash
docker compose up --build
```

- **Web app:** `http://localhost:3000`
- **API:** `http://localhost:8080` · docs at `http://localhost:8080/swagger-ui/index.html`

A default admin account is seeded on first startup (see `admin.bootstrap.*` in `.env.example`) — there's no self-service customer signup, so that's your way in.

---

📄 **License:** [MIT](./LICENSE)
