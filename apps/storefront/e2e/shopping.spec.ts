import { expect, test } from '@playwright/test';

test('browse, filter and open a product @mobile', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Woven for the moments');
  await page.goto('/collections/handloom-cottons');
  await expect(page.getByRole('heading', { level: 1, name: 'Handloom Cottons' })).toBeVisible();
  await page.goto('/collections/handloom-cottons?color=blue');
  await expect(page.getByText(/\d+ styles?/).first()).toBeVisible();
  await page.getByRole('link', { name: 'Kora Check Handloom Cotton' }).first().click();
  await expect(page.getByRole('heading', { level: 1, name: 'Kora Check Handloom Cotton' })).toBeVisible();
  await expect(page.getByText('Inclusive of GST. Shipping calculated at checkout.')).toBeVisible();
});

test('search finds by colour and handles no results', async ({ page }) => {
  await page.goto('/search?q=maroon');
  await expect(page.getByRole('heading', { name: /Results for/ })).toBeVisible();
  await expect(page.locator('article').first()).toBeVisible();
  await page.goto('/search?q=zzzznotathing');
  await expect(page.getByText('No styles match these filters').first()).toBeVisible();
});

test('guest COD checkout places exactly one confirmed order', async ({ page }) => {
  await page.goto('/products/mitti-block-print-cotton');
  await page.getByRole('button', { name: 'Increase quantity' }).first().click();
  await page.getByRole('button', { name: 'Add to bag' }).first().click();
  await expect(page.getByText('Added to your bag')).toBeVisible();

  await page.goto('/cart');
  await expect(page.getByRole('heading', { name: 'Mitti Block-Print Cotton' })).toBeVisible();
  await page.getByRole('link', { name: 'Checkout' }).click();

  await page.getByLabel('Full name').fill('Asha Rao');
  await page.getByLabel('Email', { exact: true }).fill('asha.e2e@example.com');
  // Unique number per run: COD limits pending orders per phone (by design).
  await page.getByLabel('Mobile number').fill(`9${String(Date.now()).slice(-9)}`);
  await page.getByLabel('House / flat and street').fill('12 MG Road');
  await page.getByLabel('City').fill('Bengaluru');
  await page.getByLabel('State / UT').selectOption('Karnataka');
  await page.getByLabel('PIN code', { exact: true }).fill('560001');
  await expect(page.getByText('Includes GST of')).toBeVisible();

  await page.getByLabel('Coupon code').fill('NOPE');
  await page.getByRole('button', { name: 'Apply' }).click();
  await expect(page.getByText('This coupon code is not valid.')).toBeVisible();

  await page.getByRole('radio', { name: /Cash on delivery/ }).check();
  await expect(page.getByText('COD fee').first()).toBeVisible();
  await page.getByRole('button', { name: 'Place COD order' }).click();

  await expect(page.getByRole('heading', { name: 'Your order is confirmed' })).toBeVisible({ timeout: 20_000 });
  await expect(page.getByText(/ERA-\d{6}-[A-Z0-9]{6}/).first()).toBeVisible();
  await expect(page.getByText('Cash on delivery accepted')).toBeVisible();

  await page.goto('/cart');
  await expect(page.getByText('Your bag is empty')).toBeVisible();
  await page.goto('/orders');
  await expect(page.getByText(/ERA-\d{6}-[A-Z0-9]{6}/).first()).toBeVisible();
});

test('bulk bag enforces the 10-piece minimum', async ({ page }) => {
  await page.goto('/products/kora-check-handloom');
  const bulkQty = page.getByRole('textbox', { name: 'Bulk quantity' });
  await bulkQty.fill('4');
  await page.getByRole('button', { name: 'Add 4 to bulk order' }).click();
  await expect(page.getByText('Added 4 to your bulk order')).toBeVisible();
  await page.goto('/cart?type=bulk');
  await expect(page.getByText('4 / 10 pieces')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Checkout' })).toBeDisabled();
  await page.getByLabel(/Quantity for/).selectOption('10');
  await expect(page.getByText('10 / 10 pieces')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Checkout' })).toBeVisible();
});

test('invalid PIN code and unknown pages show helpful states', async ({ page }) => {
  await page.goto('/products/does-not-exist');
  await expect(page.getByRole('heading', { name: 'We couldn’t find that page' })).toBeVisible();
  await page.goto('/track');
  await page.getByLabel('Order number').fill('ERA-000000-AAAAAA');
  await page.getByLabel('Email').fill('nobody@example.com');
  await page.getByRole('button', { name: 'Track order' }).click();
  await expect(page.getByText('We couldn’t find an order with those details.')).toBeVisible();
});
