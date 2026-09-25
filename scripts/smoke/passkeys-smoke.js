const fs = require("fs");
const path = require("path");

function loadPlaywright() {
  try {
    return require("playwright");
  } catch (error) {
    const candidates = [
      process.env.PLAYWRIGHT_PATH,
      process.env.USERPROFILE && path.join(
        process.env.USERPROFILE,
        ".cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright"
      ),
      path.resolve(__dirname, "../../.run-logs/stage13-visual-pass/node_modules/playwright")
    ].filter(Boolean);
    const available = candidates.find((candidate) => fs.existsSync(candidate));
    if (!available) throw error;
    return require(available);
  }
}


const assert = require("node:assert/strict");
const { chromium } = loadPlaywright();
const baseUrl = process.env.QA_BASE_URL || "http://localhost:18080";
const outDir = path.resolve(process.env.QA_OUT_DIR || "build/passkeys-smoke");
// Этот сценарий создаёт аккаунт. Запускаем только на локальной тестовой БД.
assert(["localhost", "127.0.0.1"].includes(new URL(baseUrl).hostname), "Use a local test instance");
fs.mkdirSync(outDir, { recursive: true });

(async () => {
  const browser = await chromium.launch({
    headless: true,
    executablePath: process.env.CHROME_PATH || "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
  });
  try {
    const context = await browser.newContext({ viewport: { width: 390, height: 844 } });
    const page = await context.newPage();
    const errors = [];
    page.on("pageerror", error => errors.push(error.message));
    const cdp = await context.newCDPSession(page);
    await cdp.send("WebAuthn.enable");
    const { authenticatorId } = await cdp.send("WebAuthn.addVirtualAuthenticator", {
      options: { protocol: "ctap2", transport: "internal", hasResidentKey: true,
        hasUserVerification: true, isUserVerified: true, automaticPresenceSimulation: true }
    });

    await page.goto(baseUrl + "/register");
    await page.locator("#email").fill("biometric-smoke-" + Date.now() + "@example.com");
    await page.locator("#password").fill("password123");
    await page.locator("#fullName").fill("Проверка биометрии");
    await page.locator("#gradeNo").fill("4");
    await page.locator('form[action="/register"] button[type="submit"]').click();
    await page.waitForURL("**/passkeys/setup");
    // Предложение не должно создавать ключ без согласия пользователя.
    assert.equal((await cdp.send("WebAuthn.getCredentials", { authenticatorId })).credentials.length, 0);
    await page.screenshot({ path: path.join(outDir, "setup.png"), fullPage: true });
    await page.locator("#passkey-register-button").click();
    await page.waitForFunction(() => document.getElementById("passkey-register-status").textContent.includes("Готово"));
    assert.equal((await cdp.send("WebAuthn.getCredentials", { authenticatorId })).credentials.length, 1);

    // Cookie должна пережить закрытие PWA и истечь примерно через заданные 36 часов.
    const trustedCookie = (await context.cookies(baseUrl)).find(cookie => cookie.name === "JSESSIONID");
    const remainingCookieSeconds = trustedCookie.expires - Date.now() / 1000;
    assert(remainingCookieSeconds > 35.9 * 60 * 60 && remainingCookieSeconds <= 36 * 60 * 60);

    // Вход работает как с действующей сессией, так и после её удаления.
    await page.goto(baseUrl + "/app");
    await page.waitForURL("**/student");
    await context.clearCookies();
    await page.goto(baseUrl + "/app");
    await page.waitForURL("**/student");
    await page.goto(baseUrl + "/passkeys/setup");
    await page.locator("#passkey-login-button").click();
    await page.waitForURL("**/student");

    // Отмена не зацикливает системное окно; доступны повтор и полноценная форма пароля.
    const cancelContext = await browser.newContext({ viewport: { width: 390, height: 844 } });
    const cancelPage = await cancelContext.newPage();
    await cancelPage.addInitScript(() => {
      localStorage.setItem("damulab-biometric-enabled", "1");
      window.biometricAttempts = 0;
      navigator.credentials.get = async () => {
        window.biometricAttempts++;
        throw new DOMException("cancelled", "NotAllowedError");
      };
    });
    await cancelPage.goto(baseUrl + "/app");
    await cancelPage.waitForFunction(() => document.getElementById("passkey-login-status").classList.contains("error"));
    assert.equal(await cancelPage.evaluate(() => window.biometricAttempts), 1);
    await cancelPage.screenshot({ path: path.join(outDir, "entry-cancelled.png"), fullPage: true });
    await cancelPage.locator("#passkey-login-button").click();
    await cancelPage.waitForFunction(() => window.biometricAttempts === 2);
    await cancelPage.getByRole("link", { name: "Войти по паролю или через Google" }).click();
    await cancelPage.locator("#password").waitFor({ state: "visible" });
    await cancelContext.close();

    // Эмулируем установленное PWA и возврат из фона, без зависимости от окон рабочего стола.
    await page.addInitScript(() => {
      const original = window.matchMedia.bind(window);
      window.matchMedia = query => query === "(display-mode: standalone)" ? { matches: true } : original(query);
    });
    await page.goto(baseUrl + "/student");
    const resume = page.waitForRequest(r => r.isNavigationRequest() && r.url().endsWith("/app"));
    await page.evaluate(() => {
      Object.defineProperty(document, "visibilityState", { value: "hidden", configurable: true });
      document.dispatchEvent(new Event("visibilitychange"));
    });
    assert.equal(await page.locator("body").evaluate(e => getComputedStyle(e).visibility), "hidden");
    await page.evaluate(() => {
      Object.defineProperty(document, "visibilityState", { value: "visible", configurable: true });
      document.dispatchEvent(new Event("visibilitychange"));
    });
    await resume;
    await page.waitForURL("**/student");
    const legacyEntry = page.waitForRequest(r => r.isNavigationRequest() && r.url().endsWith("/app"));
    await page.goto(baseUrl + "/").catch(error => {
      if (!error.message.includes("interrupted")) throw error;
    });
    await legacyEntry;
    await page.waitForURL("**/student");
    assert.deepEqual(errors, []);
    console.log("PASS: enrollment, 36-hour trusted session, expired session, cancellation, password fallback, PWA resume and legacy shortcut");
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
