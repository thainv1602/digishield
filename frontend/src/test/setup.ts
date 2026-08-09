import '@testing-library/jest-dom/vitest';
import { afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';

// jsdom under Node 26 exposes neither `localStorage` nor `window.localStorage`.
// The app guards every read with `typeof localStorage !== 'undefined'`, so
// nothing crashes — it silently takes the "no storage" branch instead. That
// makes the stored language, the acting-tenant header and anything else built
// on Storage untestable, and worse, makes them *look* covered while only the
// fallback ever runs. Install a minimal in-memory Storage so tests exercise the
// same path a browser does.
if (typeof globalThis.localStorage === 'undefined') {
  const store = new Map<string, string>();
  const memoryStorage = {
    get length() {
      return store.size;
    },
    clear: () => store.clear(),
    getItem: (key: string) => (store.has(key) ? (store.get(key) as string) : null),
    key: (index: number) => Array.from(store.keys())[index] ?? null,
    removeItem: (key: string) => void store.delete(key),
    setItem: (key: string, value: string) => void store.set(key, String(value)),
  } as unknown as Storage;

  Object.defineProperty(globalThis, 'localStorage', {
    value: memoryStorage,
    configurable: true,
  });
  if (typeof window !== 'undefined') {
    Object.defineProperty(window, 'localStorage', {
      value: memoryStorage,
      configurable: true,
    });
  }
}

// Unmount React trees and clear the DOM after every test.
afterEach(() => {
  cleanup();
  localStorage.clear();
});
