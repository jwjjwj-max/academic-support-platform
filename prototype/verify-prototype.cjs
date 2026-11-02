const path = require("path");
const { chromium } = require("playwright");

const baseUrl = "http://127.0.0.1:4173/";
const outputDir = __dirname;

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const page = await context.newPage();
  const consoleErrors = [];
  page.on("console", (message) => {
    if (message.type() === "error") consoleErrors.push(message.text());
  });
  page.on("pageerror", (error) => consoleErrors.push(error.message));

  await page.goto(baseUrl, { waitUntil: "networkidle" });
  const initialText = (await page.locator("body").innerText()).trim();
  assert(initialText.length > 500, `首页内容为空或过少（${initialText.length} 字符）：${initialText.slice(0, 180)}`);
  assert(await page.getByText("本地演示数据", { exact: true }).isVisible(), "缺少本地演示数据边界提示");
  const roleOptions = await page.locator("#role-switcher option").allTextContents();
  assert(roleOptions.length === 3, `角色切换应只保留三个角色，当前为 ${roleOptions.length} 个`);
  assert(!roleOptions.some((item) => item.includes("指导老师") || item.includes("王老师")), "角色切换仍包含指导老师");
  assert(await page.locator("[data-nav='matching']").first().isVisible(), "学生端匹配入口未渲染");
  await page.screenshot({ path: path.join(outputDir, "prototype-student-desktop.png"), fullPage: true });

  await page.locator("[data-nav='application']").first().click();
  const applicationText = await page.locator("#application-form").innerText();
  assert(applicationText.includes("上学期期末全部课程绩点"), "报名页未改为全部课程绩点");
  assert(applicationText.includes("年级"), "报名页缺少年级");
  assert(!applicationText.includes("专业排名"), "报名页仍展示专业排名");
  assert(await page.locator("#application-subjects").isVisible(), "报名页缺少手填科目输入框");
  await page.locator("#application-gpa").fill("9");
  await page.locator("#application-form button[value='submit']").click();
  assert((await page.locator("#application-form").innerText()).includes("绩点必须在 0—5 之间"), "报名表单未展示内联校验错误");

  await page.locator("[data-nav='matching']").first().click();
  const matchingText = await page.locator("#app").innerText();
  assert(!matchingText.includes("时间重合"), "学生匹配详情仍包含时间权重");
  assert(matchingText.includes("专业匹配"), "学生匹配详情缺少专业权重");
  assert(matchingText.includes("年级关系"), "学生匹配详情缺少年级关系");
  await page.locator("[data-action='confirm-match']").click();
  await page.locator("#confirm-truth").check();
  await page.locator("#modal-submit").click();
  await page.waitForTimeout(100);
  assert((await page.locator("#app").innerText()).includes("BF-2026-008"), "双方确认后未生成帮扶对");

  await page.reload({ waitUntil: "networkidle" });
  assert((await page.locator("#app").innerText()).includes("BF-2026-008"), "刷新后帮扶对状态未持久化");

  await page.locator("[data-nav='support']").first().click();
  const supportText = await page.locator("#app").innerText();
  assert(supportText.includes("过程照片") && supportText.includes("活动感悟"), "帮扶页缺少照片或感悟");
  assert(!supportText.includes("帮扶计划") && !supportText.includes("中测"), "帮扶页仍显示计划或中测");

  await page.locator("[data-nav='results']").first().click();
  const resultText = await page.locator("#app").innerText();
  assert(resultText.includes("上学期期末全部课程绩点") && resultText.includes("德育分加分文件"), "学生成绩与德育分页面内容不完整");
  assert(!resultText.includes("专业排名") && !resultText.includes("公开榜单"), "学生端仍泄露排名或榜单");

  await page.locator("[data-nav='profile']").first().click();
  const profileText = await page.locator("#app").innerText();
  assert(profileText.includes("微信号") && profileText.includes("年级"), "个人中心缺少年级或微信号");
  assert(await page.locator("[data-action='view-history']").isVisible(), "个人中心缺少本人往期帮扶入口");

  await page.locator("#role-switcher").selectOption("admin");
  assert(await page.locator("[data-nav='applications']").first().isVisible(), "管理员报名审核入口未渲染");
  assert(await page.locator("[data-nav='basics']").count() === 0, "管理端仍显示基础数据入口");
  await page.locator("[data-nav='matching-admin']").first().click();
  const adminMatchingText = await page.locator("#app").innerText();
  assert(adminMatchingText.includes("专业 25") && adminMatchingText.includes("年级 5"), "管理端匹配权重未更新");
  assert(!adminMatchingText.includes("时间 20"), "管理端匹配仍包含时间权重");
  await page.screenshot({ path: path.join(outputDir, "prototype-admin-desktop.png"), fullPage: true });

  await page.locator("[data-nav='grades']").first().click();
  assert(await page.locator("#transcript-previous").isVisible() && await page.locator("#transcript-current").isVisible(), "成绩页缺少两张成绩单上传入口");
  assert(!(await page.locator("#app").innerText()).includes("期中绩点"), "成绩页仍显示期中数据");

  await page.locator("[data-nav='rewards']").first().click();
  await page.locator("#reward-file-title").fill("浏览器验证德育分名单");
  await page.locator("#reward-file-input").setInputFiles({ name: "verification.csv", mimeType: "text/csv", buffer: Buffer.from("studentNo,name,score\n23020116,陈晨,1", "utf8") });
  await page.locator("#reward-file-form button[type='submit']").click();
  await page.waitForTimeout(100);
  assert((await page.locator("#app").innerText()).includes("浏览器验证德育分名单"), "德育分文件上传后未显示");

  await page.locator("#role-switcher").selectOption("climber");
  await page.locator("[data-nav='results']").first().click();
  assert((await page.locator("#app").innerText()).includes("浏览器验证德育分名单"), "学生端未显示管理员发布的德育分文件");

  await page.setViewportSize({ width: 390, height: 844 });
  await page.reload({ waitUntil: "networkidle" });
  assert(await page.locator("[data-action='toggle-sidebar']").isVisible(), "移动端菜单按钮不可见");
  await page.locator("[data-action='toggle-sidebar']").click();
  assert(await page.locator("#sidebar").evaluate((node) => node.classList.contains("open")), "移动端侧边栏无法展开");
  await page.locator("#sidebar").evaluate((node) => node.classList.remove("open"));
  await page.screenshot({ path: path.join(outputDir, "prototype-mobile.png"), fullPage: true });

  assert(consoleErrors.length === 0, `浏览器控制台错误：${consoleErrors.join(" | ")}`);
  await browser.close();
  process.stdout.write(JSON.stringify({
    pageLoads: true,
    primaryWorkflow: "match confirmation -> formal pair",
    persistenceAfterReload: true,
    inlineValidation: true,
    requestedChanges: {
      manualSubjects: true,
      noStudentRanking: true,
      noPlanOrMidterm: true,
      updatedMatchingWeights: true,
      twoTranscriptInputs: true,
      rewardFilePublishing: true,
      personalHistory: true,
      threeRolesOnly: true,
    },
    adminNavigation: true,
    mobileNavigation: true,
    consoleErrors,
    screenshots: ["prototype-student-desktop.png", "prototype-admin-desktop.png", "prototype-mobile.png"],
  }, null, 2));
})().catch((error) => {
  process.stderr.write(error.stack || error.message);
  process.exit(1);
});
