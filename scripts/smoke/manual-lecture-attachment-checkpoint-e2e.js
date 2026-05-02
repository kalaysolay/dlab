const fs = require("fs");
const path = require("path");

function loadPlaywright() {
  try {
    return require("playwright");
  } catch (error) {
    return require(path.resolve(__dirname, "../../.run-logs/stage13-visual-pass/node_modules/playwright"));
  }
}

const { chromium } = loadPlaywright();

const baseUrl = process.env.QA_BASE_URL || "http://localhost:18090";
const outDir = path.resolve(process.env.QA_OUT_DIR || ".run-logs/lecture-rich-smoke");
const chromePath = process.env.CHROME_PATH || "C:/Program Files/Google/Chrome/Application/chrome.exe";
fs.mkdirSync(outDir, { recursive: true });

async function login(page, email, password) {
  await page.goto(`${baseUrl}/login`, { waitUntil: "networkidle" });
  await page.fill('input[name="username"]', email);
  await page.fill('input[name="password"]', password);
  await Promise.all([page.waitForLoadState("networkidle"), page.click('button[type="submit"]')]);
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

async function run() {
  const startedAt = Date.now();
  const step = (name) => console.log(`[step] ${name} (+${Math.round((Date.now() - startedAt) / 1000)}s)`);
  const marker = "MAN" + String(Date.now()).slice(-8);
  const attachmentPath = path.join(outDir, `attachment-${marker}.pdf`);
  fs.writeFileSync(attachmentPath, `%PDF-1.4\n% smoke ${marker}\n`);

  const browser = await chromium.launch({ headless: true, executablePath: chromePath });
  const adminContext = await browser.newContext({ viewport: { width: 1366, height: 900 } });
  adminContext.setDefaultTimeout(20000);
  const adminPage = await adminContext.newPage();

  step("admin login");
  await login(adminPage, "admin@damulab.kz", "password");
  step("open topics");
  await adminPage.goto(`${baseUrl}/admin/topics`, { waitUntil: "networkidle" });

  step("create topic + question");
  const refs = await adminPage.evaluate(async (markerValue) => {
    const token = document.querySelector('input[name="_csrf"]').value;
    const subjectId = document.querySelector('select[name="subjectId"] option[value]').value;
    const gradeId = document.querySelector('select[name="gradeId"] option[value]').value;

    const topicPayload = {
      subjectId: Number(subjectId),
      gradeId: Number(gradeId),
      code: `manual-${markerValue.toLowerCase()}`,
      titleRu: `Manual Topic ${markerValue}`,
      titleKk: `Manual Topic ${markerValue}`
    };
    const topicRes = await fetch("/api/admin/topics", {
      method: "POST",
      credentials: "same-origin",
      headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": token },
      body: JSON.stringify(topicPayload)
    });
    if (!topicRes.ok) {
      throw new Error(`topic create failed: ${topicRes.status}`);
    }
    const topic = await topicRes.json();

    const questionPayload = {
      topicId: topic.id,
      bodyRu: `MANUAL checkpoint ${markerValue}`,
      bodyKk: `MANUAL checkpoint ${markerValue}`,
      source: `manual-${markerValue}`,
      difficulty: 2,
      type: "SCQ",
      options: [
        { label: "A", textRu: "A", textKk: "A", correct: true },
        { label: "B", textRu: "B", textKk: "B", correct: false }
      ]
    };
    const questionRes = await fetch("/api/admin/questions", {
      method: "POST",
      credentials: "same-origin",
      headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": token },
      body: JSON.stringify(questionPayload)
    });
    if (!questionRes.ok) {
      throw new Error(`question create failed: ${questionRes.status}`);
    }
    const question = await questionRes.json();
    const approveRes = await fetch(`/api/admin/questions/${question.id}/approve`, {
      method: "POST",
      credentials: "same-origin",
      headers: { "X-CSRF-TOKEN": token }
    });
    if (!approveRes.ok) {
      throw new Error(`question approve failed: ${approveRes.status}`);
    }
    const publishRes = await fetch(`/api/admin/questions/${question.id}/publish`, {
      method: "POST",
      credentials: "same-origin",
      headers: { "X-CSRF-TOKEN": token }
    });
    if (!publishRes.ok) {
      throw new Error(`question publish failed: ${publishRes.status}`);
    }
    const publishedQuestionRes = await fetch(`/api/admin/questions/${question.id}`, { credentials: "same-origin" });
    const publishedQuestion = await publishedQuestionRes.json();
    return {
      subjectId: String(subjectId),
      gradeId: String(gradeId),
      topicId: String(topic.id),
      questionVersionId: String(publishedQuestion.currentVersionId)
    };
  }, marker);

  await adminPage.goto(
    `${baseUrl}/admin/lectures/new?subjectId=${refs.subjectId}&gradeId=${refs.gradeId}`,
    { waitUntil: "networkidle" }
  );
  step("fill lecture create form");
  await adminPage.selectOption("#lecture-topic", refs.topicId);
  const selectedTopicValue = await adminPage.locator("#lecture-topic").inputValue();
  assert(selectedTopicValue === refs.topicId, "topic option did not get selected in lecture form");
  await adminPage.fill('input[name="titleRu"]', `Manual lecture ${marker}`);
  await adminPage.fill('input[name="titleKk"]', `Manual lecture ${marker}`);
  await adminPage.fill('input[name="source"]', `manual-${marker}`);

  await adminPage.locator("#lecture-editor-ru .ql-editor").click();
  await adminPage.keyboard.type(`RU body ${marker}`);
  await adminPage.click('[data-lecture-tab="kk"]');
  await adminPage.locator("#lecture-editor-kk .ql-editor").click();
  await adminPage.keyboard.type(`KK body ${marker}`);

  await adminPage.fill('input[name="attachments[0].title"]', `attachment-${marker}.pdf`);
  await adminPage.selectOption('select[name="attachments[0].mediaType"]', "pdf");
  await adminPage.locator('input[type="file"][name="attachmentFiles[0]"]').setInputFiles(attachmentPath);

  await adminPage.selectOption("#lecture-control-mode", "MANUAL");
  const candidatesApiCount = await adminPage.evaluate(async (topicId) => {
    for (let attempt = 0; attempt < 6; attempt += 1) {
      const response = await fetch(`/api/admin/questions?topicId=${encodeURIComponent(topicId)}&status=PUBLISHED`, {
        credentials: "same-origin"
      });
      if (response.ok) {
        const payload = await response.json();
        if (Array.isArray(payload) && payload.length > 0) {
          return payload.length;
        }
      }
      await new Promise((resolve) => setTimeout(resolve, 500));
    }
    return 0;
  }, refs.topicId);
  assert(candidatesApiCount > 0, "no published checkpoint candidates returned by API");
  await adminPage.fill("#manual-checkpoint-query", "MANUAL checkpoint");
  await adminPage.click("#manual-checkpoint-load");
  step("manual picker add/remove/add");
  const candidateButton = adminPage.locator("#manual-checkpoint-candidates button[data-checkpoint-id]").first();
  await candidateButton.waitFor({ timeout: 10000 });
  const selectedCheckpointId = await candidateButton.getAttribute("data-checkpoint-id");
  await candidateButton.click();
  await adminPage.locator("#manual-checkpoint-selected button[data-checkpoint-remove]").first().waitFor({ timeout: 10000 });
  await adminPage.locator("#manual-checkpoint-selected button[data-checkpoint-remove]").first().click();
  await adminPage.locator("#manual-checkpoint-candidates button[data-checkpoint-id]").first().click();

  await Promise.all([
    adminPage.waitForLoadState("networkidle"),
    adminPage.click('#lecture-form button[name="action"][value="draft"]')
  ]);
  step("draft saved");
  assert(new URL(adminPage.url()).pathname === "/admin/lectures", "draft save did not redirect to /admin/lectures");

  const row = adminPage.locator("table.data-table tbody tr").filter({ hasText: `Manual lecture ${marker}` }).first();
  await row.waitFor({ timeout: 10000 });
  const idText = (await row.locator("td").nth(0).innerText()).trim();
  const lectureIdMatch = idText.match(/L-(\d+)/);
  assert(lectureIdMatch, "lecture id not found in admin table");
  const lectureId = Number(lectureIdMatch[1]);

  await adminPage.goto(`${baseUrl}/admin/lectures/${lectureId}/edit`, { waitUntil: "networkidle" });
  await Promise.all([
    adminPage.waitForLoadState("networkidle"),
    adminPage.click('#lecture-form button[name="action"][value="publish"]')
  ]);
  step("published from edit form");
  assert(new URL(adminPage.url()).pathname === "/admin/lectures", "publish did not redirect to /admin/lectures");

  const lecturePayload = await adminPage.evaluate(async (id) => {
    const response = await fetch(`/api/admin/lectures/${id}`, { credentials: "same-origin" });
    return response.json();
  }, lectureId);
  const checkpointPersisted = (lecturePayload.checkpoints || [])
    .some((item) => String(item.questionVersionId) === String(selectedCheckpointId));
  assert(lecturePayload.status === "published", "lecture was not published");
  assert(lecturePayload.topicId != null, "lecture topicId is null after publish");
  assert(checkpointPersisted, "manual checkpoint selection did not persist");

  const studentContext = await browser.newContext({ viewport: { width: 1366, height: 900 } });
  studentContext.setDefaultTimeout(20000);
  const studentPage = await studentContext.newPage();
  step("student login");
  await login(studentPage, "student@damulab.kz", "password");
  await studentPage.goto(`${baseUrl}/student/lectures`, { waitUntil: "networkidle" });
  step("student lecture list opened");
  const card = studentPage.locator(".lecture-card").filter({ hasText: `Manual lecture ${marker}` }).first();
  await card.waitFor({ timeout: 10000 });
  await Promise.all([
    studentPage.waitForLoadState("networkidle"),
    card.locator("a.button.primary").click()
  ]);

  const attachmentLink = studentPage.locator(".attachment-card").first();
  await attachmentLink.waitFor({ timeout: 10000 });
  step("attachment link visible");
  const href = await attachmentLink.getAttribute("href");
  const downloadResponse = await studentPage.request.get(href.startsWith("http") ? href : `${baseUrl}${href}`);
  assert(downloadResponse.status() === 200, "attachment endpoint did not return 200");

  console.log(JSON.stringify({
    marker,
    lectureId,
    status: lecturePayload.status,
    topicId: lecturePayload.topicId,
    checkpointId: selectedCheckpointId,
    checkpointPersisted,
    attachmentStatus: downloadResponse.status()
  }, null, 2));

  await studentContext.close();
  await adminContext.close();
  await browser.close();
}

run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
