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

const { chromium } = loadPlaywright();

const baseUrl = process.env.QA_BASE_URL || "http://localhost:18090";
const outDir = path.resolve(process.env.QA_OUT_DIR || ".run-logs/lecture-rich-smoke");
const chromePath = process.env.CHROME_PATH || "C:/Program Files/Google/Chrome/Application/chrome.exe";
fs.mkdirSync(outDir, { recursive: true });

const users = {
  admin: { email: "admin@damulab.kz", password: "password" },
  student: { email: "student@damulab.kz", password: "password" }
};

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function slug(value) {
  return String(value).replace(/[^a-zA-Z0-9_-]+/g, "-").replace(/-+/g, "-").replace(/^-|-$/g, "");
}

async function snap(page, name) {
  await page.screenshot({ path: path.join(outDir, `${slug(name)}.png`), fullPage: true });
}

async function login(page, role) {
  const cred = users[role];
  await page.goto(`${baseUrl}/login`, { waitUntil: "networkidle" });
  await page.fill('input[name="username"]', cred.email);
  await page.fill('input[name="password"]', cred.password);
  await Promise.all([
    page.waitForLoadState("networkidle"),
    page.click('button[type="submit"]')
  ]);
}

async function csrf(page) {
  const value = await page.locator('input[name="_csrf"]').first().getAttribute("value");
  assert(value, "CSRF token not found");
  return value;
}

async function ensureTopic(page, marker) {
  await page.goto(`${baseUrl}/admin/topics`, { waitUntil: "networkidle" });
  const token = await csrf(page);
  const subjectId = await page.locator('select[name="subjectId"] option[value]').first().getAttribute("value");
  const gradeId = await page.locator('select[name="gradeId"] option[value]').first().getAttribute("value");
  assert(subjectId && gradeId, "subjectId/gradeId were not found");

  const created = await page.evaluate(async ({ token, subjectId, gradeId, marker }) => {
    const payload = {
      subjectId: Number(subjectId),
      gradeId: Number(gradeId),
      code: `lecture-rich-${marker.toLowerCase()}`,
      titleRu: `Lecture Smoke ${marker}`,
      titleKk: `Lecture Smoke ${marker}`
    };
    const res = await fetch("/api/admin/topics", {
      method: "POST",
      credentials: "same-origin",
      headers: {
        "Content-Type": "application/json",
        "X-CSRF-TOKEN": token
      },
      body: JSON.stringify(payload)
    });
    if (!res.ok) {
      throw new Error(`topic_create_failed:${res.status}:${await res.text()}`);
    }
    return res.json();
  }, { token, subjectId, gradeId, marker });

  return {
    subjectId: String(subjectId),
    gradeId: String(gradeId),
    topicId: String(created.id)
  };
}

async function insertFormula(page, lang, latex) {
  const host = page.locator(`#lecture-editor-${lang}`);
  await host.locator(".ql-editor").click();
  await host.locator("xpath=preceding-sibling::*[1]").locator(".ql-formula").click();
  const dialog = page.locator("#lecture-formula-dialog");
  const formulaInput = page.locator("#lecture-formula-input");
  await dialog.waitFor({ state: "visible", timeout: 5000 });
  const bounds = await dialog.boundingBox();
  const viewport = page.viewportSize();
  assert(bounds && viewport, "formula dialog bounds are unavailable");
  assert(bounds.x >= 0 && bounds.y >= 0, "formula dialog starts outside the viewport");
  assert(bounds.x + bounds.width <= viewport.width, "formula dialog overflows viewport width");
  assert(bounds.y + bounds.height <= viewport.height, "formula dialog overflows viewport height");
  await formulaInput.fill(latex);
  await page.locator("#lecture-formula-preview .katex").waitFor({ state: "visible", timeout: 5000 });
  if (viewport.width <= 390) {
    await snap(page, "01-formula-dialog-mobile");
  }
  await page.click("#lecture-formula-insert");
  await dialog.waitFor({ state: "hidden", timeout: 5000 });
}

async function pasteScreenshot(page, lang) {
  const editor = page.locator(`#lecture-editor-${lang} .ql-editor`);
  await editor.click();
  await editor.evaluate((target) => {
    const png = new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00]);
    const transfer = new DataTransfer();
    transfer.items.add(new File([png], "clipboard-smoke.png", { type: "image/png" }));
    target.dispatchEvent(new ClipboardEvent("paste", {
      bubbles: true,
      cancelable: true,
      clipboardData: transfer
    }));
  });
  await page.locator("#lecture-image-upload-status").filter({ hasText: "Изображение добавлено" })
    .waitFor({ state: "visible", timeout: 10000 });
  await page.locator(`#lecture-editor-${lang} img[src^="/files/lecture-images/"]`)
    .waitFor({ state: "visible", timeout: 5000 });
}

async function createCheckpointQuestion(page, marker, refs) {
  const token = await csrf(page);
  return page.evaluate(async ({ token, marker, refs }) => {
    const createdResponse = await fetch("/api/admin/questions", {
      method: "POST",
      credentials: "same-origin",
      headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": token },
      body: JSON.stringify({
        subjectId: Number(refs.subjectId),
        topicIds: [Number(refs.topicId)],
        gradeIds: [Number(refs.gradeId)],
        type: "SCQ",
        difficulty: 2,
        bodyRu: `Smoke checkpoint ${marker}: 2 + 2?`,
        bodyKk: `Smoke checkpoint ${marker}: 2 + 2?`,
        source: `lecture-rich-smoke-${marker}`,
        options: [
          { label: "A", textRu: "3", textKk: "3", correct: false },
          { label: "B", textRu: "4", textKk: "4", correct: true }
        ]
      })
    });
    if (!createdResponse.ok) throw new Error(`question_create_failed:${createdResponse.status}`);
    const created = await createdResponse.json();
    for (const action of ["approve", "publish"]) {
      const response = await fetch(`/api/admin/questions/${created.id}/${action}`, {
        method: "POST",
        credentials: "same-origin",
        headers: { "X-CSRF-TOKEN": token }
      });
      if (!response.ok) throw new Error(`question_${action}_failed:${response.status}`);
    }
    return String(created.currentVersionId);
  }, { token, marker, refs });
}

async function fillLectureEditor(page, marker, refs) {
  await page.goto(`${baseUrl}/admin/lectures/new?subjectId=${refs.subjectId}&gradeId=${refs.gradeId}`, { waitUntil: "networkidle" });
  await page.selectOption("#lecture-topic", refs.topicId);
  await page.fill('input[name="titleRu"]', `Lecture rich smoke ${marker}`);
  await page.fill('input[name="titleKk"]', `Lecture rich smoke ${marker}`);
  await page.fill('input[name="source"]', `lecture-rich-smoke-${marker}`);

  const ruEditor = page.locator("#lecture-editor-ru .ql-editor");
  await ruEditor.click();
  await page.keyboard.type(`Lecture marker ${marker}. Inline formula: `);
  await page.setViewportSize({ width: 360, height: 800 });
  await insertFormula(page, "ru", "P=\\frac{a}{b}\\cdot 100\\%");
  await page.setViewportSize({ width: 1366, height: 900 });
  await page.keyboard.type(" Block formula below.");
  await page.keyboard.press("Enter");
  await page.keyboard.press("Enter");
  await insertFormula(page, "ru", "\\displaystyle x^2 + y^2 = z^2");
  await page.keyboard.press("Enter");
  await page.keyboard.type("End of RU block.");
  await pasteScreenshot(page, "ru");

  await page.click('[data-lecture-tab="kk"]');
  const kkEditor = page.locator("#lecture-editor-kk .ql-editor");
  await kkEditor.click();
  await page.keyboard.type(`KK marker ${marker}.`);

  await page.fill('input[name="attachments[0].title"]', `lecture-${marker}.pdf`);
  await page.selectOption('select[name="attachments[0].mediaType"]', "pdf");
  await page.fill('input[name="attachments[0].url"]', "https://example.org/lecture-material.pdf");

  await snap(page, "01-admin-editor-filled");
  await submitLectureForm(page, "draft");
  const afterCreateUrl = page.url();
  if (new URL(afterCreateUrl).pathname !== "/admin/lectures") {
    const clientAttachmentError = await page.locator("#lecture-attachments-error").innerText().catch(() => "");
    const globalError = await page.locator(".alert.error").first().innerText().catch(() => "");
    throw new Error(`expected redirect to /admin/lectures after draft save; url=${afterCreateUrl}; clientError=${clientAttachmentError}; globalError=${globalError}`);
  }
}

async function openRowAndParseLectureId(page, marker) {
  const row = page.locator("table.data-table tbody tr").filter({ hasText: `Lecture rich smoke ${marker}` }).first();
  await row.waitFor({ timeout: 10000 });
  const idText = (await row.locator("td").nth(0).innerText()).trim();
  const match = idText.match(/L-(\d+)/);
  assert(match, "unable to parse lecture id");
  return { row, lectureId: Number(match[1]) };
}

async function verifyReopenAndEdit(page, lectureId, marker, checkpointVersionId) {
  await page.goto(`${baseUrl}/admin/lectures/${lectureId}/edit`, { waitUntil: "networkidle" });
  const formulaCount = await page.locator("#lecture-editor-ru .ql-editor .ql-formula").count();
  assert(formulaCount >= 2, `expected at least 2 formulas after reopen, got ${formulaCount}`);
  await snap(page, "02-admin-editor-reopened");

  const editedSourceMarker = `lecture-rich-edited-${marker}`;
  await page.fill('input[name="source"]', editedSourceMarker);
  const ruEditor = page.locator("#lecture-editor-ru .ql-editor");
  await ruEditor.click();
  await page.keyboard.press("End");
  await page.keyboard.type(` UI save marker ${marker}`);
  await page.selectOption("#lecture-control-mode", "MANUAL");
  await page.click("#manual-checkpoint-load");
  const checkpointButton = page.locator(`[data-checkpoint-id="${checkpointVersionId}"]`);
  await checkpointButton.waitFor({ state: "visible", timeout: 10000 });
  await checkpointButton.click();
  assert(
    await page.locator(`input[name="checkpointQuestionVersionIds"][value="${checkpointVersionId}"]`).count() === 1,
    "manual checkpoint hidden input was not created"
  );

  await submitLectureForm(page, "draft");
  const afterDraftUrl = page.url();
  if (new URL(afterDraftUrl).pathname !== "/admin/lectures") {
    const globalError = await page.locator(".alert.error").first().innerText().catch(() => "");
    throw new Error(`expected redirect to /admin/lectures after edit draft save; url=${afterDraftUrl}; globalError=${globalError}`);
  }

  await page.goto(`${baseUrl}/admin/lectures/${lectureId}/edit`, { waitUntil: "networkidle" });
  const sourceAfterEdit = await page.locator('input[name="source"]').inputValue();
  assert(
    sourceAfterEdit.includes(editedSourceMarker),
    `edited source marker was not saved; source=${sourceAfterEdit}`
  );
  const formulaCountAfter = await page.locator("#lecture-editor-ru .ql-editor .ql-formula").count();
  assert(formulaCountAfter >= 2, `expected at least 2 formulas after ui save, got ${formulaCountAfter}`);
  assert(await page.inputValue("#lecture-control-mode") === "MANUAL", "manual control mode was not restored");
  assert(
    await page.locator(`input[name="checkpointQuestionVersionIds"][value="${checkpointVersionId}"]`).count() === 1,
    "selected checkpoint was not restored"
  );
  assert(
    await page.locator('#lecture-editor-ru img[src^="/files/lecture-images/"]').count() === 1,
    "pasted lecture image was not restored"
  );
  const ruHtml = await page.locator("#lecture-content-ru").inputValue();
  assert(ruHtml.includes("ql-formula"), "saved RU content lost formula markup");
  assert(ruHtml.includes(`Lecture marker ${marker}`), "saved RU content lost marker text");
  assert(ruHtml.includes(`UI save marker ${marker}`), "saved RU content lost UI edit marker");
  assert(!ruHtml.includes("<script"), "saved RU content still contains script tag");
}

async function submitLectureForm(page, action) {
  await Promise.all([
    page.waitForLoadState("networkidle"),
    page.click(`#lecture-form button[name="action"][value="${action}"]`)
  ]);
}

async function publishLecture(page, marker) {
  await page.goto(`${baseUrl}/admin/lectures`, { waitUntil: "networkidle" });
  const row = page.locator("table.data-table tbody tr").filter({ hasText: `Lecture rich smoke ${marker}` }).first();
  await row.waitFor({ timeout: 10000 });
  const publishButton = row.locator('form[action$="/publish"] button');
  await Promise.all([
    page.waitForLoadState("networkidle"),
    publishButton.click()
  ]);
  await snap(page, "03-admin-lecture-published");
}

async function verifyStudentVisibility(browser, marker) {
  const context = await browser.newContext({ viewport: { width: 1366, height: 900 } });
  const page = await context.newPage();
  await login(page, "student");
  await page.goto(`${baseUrl}/student/lectures`, { waitUntil: "networkidle" });
  const card = page.locator(".lecture-card").filter({ hasText: `Lecture rich smoke ${marker}` }).first();
  await card.waitFor({ timeout: 10000 });
  await Promise.all([
    page.waitForLoadState("networkidle"),
    card.locator("a.button.primary").click()
  ]);
  const pageText = await page.locator("body").innerText();
  assert(pageText.includes(`Lecture marker ${marker}`), "student lecture page does not contain lecture marker");
  const formulaCount = await page.locator(".lecture-content .ql-formula, .lecture-content .katex").count();
  assert(formulaCount > 0, "student lecture page does not show formula content");
  assert(await page.locator('.lecture-content img[src^="/files/lecture-images/"]').count() === 1,
    "student lecture page does not show pasted image");
  assert(await page.locator("[data-checkpoint-form]").count() === 1,
    "student lecture page does not show manual checkpoint");
  await snap(page, "04-student-lecture-visible");
  await context.close();
}

async function run() {
  const marker = String(Date.now()).slice(-8);
  const summary = {
    startedAt: new Date().toISOString(),
    baseUrl,
    marker,
    status: "running",
    steps: [],
    artifactsDir: outDir
  };
  const step = (name, status, details) => {
    summary.steps.push({ name, status, details: details || "", at: new Date().toISOString() });
  };

  const browser = await chromium.launch({
    headless: true,
    executablePath: chromePath
  });

  try {
    const adminContext = await browser.newContext({ viewport: { width: 1366, height: 900 } });
    const adminPage = await adminContext.newPage();

    await login(adminPage, "admin");
    step("admin-login", "ok");

    const refs = await ensureTopic(adminPage, marker);
    step("topic-created", "ok", `topicId=${refs.topicId}`);

    const checkpointVersionId = await createCheckpointQuestion(adminPage, marker, refs);
    step("checkpoint-question-created", "ok", `versionId=${checkpointVersionId}`);

    await fillLectureEditor(adminPage, marker, refs);
    const { lectureId } = await openRowAndParseLectureId(adminPage, marker);
    step("lecture-created-draft", "ok", `lectureId=${lectureId}`);

    await verifyReopenAndEdit(adminPage, lectureId, marker, checkpointVersionId);
    step("lecture-reopened-edited", "ok");

    await publishLecture(adminPage, marker);
    step("lecture-published", "ok");

    await adminContext.close();

    await verifyStudentVisibility(browser, marker);
    step("student-visibility", "ok");

    summary.status = "passed";
  } catch (error) {
    summary.status = "failed";
    summary.error = error.message;
    summary.stack = error.stack;
  } finally {
    await browser.close();
    summary.finishedAt = new Date().toISOString();
    fs.writeFileSync(path.join(outDir, "lecture-rich-editor-smoke-report.json"), JSON.stringify(summary, null, 2));
    console.log(JSON.stringify(summary, null, 2));
    if (summary.status !== "passed") {
      process.exitCode = 1;
    }
  }
}

run();
