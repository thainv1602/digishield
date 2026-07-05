# Running DigiShield "prod-like"

A runnable, **prod-LIKE** configuration: the backend boots against a **real
PostgreSQL + Flyway** stack (not H2), exercising the actual migration path, while
keeping the permissive dev security and demo seeders so the React frontend works
end-to-end today — before the JWT issuer is built.

## 1. Start the backend stack (one command)

From `digishield/`:

```bash
docker compose -f deploy/compose/docker-compose.prodlike.yml up --build
```

This starts `postgres` (16-alpine), `redis`, `rabbitmq`, and `api`. The api waits
for Postgres to be healthy, then:

- runs with the combined profile **`dev,pgdemo`**;
- **Flyway applies `V2026.06.27.001` (init) + `V2026.06.27.002` (fe_alignment)**
  automatically on api startup (no separate migrate job);
- Hibernate validates the schema (`ddl-auto: validate`);
- the **dev seeders populate demo data** under tenant
  `11111111-1111-1111-1111-111111111111`.

The api connects as the **`digishield` Postgres superuser**, which **bypasses
row-level security** (even FORCE RLS). That is intentional: this is a
**single-tenant demo**, the dev RLS aspect is off and never sets the
`app.tenant_id` GUC, so the superuser bypass is what lets the seeders and queries
work.

API base URL: `http://localhost:8080/api/v1`
(RabbitMQ management UI: `http://localhost:15672`.)

## 2. Run the frontend

In `frontend/`, point the app at the running backend and start the Vite dev
server (CORS for `http://localhost:5173` is already allowed by
DevSecurityConfig / CorsConfig):

```bash
# frontend/.env (or .env.local)
VITE_API_BASE_URL=http://localhost:8080/api/v1
```

```bash
npm install
npm run dev          # http://localhost:5173
```

Optionally, to serve a production build locally (still hitting :8080):

```bash
npm run build && npm run preview
```

## 3. Teardown

```bash
docker compose -f deploy/compose/docker-compose.prodlike.yml down -v
```

`-v` also drops the `postgres-data` volume, so the next `up` starts from a clean
database and re-runs the seeders.

## This is prod-LIKE, not full prod

- **Auth is permissive here** because prod-like runs the `dev` profile, whose
  `DevSecurityConfig` lets requests through without a token. The prod
  `SecurityConfig` (`@Profile("!dev")`) is now a real JWT resource server: point
  `AUTH_JWT_ISSUER_URI` at your IdP's OpenID issuer (for Cognito,
  `https://cognito-idp.<region>.amazonaws.com/<userPoolId>`) and it validates
  signature + issuer + expiry and maps the `cognito:groups` claim to `ROLE_*`.
  With no issuer set, a non-`dev` profile fails closed (actuator only).
- **Demo data is seeded** by the dev seeders, and a single demo tenant is pinned.
- **RLS is effectively off** because the app connects as a superuser.

Before true production, you must: provision the IdP/user pool and set
`AUTH_JWT_ISSUER_URI` (+ optional `AUTH_JWT_AUDIENCE`), run a non-`dev` profile so
the prod `SecurityConfig` is active, and ensure the IdP emits the `tid` (tenant)
and group claims; remove the dev seeders and permissive security; connect as a
**non-superuser** role so RLS is enforced; and keep `ddl-auto: validate` (already
the pgdemo/prod setting) against the verified schema.

---

## Tóm tắt (Tiếng Việt)

Đây là cấu hình **giống production** để chạy thử: backend chạy trên
**PostgreSQL + Flyway thật** (không phải H2), nhưng vẫn giữ bảo mật dev dễ dãi và
dữ liệu demo để frontend React hoạt động đầu-cuối ngay bây giờ.

- **Khởi động toàn bộ:**
  `docker compose -f deploy/compose/docker-compose.prodlike.yml up --build`
  (Flyway tự chạy `V001` + `V002` khi api khởi động; seeder nạp dữ liệu demo cho
  tenant `1111...`; api kết nối bằng superuser `digishield` nên RLS bị bỏ qua —
  hợp lý cho demo một tenant.)
- **Frontend:** trong `frontend/`, đặt
  `VITE_API_BASE_URL=http://localhost:8080/api/v1` rồi chạy `npm run dev`
  (chạy ở `http://localhost:5173`, CORS đã được cho phép). Có thể dùng
  `npm run build && npm run preview`.
- **Lưu ý:** đây là **prod-LIKE, chưa phải prod thật** — chạy profile `dev` nên
  bảo mật còn dễ dãi và dữ liệu là demo. JWT resource server đã sẵn sàng ở
  `SecurityConfig` (`@Profile("!dev")`): đặt `AUTH_JWT_ISSUER_URI` trỏ tới OpenID
  issuer của IdP (Cognito: `https://cognito-idp.<region>.amazonaws.com/<userPoolId>`)
  là bật xác thực thật. Trước khi lên production thật: cấp phát IdP + đặt
  `AUTH_JWT_ISSUER_URI`, chạy profile khác `dev`, bỏ seeder + bảo mật dev, kết nối
  bằng user **không phải superuser** để bật RLS, và giữ `ddl-auto: validate`.
- **Dọn dẹp:** `docker compose -f deploy/compose/docker-compose.prodlike.yml down -v`
