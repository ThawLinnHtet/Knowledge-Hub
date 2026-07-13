import { expect, test } from '@playwright/test'

test('registers, uploads, searches, chats with citations, and deletes a document', async ({
  page,
}) => {
  const email = `e2e-${Date.now()}@example.com`
  const password = 'correct-horse-battery-staple'

  await page.goto('/')
  await page.getByRole('link', { name: 'Create your account' }).click()
  await page.getByLabel('Email address').fill(email)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(
    page.getByText('Account created. Sign in to continue.'),
  ).toBeVisible()

  await page.getByLabel('Email address').fill(email)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('heading', { name: 'Documents' })).toBeVisible()

  await page.getByLabel('Choose documents').setInputFiles({
    name: 'operations.txt',
    mimeType: 'text/plain',
    buffer: Buffer.from(
      'The retry policy uses bounded exponential backoff for transient failures.',
    ),
  })
  await page.getByRole('button', { name: 'Review upload' }).click()
  await page.getByRole('button', { name: 'Upload 1 document' }).click()
  await expect(page.getByText('1 document uploaded')).toBeVisible()
  await expect(
    page
      .getByRole('button', { name: 'View operations.txt' })
      .getByText('Ready'),
  ).toBeVisible({ timeout: 45_000 })

  await page.getByRole('link', { name: 'Search', exact: true }).click()
  await page.getByLabel('Search your library').fill('bounded retry policy')
  await page.getByRole('button', { name: 'Search', exact: true }).click()
  await expect(page.getByText('operations.txt')).toBeVisible()
  await expect(page.getByText(/bounded exponential backoff/i)).toBeVisible()

  await page.getByRole('link', { name: 'Chat', exact: true }).click()
  await page.getByRole('button', { name: 'New conversation' }).click()
  await page
    .getByRole('textbox', { name: 'Message', exact: true })
    .fill('What is the retry policy?')
  await page.getByRole('button', { name: 'Send message' }).click()
  await expect(page.getByRole('link', { name: /operations.txt/i })).toBeVisible(
    {
      timeout: 30_000,
    },
  )

  await page.getByRole('link', { name: 'Documents', exact: true }).click()
  await page.getByRole('button', { name: 'View operations.txt' }).click()
  await page.getByRole('button', { name: 'Delete document' }).click()
  await page.getByRole('button', { name: 'Delete permanently' }).click()
  await expect(page.getByText('operations.txt')).not.toBeVisible()
})
