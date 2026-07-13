import { defineConfig, devices } from '@playwright/test'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))
const reuseExistingServer = process.env.E2E_REUSE_EXISTING_SERVER === 'true'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : 'list',
  globalTeardown: './e2e/global-teardown.ts',
  use: {
    baseURL: 'http://127.0.0.1:15173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: [
    {
      command: 'node e2e/start-api.mjs',
      cwd: here,
      url: 'http://127.0.0.1:18080/actuator/health/readiness',
      timeout: 180_000,
      reuseExistingServer,
    },
    {
      command: 'npm run dev -- --host 127.0.0.1 --port 15173',
      cwd: here,
      env: { VITE_API_URL: 'http://127.0.0.1:18080' },
      url: 'http://127.0.0.1:15173',
      timeout: 60_000,
      reuseExistingServer,
    },
  ],
})
