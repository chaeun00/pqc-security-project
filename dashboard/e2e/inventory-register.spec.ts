import { test, expect } from '@playwright/test'

const E2E_USER = process.env.E2E_USER ?? 'admin'
const E2E_PASS = process.env.E2E_PASS ?? 'password'

async function login(page: import('@playwright/test').Page) {
  await page.goto('/login')
  await page.waitForSelector('text=PQC 로그인')
  await page.fill('input[placeholder="admin"]', E2E_USER)
  await page.fill('input[type="password"]', E2E_PASS)
  await page.click('button[type="submit"]')
  await page.waitForURL('**/dashboard')
}

// 시나리오 ②: 수동 등록 폼 작성 → 저장 → 목록 반영 확인
test.describe('Inventory 수동 등록', () => {
  test('등록 폼 작성 후 저장 → 목록에 신규 항목 반영', async ({ page }) => {
    await login(page)
    // SPA 내비게이션 — page.goto() 대신 NavLink 클릭
    await page.click('a[href="/inventory"]')
    await page.waitForSelector('text=TLS Certificate')

    await page.fill('input[placeholder="이름"]', 'E2E Test Key')
    await page.fill('input[placeholder="알고리즘"]', 'ML-KEM-512')
    await page.fill('input[placeholder="위치"]', 'e2e-service')

    const registerBtn = page.getByRole('button', { name: '등록' })
    await expect(registerBtn).toBeEnabled()
    await registerBtn.click()

    await expect(page.locator('text=E2E Test Key')).toBeVisible()
  })
})
