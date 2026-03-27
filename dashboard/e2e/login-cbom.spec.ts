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

// 시나리오 ①: 로그인 → CBOM 목록 진입 → 알고리즘 필터 동작 확인
test.describe('로그인 → CBOM 목록 → 알고리즘 필터', () => {
  test('로그인 후 사이드바로 CBOM 목록 진입', async ({ page }) => {
    await login(page)
    // SPA 내비게이션 — page.goto() 대신 NavLink 클릭
    await page.click('a[href="/cbom"]')
    await page.waitForSelector('h1:has-text("CBOM 목록")')
    await expect(page.locator('h1')).toContainText('CBOM 목록')
    // 데이터 로딩 후 행 확인
    await expect(page.locator('table tbody tr').first()).toBeVisible()
  })

  test('알고리즘 필터 선택 시 목록 건수 변경', async ({ page }) => {
    await login(page)
    await page.click('a[href="/cbom"]')
    await page.waitForSelector('table tbody tr')

    // 전체 건수 확인
    const totalText = await page.locator('text=총').textContent()
    expect(totalText).toMatch(/총 \d+건/)

    // RSA-2048 필터 선택
    await page.selectOption('#algorithm-filter', 'RSA-2048')
    await expect(page.locator('text=총 1건')).toBeVisible()
    await expect(page.locator('table tbody td', { hasText: 'RSA-2048' }).first()).toBeVisible()
  })
})
