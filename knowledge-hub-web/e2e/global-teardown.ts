import { execFileSync } from 'node:child_process'
import path from 'node:path'

export default function globalTeardown() {
  const root = path.resolve(import.meta.dirname, '../..')
  try {
    execFileSync(
      'docker',
      [
        'compose',
        '--project-name',
        'knowledge-hub-e2e',
        '--file',
        path.join(root, 'compose.e2e.yaml'),
        'down',
        '--volumes',
        '--remove-orphans',
      ],
      { cwd: root, stdio: 'inherit' },
    )
  } catch {
    // Preserve the test result when cleanup itself fails.
  }
}
