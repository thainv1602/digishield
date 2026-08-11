# Generated API client (do not edit by hand)

Everything under `src/api/generated/` is produced by [orval](https://orval.dev)
from the OpenAPI specification and **should not be hand-edited or committed**
(it is git-ignored except for this file and `.gitkeep`).

## How to generate

```bash
npm run gen:api
```

This runs `scripts/gen-api.mjs`, a thin wrapper around
`orval --config ./orval.config.ts` that fails the build when orval does not
finish (orval reports its own errors and still exits 0) and empties this
directory first, since orval overwrites but never prunes. The generation
itself:

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

## Nothing imports this yet

No component imports the generated client: every feature hand-writes its own
`src/features/*/api.ts` on top of `apiRequest`. What this tree buys today is a
contract check - `tsconfig.json` includes all of `src`, so `npm run typecheck`
compiles these files even though nothing references them, and a spec that
cannot produce a client that compiles fails CI. It is not proof that the
hand-written fetchers agree with the spec; nothing checks that yet.

## Usage example (after generation)

```ts
// import paths depend on the OpenAPI operationId / tag
import { useGetMe } from '@/api/generated/auth/auth';

function Profile() {
  const { data, isLoading } = useGetMe();
  // ...
}
```
