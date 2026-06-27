# Generated API client (do not edit by hand)

Everything under `src/api/generated/` is produced by [orval](https://orval.dev)
from the OpenAPI specification and **should not be hand-edited or committed**
(it is git-ignored except for this file and `.gitkeep`).

## How to generate

```bash
npm run gen:api
```

This runs `orval --config ./orval.config.ts`, which:

1. Reads the spec at `../docs/DigiShield_openapi.yaml`.
2. Emits fully typed TanStack Query (react-query) hooks here, split per OpenAPI
   tag (e.g. `auth/`, `learning/`, `reports/`), plus TypeScript models under
   `model/`.
3. Wires every request through our hand-written axios instance
   (`src/shared/api/client.ts`, exported as `apiRequest`) — the orval "mutator".
   That instance applies `baseURL` (`VITE_API_BASE_URL`), the
   `Authorization: Bearer <token>` and `X-Tenant-Id` headers, and 401 handling.

## Why it is not committed

The generated client is a deterministic build artifact of the spec. Regenerate
it after `npm install` (or wire `gen:api` into a `postinstall` / CI step) so the
client always matches the contract in `docs/DigiShield_openapi.yaml`.

## Usage example (after generation)

```ts
// import paths depend on the OpenAPI operationId / tag
import { useGetMe } from '@/api/generated/auth/auth';

function Profile() {
  const { data, isLoading } = useGetMe();
  // ...
}
```
