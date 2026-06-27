import { defineConfig } from 'orval';

/**
 * Orval configuration for DigiShield.
 *
 * Generates a fully typed TanStack Query (react-query) client from the
 * OpenAPI spec at ../docs/DigiShield_openapi.yaml into src/api/generated.
 *
 * The generated hooks delegate every HTTP call to our hand-written axios
 * instance (the "mutator") at src/shared/api/client.ts, so auth headers
 * (Authorization: Bearer, X-Tenant-Id), baseURL and 401 handling are applied
 * uniformly. The baseURL itself comes from import.meta.env.VITE_API_BASE_URL.
 *
 * Run with: `npm run gen:api`
 */
export default defineConfig({
  digishield: {
    input: {
      target: '../docs/DigiShield_openapi.yaml',
    },
    output: {
      mode: 'tags-split',
      target: './src/api/generated',
      schemas: './src/api/generated/model',
      client: 'react-query',
      // Prettier-format the generated output.
      prettier: true,
      override: {
        // All requests go through our custom axios instance.
        mutator: {
          path: './src/shared/api/client.ts',
          name: 'apiRequest',
        },
        query: {
          useQuery: true,
          useInfinite: false,
          signal: true,
        },
      },
    },
    hooks: {
      afterAllFilesWrite: 'prettier --write',
    },
  },
});
