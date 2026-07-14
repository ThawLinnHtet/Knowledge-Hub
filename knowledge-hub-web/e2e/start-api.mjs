import { execFileSync, spawn } from 'node:child_process'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

const webRoot = path.dirname(path.dirname(fileURLToPath(import.meta.url)))
const root = path.dirname(webRoot)
const apiRoot = path.join(root, 'knowledge-hub-api')
const compose = path.join(root, 'compose.e2e.yaml')
const project = 'knowledge-hub-e2e'

execFileSync(
  'docker',
  [
    'compose',
    '--project-name',
    project,
    '--file',
    compose,
    'up',
    '--detach',
    '--wait',
  ],
  { cwd: root, stdio: 'inherit' },
)

const executable = process.platform === 'win32' ? 'mvnw.cmd' : './mvnw'
const api = spawn(executable, ['spring-boot:run'], {
  cwd: apiRoot,
  env: {
    ...process.env,
    AI_FAKE_MODE: 'true',
    AI_MODEL_CHAT: 'none',
    AI_MODEL_EMBEDDING: 'none',
    AUTH_LOG_RESET_TOKENS: 'false',
    APP_BASE_URL: 'http://127.0.0.1:18080',
    APP_WEB_URL: 'http://127.0.0.1:15173',
    CORS_ALLOWED_ORIGINS: 'http://127.0.0.1:15173',
    DATABASE_URL: 'jdbc:postgresql://127.0.0.1:55433/knowledgehub',
    INGESTION_INITIAL_DELAY: '1s',
    INGESTION_POLL_DELAY: '1s',
    JWT_SECRET: 'e2e-only-secret-that-is-at-least-32-bytes',
    MINIO_ACCESS_KEY: 'kh_e2e',
    MINIO_ENDPOINT: 'http://127.0.0.1:59000',
    MINIO_SECRET_KEY: 'kh_e2e_minio_secret',
    POSTGRES_PASSWORD: 'kh_e2e_secret',
    POSTGRES_USER: 'kh_e2e',
    SERVER_PORT: '18080',
    SPRING_DOCKER_COMPOSE_ENABLED: 'false',
  },
  shell: process.platform === 'win32',
  stdio: 'inherit',
})

const stop = () => api.kill('SIGTERM')
process.on('SIGINT', stop)
process.on('SIGTERM', stop)
api.on('exit', (code) => process.exit(code ?? 1))
