import '@testing-library/jest-dom/vitest';
import {cleanup} from '@testing-library/react';
import {afterEach} from 'vitest';

const storage = new Map<string, string>();
const localStorageStub: Storage = {
  get length() {
    return storage.size;
  },
  clear() {
    storage.clear();
  },
  getItem(key) {
    return storage.get(key) ?? null;
  },
  key(index) {
    return [...storage.keys()][index] ?? null;
  },
  removeItem(key) {
    storage.delete(key);
  },
  setItem(key, value) {
    storage.set(key, value);
  },
};

Object.defineProperty(globalThis, 'localStorage', {
  configurable: true,
  value: localStorageStub,
});

afterEach(() => {
  cleanup();
  localStorage.clear();
});
