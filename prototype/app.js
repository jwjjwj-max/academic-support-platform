(() => {
  "use strict";

  const STORAGE_KEY = "academic-support-prototype-v1";
  const VERSION = 4;

  const roleMeta = {
    climber: { name: "陈晨", label: "学业攀登者", avatar: "陈" },
    navigator: { name: "林晓", label: "学业领航者", avatar: "林" },
    admin: { name: "学习部管理员", label: "管理员", avatar: "管" },
  };

  const studentNav = [
    ["main", "首页", "dashboard", "⌂"],
    ["main", "当期报名", "application", "▤"],
    ["main", "匹配中心", "matching", "⇄"],
    ["main", "我的帮扶", "support", "◎"],
    ["main", "成绩与德育分", "results", "▥"],
    ["account", "消息中心", "notifications", "◉"],
    ["account", "个人中心", "profile", "♙"],
  ];

  const adminNav = [
    ["overview", "仪表盘", "dashboard", "⌂"],
    ["activity", "期次管理", "periods", "▦"],
    ["activity", "报名审核", "applications", "✓"],
    ["activity", "匹配管理", "matching-admin", "⇄"],
    ["process", "帮扶对管理", "pairs", "◎"],
    ["process", "材料审核", "materials", "▣"],
    ["outcome", "成绩管理", "grades", "▥"],
    ["outcome", "德育分名单", "rewards", "★"],
    ["system", "通知管理", "notices", "◉"],
    ["system", "操作日志", "logs", "≡"],
  ];

  const pageMeta = {
    dashboard: ["首页", "当前期次业务概览"],
    application: ["当期报名", "填写或查看本期参与资料"],
    matching: ["匹配中心", "查看匹配依据并完成结对确认"],
    support: ["我的帮扶", "过程照片与活动感悟"],
    results: ["成绩与德育分", "查看绩点变化和已发布的德育分文件"],
    notifications: ["消息中心", "当前账号的站内通知与待办"],
    profile: ["个人中心", "账号资料与历史参与记录"],
    periods: ["期次管理", "配置活动时间、规则与归档状态"],
    applications: ["报名审核", "审核本期领航者和攀登者资料"],
    "matching-admin": ["匹配管理", "运行、调整并发布可解释的配对方案"],
    pairs: ["帮扶对管理", "查看正式结对和阶段完成情况"],
    materials: ["材料审核", "审核过程照片和活动感悟"],
    grades: ["成绩管理", "导入、校验并查看成绩变化"],
    rewards: ["德育分名单", "上传并发布德育分加分名单文件"],
    notices: ["通知管理", "发送站内通知并查看阅读情况"],
    logs: ["操作日志", "追溯管理员关键操作"],
  };

  function seedState() {
    return {
      version: VERSION,
      currentRole: "climber",
      currentPage: "dashboard",
      currentPeriodId: "p2026fall",
      profiles: {
        climber: { phone: "13800002026", wechat: "chenchen_zufe", grade: "2023级" },
        navigator: { phone: "13900002026", wechat: "linxiao_zufe", grade: "2022级" },
      },
      periods: [
        {
          id: "p2026fall",
          name: "2026—2027 学年秋期",
          shortName: "2026 秋",
          status: "跟踪",
          registration: "2026-09-01 至 2026-09-15",
          matching: "2026-09-16 至 2026-09-20",
          confirmationDeadline: "2026-09-23 23:59",
          tracking: "2026-10-01 至 2026-12-15",
          acceptance: "2026-12-16 至 2027-01-10",
          minGpa: 3.5,
          rankThreshold: 30,
          photoMin: 4,
          photoMax: 8,
          publicBoard: true,
        },
        {
          id: "p2026spring",
          name: "2025—2026 学年春期",
          shortName: "2026 春",
          status: "已结束",
          registration: "2026-02-18 至 2026-02-28",
          matching: "2026-03-01 至 2026-03-05",
          confirmationDeadline: "2026-03-08 23:59",
          tracking: "2026-03-10 至 2026-05-30",
          acceptance: "2026-06-01 至 2026-06-20",
          minGpa: 3.5,
          rankThreshold: 30,
          photoMin: 4,
          photoMax: 8,
          publicBoard: true,
        },
      ],
      applications: [
        {
          id: "a-c001", userId: "C001", studentNo: "23020116", name: "陈晨", role: "climber",
          college: "信息管理与人工智能学院", major: "计算机科学与技术", grade: "2023级", gpa: 2.61,
          failed: "高等数学", subjects: ["高等数学", "数据结构"],
          styles: ["耐心细讲型", "刷题实战型"], personality: ["温和耐心"],
          statement: "高等数学积分部分基础不牢，数据结构中的树和图也比较吃力，希望通过固定复习和练习逐步改善。",
          status: "已通过", submittedAt: "2026-09-08 20:14",
        },
        {
          id: "a-n001", userId: "N001", studentNo: "22020308", name: "林晓", role: "navigator",
          college: "信息管理与人工智能学院", major: "计算机科学与技术", grade: "2022级", gpa: 3.82,
          failed: "无", subjects: ["高等数学", "数据结构", "线性代数"],
          styles: ["耐心细讲型", "刷题实战型"], personality: ["温和耐心", "严谨认真"],
          statement: "擅长将知识点拆成小步骤讲解，会根据对方基础安排例题和复盘任务，有过班级答疑经验。",
          status: "已通过", submittedAt: "2026-09-07 18:40",
        },
        {
          id: "a-c002", userId: "C002", studentNo: "23020205", name: "周雨", role: "climber",
          college: "信息管理与人工智能学院", major: "信息管理与信息系统", grade: "2023级", gpa: 2.74,
          failed: "无", subjects: ["概率论", "Python 程序设计"],
          styles: ["系统讲解型"], personality: ["外向健谈"], statement: "概率论概念容易混淆，希望有人带着梳理框架。",
          status: "审核中", submittedAt: "2026-09-10 11:22",
        },
        {
          id: "a-n002", userId: "N002", studentNo: "22020411", name: "赵一帆", role: "navigator",
          college: "信息管理与人工智能学院", major: "信息管理与信息系统", grade: "2022级", gpa: 3.67,
          failed: "无", subjects: ["概率论", "Python 程序设计", "数据库原理"],
          styles: ["系统讲解型", "提纲挈领型"], personality: ["外向健谈"], statement: "习惯先搭建知识框架，再用小题检查理解。",
          status: "已通过", submittedAt: "2026-09-06 15:31",
        },
        {
          id: "a-c003", userId: "C003", studentNo: "24020127", name: "吴桐", role: "climber",
          college: "信息管理与人工智能学院", major: "人工智能", grade: "2024级", gpa: 2.48,
          failed: "线性代数", subjects: ["线性代数", "大学物理"],
          styles: ["刷题实战型"], personality: ["轻松活泼"], statement: "希望重点训练矩阵计算和物理力学题。",
          status: "已驳回", submittedAt: "2026-09-09 09:17", rejectReason: "困难科目说明不足，请补充具体薄弱章节。",
        },
      ],
      candidate: {
        id: "m001", navigatorId: "N001", climberId: "C001", status: "待双方确认",
        score: 94, scores: { subject: 38, style: 15, personality: 9, major: 25, grade: 3, gap: 4 },
        confirmations: { navigator: true, climber: false }, deadline: "2026-09-23 23:59", rejectedPairs: [],
      },
      matchingResults: [
        { id: "mr01", navigatorId: "N001", climberId: "C001", score: 94, subject: 38, style: 15, personality: 9, major: 25, grade: 3, gap: 4, status: "已发布" },
        { id: "mr02", navigatorId: "N002", climberId: "C002", score: 91, subject: 36, style: 15, personality: 8, major: 25, grade: 3, gap: 4, status: "待发布" },
      ],
      pair: null,
      photos: [
        { id: "ph01", name: "图书馆高数答疑.jpg", note: "周三下午图书馆复习积分方法", uploader: "林晓", date: "2026-10-09 16:20", peerConfirmed: true, adminStatus: "通过" },
        { id: "ph02", name: "错题复盘.png", note: "对近期错题进行分类整理", uploader: "陈晨", date: "2026-10-18 10:12", peerConfirmed: true, adminStatus: "待审核" },
        { id: "ph03", name: "数据结构讨论.jpg", note: "梳理二叉树遍历", uploader: "林晓", date: "2026-11-02 15:41", peerConfirmed: false, adminStatus: "待对方确认" },
      ],
      reflections: {
        navigator: { text: "", status: "草稿", rejectReason: "" },
        climber: { text: "", status: "草稿", rejectReason: "" },
      },
      grades: [
        { studentNo: "23020116", name: "陈晨", role: "攀登者", baseGpa: 2.61, finalGpa: 3.08, baseRank: 62, baseCohort: 96, finalRank: 43, finalCohort: 97 },
        { studentNo: "23020205", name: "周雨", role: "攀登者", baseGpa: 2.74, finalGpa: 2.96, baseRank: 51, baseCohort: 88, finalRank: 41, finalCohort: 89 },
        { studentNo: "22020308", name: "林晓", role: "领航者", baseGpa: 3.82, finalGpa: 3.86, baseRank: 8, baseCohort: 102, finalRank: 7, finalCohort: 102 },
      ],
      rewardFiles: [
        {
          id: "rf-demo-01",
          title: "2026秋学业帮扶德育分加分名单（第一批）",
          fileName: "2026秋德育分加分名单.csv",
          mimeType: "text/csv",
          dataUrl: "data:text/csv;charset=utf-8,%EF%BB%BF%E5%AD%A6%E5%8F%B7%2C%E5%A7%93%E5%90%8D%2C%E5%BE%B7%E8%82%B2%E5%88%86%0A22020308%2C%E6%9E%97%E6%99%93%2C1",
          uploadedAt: "2026-12-20 10:30",
          status: "已发布",
        },
      ],
      courses: [
        { id: "c01", code: "MATH101", name: "高等数学", college: "全校", category: "通识分层必修", enabled: true },
        { id: "c02", code: "CS201", name: "数据结构", college: "信息管理与人工智能学院", category: "专业必修", enabled: true },
        { id: "c03", code: "MATH202", name: "概率论", college: "全校", category: "学科必修", enabled: true },
        { id: "c04", code: "CS102", name: "Python 程序设计", college: "信息管理与人工智能学院", category: "专业必修", enabled: true },
        { id: "c05", code: "MATH103", name: "线性代数", college: "全校", category: "通识分层必修", enabled: true },
      ],
      colleges: [
        { id: "org01", name: "信息管理与人工智能学院", majors: ["计算机科学与技术", "信息管理与信息系统", "人工智能"], enabled: true },
        { id: "org02", name: "数据科学学院", majors: ["经济统计学", "应用统计学"], enabled: true },
        { id: "org03", name: "全校公共课程", majors: [], enabled: true },
      ],
      notifications: [
        { id: "nt01", audience: "climber", title: "请确认本期匹配结果", content: "领航者已确认，请于 9 月 23 日前完成结对确认。", date: "2026-09-20 10:00", read: false },
        { id: "nt02", audience: "navigator", title: "匹配结果已发布", content: "请查看匿名匹配信息并完成确认。", date: "2026-09-19 18:00", read: true },
        { id: "nt03", audience: "all", title: "跟踪阶段材料提醒", content: "请各帮扶对按实际进展上传过程照片。", date: "2026-10-15 09:00", read: false },
      ],
      logs: [
        { id: "lg01", time: "2026-10-15 09:00", actor: "学习部管理员", action: "发送通知", target: "2026 秋 · 全体参与者", detail: "跟踪阶段材料提醒" },
        { id: "lg02", time: "2026-09-19 18:00", actor: "学习部管理员", action: "发布匹配", target: "第 1 轮 · 1 组", detail: "发布候选配对" },
        { id: "lg03", time: "2026-09-16 14:30", actor: "学习部管理员", action: "运行匹配", target: "2026 秋", detail: "默认权重，最低分 60" },
      ],
      gradeImports: { previous: [], current: [] },
      historyRecords: [
        { id: "h2025fall-c", audience: "climber", period: "2025 秋", role: "学业攀登者", partner: "许同学", pairId: "BF-2025-021", subjects: ["线性代数"], gpaBefore: 2.38, gpaAfter: 2.61, photos: 6, result: "已完成" },
        { id: "h2025fall-n", audience: "navigator", period: "2025 秋", role: "学业领航者", partner: "郑同学", pairId: "BF-2025-006", subjects: ["高等数学"], gpaBefore: 3.76, gpaAfter: 3.82, photos: 7, result: "已完成" },
      ],
      ui: { errors: {}, basicTab: "courses", applicationFilter: "全部", materialFilter: "全部", search: "" },
    };
  }

  function loadState() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return seedState();
      const parsed = JSON.parse(raw);
      if (parsed.version !== VERSION) return seedState();
      return parsed;
    } catch (error) {
      return seedState();
    }
  }

  let state = loadState();
  let modalSubmitHandler = null;

  const app = document.getElementById("app");
  const nav = document.getElementById("primary-nav");
  const roleSwitcher = document.getElementById("role-switcher");
  const periodSwitcher = document.getElementById("period-switcher");
  const modal = document.getElementById("modal");
  const modalForm = document.getElementById("modal-form");

  function saveState() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  }

  function commit(message, type = "success") {
    saveState();
    render();
    if (message) toast(message, type);
  }

  function esc(value) {
    return String(value ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#039;");
  }

  function statusClass(value) {
    if (["已通过", "双方已确认", "已完成", "已发放", "已确认", "完整", "进行中", "已具备资格", "已发布"].includes(value)) return "green";
    if (["已驳回", "已拒绝", "中途退出", "存在异常", "已取消"].includes(value)) return "red";
    if (["审核中", "待审核", "待确认", "待发布", "待发放", "待判定", "待双方确认", "单方已确认", "待对方确认", "部分完整"].includes(value)) return "yellow";
    if (["草稿", "已结束", "已归档", "未发放"].includes(value)) return "gray";
    return "blue";
  }

  function status(value) {
    return `<span class="status ${statusClass(value)}">${esc(value)}</span>`;
  }

  function tags(items) {
    return (items || []).map((item) => `<span class="tag">${esc(item)}</span>`).join("");
  }

  function currentPeriod() {
    return state.periods.find((item) => item.id === state.currentPeriodId) || state.periods[0];
  }

  function currentApplication(role = state.currentRole) {
    const userId = role === "navigator" ? "N001" : "C001";
    return state.applications.find((item) => item.userId === userId);
  }

  function applicant(userId) {
    return state.applications.find((item) => item.userId === userId) || {};
  }

  function addLog(action, target, detail) {
    state.logs.unshift({
      id: `lg${Date.now()}`,
      time: new Date().toLocaleString("zh-CN", { hour12: false }),
      actor: roleMeta[state.currentRole].name,
      action,
      target,
      detail,
    });
  }

  function addNotification(audience, title, content) {
    state.notifications.unshift({
      id: `nt${Date.now()}${Math.random().toString(16).slice(2)}`,
      audience,
      title,
      content,
      date: new Date().toLocaleString("zh-CN", { hour12: false }),
      read: false,
    });
  }

  function toast(message, type = "success") {
    const node = document.createElement("div");
    node.className = `toast ${type}`;
    node.textContent = message;
    document.getElementById("toast-region").appendChild(node);
    window.setTimeout(() => node.remove(), 3200);
  }

  function openModal(title, body, submitLabel, handler) {
    document.getElementById("modal-title").textContent = title;
    document.getElementById("modal-body").innerHTML = body;
    document.getElementById("modal-submit").textContent = submitLabel || "确认";
    modalSubmitHandler = handler;
    modal.showModal();
  }

  function closeModal() {
    modal.close();
    modalSubmitHandler = null;
  }

  function unreadCount() {
    return state.notifications.filter((item) => !item.read && (item.audience === state.currentRole || item.audience === "all")).length;
  }

  function getNavItems() {
    if (state.currentRole === "admin") return adminNav;
    return studentNav;
  }

  function navGroupName(group) {
    const names = { main: "本期活动", account: "账号", overview: "概览", activity: "活动管理", process: "过程管理", outcome: "成果管理", system: "系统" };
    return names[group] || group;
  }

  function renderShell() {
    roleSwitcher.value = state.currentRole;
    periodSwitcher.innerHTML = state.periods.map((item) => `<option value="${item.id}" ${item.id === state.currentPeriodId ? "selected" : ""}>${esc(item.shortName)} · ${esc(item.status)}</option>`).join("");
    const meta = roleMeta[state.currentRole];
    document.getElementById("user-chip").innerHTML = `<strong>${esc(meta.name)}</strong><span>${esc(meta.label)}</span>`;
    document.getElementById("notification-count").textContent = unreadCount();
    document.getElementById("notification-count").hidden = unreadCount() === 0;

    const allowed = getNavItems().map((item) => item[2]);
    if (!allowed.includes(state.currentPage)) state.currentPage = "dashboard";
    const [title, subtitle] = pageMeta[state.currentPage] || pageMeta.dashboard;
    document.getElementById("page-title").textContent = title;
    document.getElementById("page-subtitle").textContent = subtitle;

    let lastGroup = "";
    nav.innerHTML = getNavItems().map(([group, label, page, icon]) => {
      const groupHeader = group !== lastGroup ? `<div class="nav-group-label">${navGroupName(group)}</div>` : "";
      lastGroup = group;
      const pill = page === "notifications" && unreadCount() ? `<span class="nav-pill">${unreadCount()}</span>` : "";
      return `${groupHeader}<button type="button" class="nav-item ${state.currentPage === page ? "active" : ""}" data-nav="${page}"><span class="nav-icon">${icon}</span>${label}${pill}</button>`;
    }).join("");
  }

  function render() {
    renderShell();
    const page = state.currentPage;
    let html = "";
    if (page === "dashboard") html = renderDashboard();
    else if (page === "application") html = renderApplication();
    else if (page === "matching") html = renderStudentMatching();
    else if (page === "support") html = renderSupport();
    else if (page === "results") html = renderResults();
    else if (page === "notifications") html = renderNotifications();
    else if (page === "profile") html = renderProfile();
    else if (page === "periods") html = renderPeriods();
    else if (page === "basics") html = renderBasics();
    else if (page === "applications") html = renderApplicationsAdmin();
    else if (page === "matching-admin") html = renderMatchingAdmin();
    else if (page === "pairs") html = renderPairs();
    else if (page === "materials") html = renderMaterials();
    else if (page === "grades") html = renderGrades();
    else if (page === "rewards") html = renderRewards();
    else if (page === "notices") html = renderNoticesAdmin();
    else if (page === "logs") html = renderLogs();
    app.innerHTML = html;
  }

  function metric(label, value, note = "") {
    return `<section class="card metric-card"><div class="metric-label">${esc(label)}</div><div class="metric-value">${esc(value)}</div><div class="metric-note">${esc(note)}</div></section>`;
  }

  function progress(label, value, suffix = `${value}%`) {
    const clamped = Math.max(0, Math.min(100, Number(value) || 0));
    return `<div class="progress-row"><div class="progress-label"><span>${esc(label)}</span><strong>${esc(suffix)}</strong></div><div class="progress"><span style="width:${clamped}%"></span></div></div>`;
  }

  function renderDashboard() {
    if (state.currentRole === "admin") return renderAdminDashboard();
    return renderStudentDashboard();
  }

  function renderStudentDashboard() {
    const period = currentPeriod();
    const application = currentApplication();
    const isClimber = state.currentRole === "climber";
    const confirmed = state.candidate.confirmations[state.currentRole];
    const tasks = [];
    if (application?.status !== "已通过") tasks.push(["完善报名资料", `当前状态：${application?.status || "未报名"}`, "application"]);
    if (!state.pair) tasks.push([confirmed ? "等待对方确认结对" : "确认匹配结果", `截止：${state.candidate.deadline}`, "matching"]);
    if (state.pair && state.photos.filter((item) => item.peerConfirmed && item.adminStatus !== "驳回").length < period.photoMin) tasks.push(["补充过程照片", `有效 ${state.photos.filter((item) => item.peerConfirmed && item.adminStatus !== "驳回").length} 张，建议范围 ${period.photoMin}—${period.photoMax} 张`, "support"]);
    if (tasks.length === 0) tasks.push(["本阶段任务已完成", "有新任务时会在这里提醒", "notifications"]);

    return `<div class="page-stack">
      <section class="hero">
        <div>
          <div class="eyebrow">${esc(period.shortName)} · ${esc(period.status)}阶段</div>
          <h2>${isClimber ? "欢迎回来，陈晨" : "欢迎回来，林晓"}</h2>
          <p>${state.pair ? "你已进入正式帮扶阶段，请按要求上传过程照片并在验收阶段提交感悟。" : "本期匹配结果已发布，请在截止时间前完成结对确认。"} 页面内容为本地演示数据，不会影响真实学生记录。</p>
        </div>
        <div class="hero-actions">
          <button class="button primary" data-nav="${state.pair ? "support" : "matching"}">${state.pair ? "进入我的帮扶" : "查看匹配结果"}</button>
          <button class="button secondary" data-nav="application">查看报名资料</button>
        </div>
      </section>

      <div class="grid four">
        ${metric("报名状态", application?.status || "未报名", `${application?.studentNo || "—"} · ${isClimber ? "攀登者" : "领航者"}`)}
        ${metric("结对状态", state.pair ? "已结对" : state.candidate.status, state.pair ? state.pair.id : `匹配分 ${state.candidate.score}`)}
        ${metric("有效过程照片", state.photos.filter((item) => item.peerConfirmed && item.adminStatus !== "驳回").length, `建议范围 ${period.photoMin}—${period.photoMax} 张`)}
        ${metric("未读消息", unreadCount(), "点击消息中心查看")}
      </div>

      <div class="grid sidebar-layout">
        <section class="card">
          <div class="card-header"><div><h2>我的待办</h2><p>根据角色、阶段和业务状态自动生成</p></div></div>
          <div class="card-body">
            <ul class="task-list">${tasks.map(([title, note, page]) => `<li class="task-item"><span class="task-icon">✓</span><div class="content"><strong>${esc(title)}</strong><small>${esc(note)}</small></div><button class="button small secondary" data-nav="${page}">处理</button></li>`).join("")}</ul>
          </div>
        </section>
        <section class="card">
          <div class="card-header"><div><h2>期次进度</h2><p>关键节点由管理员配置</p></div></div>
          <div class="card-body">
            <div class="timeline">
              ${[["报名", "已完成", "done"],["匹配确认", state.pair ? "已完成" : "进行中", state.pair ? "done" : "active"],["跟踪", period.tracking, "active"],["验收", period.acceptance, ""]].map(([title, desc, cls]) => `<div class="timeline-item ${cls}"><span class="timeline-dot">✓</span><div><div class="timeline-title">${esc(title)}</div><div class="timeline-desc">${esc(desc)}</div></div></div>`).join("")}
            </div>
          </div>
        </section>
      </div>
    </div>`;
  }

  function renderAdminDashboard() {
    const passed = state.applications.filter((item) => item.status === "已通过").length;
    const pending = state.applications.filter((item) => item.status === "审核中").length;
    const validPhotos = state.photos.filter((item) => item.peerConfirmed && item.adminStatus !== "驳回").length;
    return `<div class="page-stack">
      <section class="hero">
        <div><div class="eyebrow">${esc(currentPeriod().shortName)} · 管理工作台</div><h2>跟踪阶段运营概览</h2><p>指标均根据本原型中的示例记录实时计算。你可以审核报名、重新运行匹配、导入 CSV 成绩并导出真实文件。</p></div>
        <div class="hero-actions"><button class="button primary" data-nav="applications">处理待审核</button><button class="button secondary" data-nav="matching-admin">查看匹配</button></div>
      </section>
      <div class="grid four">
        ${metric("报名人数", state.applications.length, `领航者 ${state.applications.filter((a) => a.role === "navigator").length} · 攀登者 ${state.applications.filter((a) => a.role === "climber").length}`)}
        ${metric("审核通过", passed, `待审核 ${pending} 人`)}
        ${metric("正式帮扶对", state.pair ? 1 : 0, state.pair ? state.pair.id : "等待双方确认")}
        ${metric("成绩完整率", `${Math.round(state.grades.filter((g) => g.finalGpa !== "" && g.finalGpa != null).length / state.grades.length * 100)}%`, `${state.grades.length} 条示例成绩`)}
      </div>
      <div class="grid two">
        <section class="card"><div class="card-header"><div><h2>关键进度</h2><p>点击相关模块可查看底层明细</p></div></div><div class="card-body">
          ${progress("报名审核", Math.round(passed / state.applications.length * 100), `${passed}/${state.applications.length}`)}
          ${progress("结对确认", state.pair ? 100 : 50, state.pair ? "已完成" : "1/2 已确认")}
          ${progress("有效过程照片", Math.round(validPhotos / currentPeriod().photoMin * 100), `${validPhotos} 张 · 建议 ${currentPeriod().photoMin}—${currentPeriod().photoMax} 张`)}
          ${progress("成绩单导入", state.gradeImports.previous.length && state.gradeImports.current.length ? 100 : 0, state.gradeImports.previous.length && state.gradeImports.current.length ? "两学期均已导入" : "等待两张成绩单")}
        </div></section>
        <section class="card"><div class="card-header"><div><h2>异常与待处理</h2><p>处理后指标会同步变化</p></div></div><div class="card-body"><ul class="task-list">
          <li class="task-item"><span class="task-icon">审</span><div class="content"><strong>${pending} 条报名待审核</strong><small>包含攀登者资料完整性审核</small></div><button class="button small secondary" data-nav="applications">查看</button></li>
          <li class="task-item"><span class="task-icon">材</span><div class="content"><strong>${state.photos.filter((p) => p.adminStatus === "待审核").length} 份照片待审核</strong><small>仅双方确认后的照片进入审核</small></div><button class="button small secondary" data-nav="materials">查看</button></li>
          <li class="task-item"><span class="task-icon">德</span><div class="content"><strong>${state.rewardFiles.length} 份德育分名单</strong><small>上传后学生可直接下载文件</small></div><button class="button small secondary" data-nav="rewards">查看</button></li>
        </ul></div></section>
      </div>
    </div>`;
  }

  function renderApplication() {
    const item = currentApplication();
    const isNavigator = state.currentRole === "navigator";
    const errors = state.ui.errors.application || {};
    if (!item) return `<section class="card"><div class="empty-state"><div class="empty-icon">▤</div><h3>本期尚未报名</h3><p>当前期次处于${esc(currentPeriod().status)}阶段，报名入口已关闭。</p><button class="button primary" disabled title="报名期已结束">创建报名</button></div></section>`;
    return `<div class="page-stack">
      <div class="notice ${item.status === "已驳回" ? "danger" : ""}">当前报名状态：${status(item.status)} ${item.rejectReason ? `驳回原因：${esc(item.rejectReason)}` : "报名资料为当期快照，不会随以后个人资料修改而改变。"}</div>
      <form id="application-form" class="card" novalidate>
        <div class="card-header"><div><h2>${isNavigator ? "领航者" : "攀登者"}报名资料</h2><p>学号 ${esc(item.studentNo)} · ${esc(currentPeriod().name)}</p></div>${status(item.status)}</div>
        <div class="card-body">
          <div class="form-grid">
            <div class="field"><label>真实姓名</label><input value="${esc(item.name)}" disabled><span class="hint">账号基础资料，报名页不可修改</span></div>
            <div class="field"><label>身份</label><input value="${isNavigator ? "学业领航者" : "学业攀登者"}" disabled></div>
            <div class="field"><label>年级</label><input value="${esc(item.grade || state.profiles[state.currentRole]?.grade || "未填写")}" disabled><span class="hint">可在个人中心修改</span></div>
            <div class="field"><label for="application-gpa">上学期期末全部课程绩点 *</label><input id="application-gpa" name="gpa" type="number" min="0" max="5" step="0.01" value="${esc(item.gpa)}"><span class="error">${esc(errors.gpa || "")}</span></div>
            <div class="field full"><label for="application-subjects">${isNavigator ? "擅长科目" : "困难科目"} *</label><input id="application-subjects" name="subjectsText" value="${esc(item.subjects.join("、"))}" placeholder="请手动填写，多个科目用逗号或顿号分隔"><span class="hint">例如：高等数学、数据结构、线性代数</span><span class="error">${esc(errors.subjects || "")}</span></div>
            <div class="field full"><label for="application-statement">${isNavigator ? "个人自述" : "学习困难描述"}（20—300 字，原型缩短最低字数便于体验）*</label><textarea id="application-statement" name="statement" maxlength="300">${esc(item.statement)}</textarea><span class="hint"><span data-word-count="application-statement">${item.statement.length}</span>/300 字</span><span class="error">${esc(errors.statement || "")}</span></div>
          </div>
        </div>
        <div class="card-footer">
          <button type="button" class="button ghost" data-action="restore-application">恢复上次保存</button>
          <button type="submit" class="button secondary" name="intent" value="draft">保存草稿</button>
          <button type="submit" class="button primary" name="intent" value="submit">提交审核</button>
        </div>
      </form>
    </div>`;
  }

  function renderStudentMatching() {
    const isNavigator = state.currentRole === "navigator";
    const other = applicant(isNavigator ? state.candidate.climberId : state.candidate.navigatorId);
    const otherProfile = state.profiles[isNavigator ? "climber" : "navigator"] || {};
    if (state.pair) {
      return `<div class="page-stack"><div class="notice success">双方已确认，正式帮扶对 <strong>${esc(state.pair.id)}</strong> 已生成。姓名与联系方式现已向双方开放。</div>
      <section class="card"><div class="card-header"><div><h2>结对成功</h2><p>2026 秋 · 1 对 1 学业帮扶</p></div>${status("双方已确认")}</div><div class="card-body"><dl>
        <div class="profile-row"><dt>帮扶对象</dt><dd>${esc(other.name)} · ${esc(other.grade)} · ${esc(other.major)}</dd></div>
        <div class="profile-row"><dt>联系电话</dt><dd>${esc(otherProfile.phone || "未填写")}</dd></div>
        <div class="profile-row"><dt>微信号</dt><dd>${esc(otherProfile.wechat || "未填写")}</dd></div>
        <div class="profile-row"><dt>重点科目</dt><dd>${tags(["高等数学", "数据结构"])}</dd></div>
        <div class="profile-row"><dt>确认时间</dt><dd>${esc(state.pair.confirmedAt)}</dd></div>
      </dl></div><div class="card-footer"><button class="button secondary" data-action="reset-confirmation">重新体验确认流程</button><button class="button primary" data-nav="support">进入我的帮扶</button></div></section></div>`;
    }
    const mine = state.candidate.confirmations[state.currentRole];
    return `<div class="page-stack">
      <div class="notice warning">这是匿名候选配对。双方确认前不展示姓名、学号、电话和详细成绩。截止时间：${esc(state.candidate.deadline)}</div>
      <div class="grid sidebar-layout">
        <section class="card">
          <div class="card-header"><div><h2>推荐匹配对象</h2><p>${isNavigator ? "学业攀登者" : "学业领航者"} · 匿名资料</p></div>${status(mine ? "单方已确认" : "待双方确认")}</div>
          <div class="card-body">
            <div class="score-overview"><div class="score-circle"><strong>${state.candidate.score}<span>综合匹配分</span></strong></div><div>
              ${progress("科目匹配", Math.round(state.candidate.scores.subject / 40 * 100), `${state.candidate.scores.subject}/40`)}
              ${progress("学习风格", Math.round(state.candidate.scores.style / 15 * 100), `${state.candidate.scores.style}/15`)}
              ${progress("专业匹配", Math.round(state.candidate.scores.major / 25 * 100), `${state.candidate.scores.major}/25`)}
            </div></div>
            <div class="divider"></div>
            <dl>
              <div class="profile-row"><dt>共同科目</dt><dd>${tags(["高等数学", "数据结构"])}</dd></div>
              <div class="profile-row"><dt>学习方式</dt><dd>${tags(other.styles)}</dd></div>
              <div class="profile-row"><dt>性格特点</dt><dd>${tags(other.personality)}</dd></div>
              <div class="profile-row"><dt>专业关系</dt><dd>同学院 · 同专业</dd></div>
              <div class="profile-row"><dt>年级关系</dt><dd>${esc(other.grade)} · 相邻年级</dd></div>
              <div class="profile-row"><dt>匿名自述</dt><dd>${esc(other.statement)}</dd></div>
            </dl>
          </div>
          <div class="card-footer">
            <button class="button danger" data-action="reject-match" ${mine ? "disabled title=\"已确认后需联系管理员处理\"" : ""}>拒绝结对</button>
            <button class="button primary" data-action="confirm-match" ${mine ? "disabled" : ""}>${mine ? "已确认，等待对方" : "确认结对"}</button>
          </div>
        </section>
        <section class="card"><div class="card-header"><div><h2>确认进度</h2><p>双方均确认后才生成帮扶对</p></div></div><div class="card-body"><div class="timeline">
          <div class="timeline-item ${state.candidate.confirmations.navigator ? "done" : "active"}"><span class="timeline-dot">✓</span><div><div class="timeline-title">领航者确认</div><div class="timeline-desc">${state.candidate.confirmations.navigator ? "已确认" : "等待操作"}</div></div></div>
          <div class="timeline-item ${state.candidate.confirmations.climber ? "done" : "active"}"><span class="timeline-dot">✓</span><div><div class="timeline-title">攀登者确认</div><div class="timeline-desc">${state.candidate.confirmations.climber ? "已确认" : "等待操作"}</div></div></div>
          <div class="timeline-item ${state.pair ? "done" : ""}"><span class="timeline-dot">3</span><div><div class="timeline-title">生成正式帮扶对</div><div class="timeline-desc">确认后开放双方联系方式</div></div></div>
        </div></div></section>
      </div>
    </div>`;
  }

  function renderSupport() {
    if (!state.pair) {
      return `<section class="card"><div class="empty-state"><div class="empty-icon">◎</div><h3>尚未形成正式帮扶对</h3><p>完成双方确认后，这里将开放过程照片和活动感悟。</p><button class="button primary" data-nav="matching">前往确认匹配</button></div></section>`;
    }
    const role = state.currentRole;
    const reflection = state.reflections[role];
    const validPhotos = state.photos.filter((p) => p.peerConfirmed && p.adminStatus !== "驳回").length;
    return `<div class="page-stack">
      <section class="hero"><div><div class="eyebrow">帮扶对 ${esc(state.pair.id)}</div><h2>林晓 × 陈晨</h2><p>重点科目：高等数学、数据结构。当前处于跟踪阶段，过程照片需经对方确认后才计入有效材料。</p></div><div class="hero-actions"><span class="status green">进行中</span></div></section>
      <div class="grid two">
        ${metric("有效照片", validPhotos, `建议范围 ${currentPeriod().photoMin}—${currentPeriod().photoMax} 张`)}
        ${metric("我的感悟", reflection.status, "验收阶段材料")}
      </div>
      ${renderPhotoCard()}
      ${renderReflectionCard()}
    </div>`;
  }

  function renderPhotoCard() {
    return `<section class="card"><div class="card-header"><div><h2>过程照片</h2><p>交互原型仅在浏览器保存文件名和说明，不上传真实服务器</p></div><button class="button primary" data-action="open-photo-upload">上传照片</button></div><div class="card-body">
      <div class="photo-grid">${state.photos.map((photo) => `<article class="photo-card"><div class="photo-placeholder">图片占位预览<br>${esc(photo.name)}</div><div class="photo-meta"><strong title="${esc(photo.name)}">${esc(photo.name)}</strong><small>${esc(photo.note || "无说明")}</small><small>${esc(photo.uploader)} · ${esc(photo.date)}</small><div style="margin-top:8px">${status(photo.peerConfirmed ? photo.adminStatus : "待对方确认")}</div>${!photo.peerConfirmed && photo.uploader !== roleMeta[state.currentRole].name ? `<button class="button small secondary" style="margin-top:8px" data-action="confirm-photo" data-id="${photo.id}">确认真实</button>` : ""}</div></article>`).join("")}</div>
      ${state.photos.length === 0 ? `<div class="empty-state"><h3>还没有过程照片</h3><p>上传第一份过程材料，文件名和说明会保存到当前浏览器。</p></div>` : ""}
    </div></section>`;
  }

  function renderReflectionCard() {
    const reflection = state.reflections[state.currentRole];
    const errors = state.ui.errors.reflection || {};
    return `<form id="reflection-form" class="card" novalidate><div class="card-header"><div><h2>${state.currentRole === "navigator" ? "领航者" : "攀登者"}活动感悟</h2><p>正式要求 300—500 字，支持草稿和驳回后重提</p></div>${status(reflection.status)}</div><div class="card-body">
      <div class="notice">参考方向：参与前状态、过程中的具体改变、自身收获、未来计划与活动建议。系统不代写正文。</div>
      <div class="field" style="margin-top:16px"><label for="reflection-text">感悟正文 *</label><textarea id="reflection-text" name="text" minlength="300" maxlength="500" ${reflection.status === "审核通过" ? "readonly" : ""}>${esc(reflection.text)}</textarea><span class="hint"><span data-word-count="reflection-text">${reflection.text.length}</span>/500 字</span><span class="error">${esc(errors.text || "")}</span></div>
      ${reflection.rejectReason ? `<div class="notice danger" style="margin-top:12px">驳回原因：${esc(reflection.rejectReason)}</div>` : ""}
    </div><div class="card-footer">${reflection.status !== "审核通过" ? `<button type="submit" class="button secondary" name="intent" value="draft">保存草稿</button><button type="submit" class="button primary" name="intent" value="submit">提交审核</button>` : `<span class="muted">已审核通过，不可自行修改</span>`}</div></form>`;
  }

  function percentile(rank, cohort) {
    if (!rank || !cohort) return null;
    return (cohort - rank + 1) / cohort * 100;
  }

  function improvement(grade) {
    const base = percentile(Number(grade.baseRank), Number(grade.baseCohort));
    const final = percentile(Number(grade.finalRank), Number(grade.finalCohort));
    if (base == null || final == null) return null;
    return final - base;
  }

  function renderResults() {
    const application = currentApplication();
    const grade = state.grades.find((g) => g.studentNo === application?.studentNo) || state.grades[0];
    const gpaChange = Number(grade.finalGpa) - Number(grade.baseGpa);
    const files = state.rewardFiles.filter((file) => file.status === "已发布");
    return `<div class="page-stack">
      <div class="notice">成绩由管理员分别导入上学期期末和本学期期末成绩单。这里仅展示你本人的全部课程绩点变化。</div>
      <div class="grid three">
        ${metric("上学期期末全部课程绩点", grade.baseGpa, "成绩单提取")}
        ${metric("本学期期末全部课程绩点", grade.finalGpa, "成绩单提取")}
        ${metric("绩点变化", `${gpaChange >= 0 ? "+" : ""}${gpaChange.toFixed(2)}`, "本学期 − 上学期")}
      </div>
      <section class="card"><div class="card-header"><div><h2>德育分加分文件</h2><p>由管理员上传发布；平台不展示证书或其他奖励</p></div></div><div class="card-body">${files.length ? `<ul class="list">${files.map((file) => `<li class="list-item"><span class="task-icon">文</span><div class="content"><strong>${esc(file.title)}</strong><small>${esc(file.fileName)} · ${esc(file.uploadedAt)}</small></div><button class="button secondary" data-action="download-reward-file" data-id="${file.id}">下载文件</button></li>`).join("")}</ul>` : `<div class="empty-state"><h3>暂无已发布文件</h3><p>管理员发布德育分名单后，可在这里下载。</p></div>`}</div></section>
    </div>`;
  }

  function renderNotifications() {
    const items = state.notifications.filter((item) => item.audience === state.currentRole || item.audience === "all");
    return `<section class="card"><div class="card-header"><div><h2>站内通知</h2><p>首版不发送短信或微信消息</p></div><div class="toolbar"><button class="button secondary" data-action="mark-all-read">全部已读</button></div></div><div class="card-body">${items.length ? `<ul class="list">${items.map((item) => `<li class="list-item"><span class="task-icon">${item.read ? "✓" : "●"}</span><div class="content"><strong>${esc(item.title)}</strong><small>${esc(item.content)}</small><small>${esc(item.date)}</small></div>${item.read ? status("已读") : `<button class="button small secondary" data-action="mark-read" data-id="${item.id}">标为已读</button>`}</li>`).join("")}</ul>` : `<div class="empty-state"><h3>暂无通知</h3><p>与你相关的业务提醒会显示在这里。</p></div>`}</div></section>`;
  }

  function renderProfile() {
    const item = currentApplication();
    return `<div class="grid sidebar-layout"><section class="card"><div class="card-header"><div><h2>账号资料</h2><p>长期账号信息，与每期报名记录分离</p></div><button class="button secondary" data-action="edit-profile">编辑资料</button></div><div class="card-body"><dl>
      <div class="profile-row"><dt>姓名</dt><dd>${esc(item.name)}</dd></div><div class="profile-row"><dt>学号</dt><dd>${esc(item.studentNo)}</dd></div><div class="profile-row"><dt>年级</dt><dd>${esc(state.profiles[state.currentRole]?.grade || item.grade || "未填写")}</dd></div><div class="profile-row"><dt>学院</dt><dd>${esc(item.college)}</dd></div><div class="profile-row"><dt>专业</dt><dd>${esc(item.major)}</dd></div><div class="profile-row"><dt>联系电话</dt><dd>${esc(state.profiles[state.currentRole]?.phone || "未填写")}</dd></div><div class="profile-row"><dt>微信号</dt><dd>${esc(state.profiles[state.currentRole]?.wechat || "未填写")}</dd></div>
    </dl></div></section><section class="card"><div class="card-header"><div><h2>历史参与</h2><p>仅展示自己参加过的期次，详情只读</p></div></div><div class="card-body"><ul class="list"><li class="list-item"><div class="content"><strong>2026 秋 · ${state.currentRole === "navigator" ? "领航者" : "攀登者"}</strong><small>${state.pair ? `帮扶对 ${state.pair.id}` : "确认中"}</small></div>${status(state.pair ? "进行中" : "待确认")}</li>${state.historyRecords.filter((record) => record.audience === state.currentRole).map((record) => `<li class="list-item"><div class="content"><strong>${esc(record.period)} · ${esc(record.role)}</strong><small>帮扶对 ${esc(record.pairId)} · ${esc(record.subjects.join("、"))}</small></div><button class="button small secondary" data-action="view-history" data-id="${record.id}">查看</button></li>`).join("")}</ul></div></section></div>`;
  }

  function renderPeriods() {
    return `<div class="page-stack"><div class="notice">期次是全部业务数据的顶层范围。已有报名数据的期次不能直接删除，只能结束或归档。</div><section class="card"><div class="card-header"><div><h2>活动期次</h2><p>创建期次后才能开展报名、匹配和材料管理</p></div><button class="button primary" data-action="create-period">创建期次</button></div><div class="card-body"><div class="table-wrap"><table><thead><tr><th>期次</th><th>状态</th><th>报名时间</th><th>匹配时间</th><th>绩点门槛</th><th>照片范围</th><th>操作</th></tr></thead><tbody>${state.periods.map((p) => `<tr><td><strong>${esc(p.name)}</strong><br><span class="muted">${esc(p.shortName)}</span></td><td>${status(p.status)}</td><td>${esc(p.registration)}</td><td>${esc(p.matching)}</td><td>全部课程绩点 ${p.minGpa} 或前 ${p.rankThreshold}%</td><td>${p.photoMin}—${p.photoMax} 张</td><td><div class="table-actions"><button class="button small secondary" data-action="edit-period" data-id="${p.id}">编辑</button><button class="button small ghost" data-action="switch-period-status" data-id="${p.id}">${p.status === "已结束" ? "解除归档" : "切换阶段"}</button></div></td></tr>`).join("")}</tbody></table></div></div></section></div>`;
  }

  function renderBasics() {
    const tab = state.ui.basicTab;
    const search = state.ui.search.toLowerCase();
    const courses = state.courses.filter((c) => !search || `${c.code}${c.name}${c.college}${c.category}`.toLowerCase().includes(search));
    const coursePanel = `<section class="card"><div class="card-header"><div><h2>课程库</h2><p>已使用课程只能停用，不能物理删除</p></div><button class="button primary" data-action="add-course">新增课程</button></div><div class="card-body"><div class="toolbar" style="margin-bottom:14px"><input id="basic-search" value="${esc(state.ui.search)}" placeholder="搜索课程编号、名称或学院"><button class="button secondary" data-action="apply-basic-search">搜索</button><span class="spacer"></span><button class="button ghost" data-action="download-course-template">下载导入模板</button></div><div class="table-wrap"><table><thead><tr><th>课程编号</th><th>课程名称</th><th>所属学院</th><th>分类</th><th>状态</th><th>操作</th></tr></thead><tbody>${courses.map((c) => `<tr><td>${esc(c.code)}</td><td><strong>${esc(c.name)}</strong></td><td>${esc(c.college)}</td><td>${esc(c.category)}</td><td>${status(c.enabled ? "已通过" : "已停用")}</td><td><button class="button small ghost" data-action="toggle-course" data-id="${c.id}">${c.enabled ? "停用" : "启用"}</button></td></tr>`).join("")}</tbody></table></div>${courses.length ? "" : `<div class="empty-state"><h3>没有匹配的课程</h3><p>尝试更换关键词，或新增一门课程。</p></div>`}</div></section>`;
    const orgPanel = `<section class="card"><div class="card-header"><div><h2>学院与专业</h2><p>新增专业会保存到当前浏览器</p></div><button class="button primary" data-action="add-major">新增专业</button></div><div class="card-body"><ul class="list">${state.colleges.map((college) => `<li class="list-item"><div class="content"><strong>${esc(college.name)}</strong><small>${college.majors.length ? esc(college.majors.join("、")) : "暂无专业；仅作为公共课程归属"}</small></div>${status(college.enabled ? "已通过" : "已停用")}</li>`).join("")}</ul></div></section>`;
    return `<div class="page-stack"><div class="tabs"><button class="tab ${tab === "courses" ? "active" : ""}" data-action="basic-tab" data-tab="courses">课程库</button><button class="tab ${tab === "org" ? "active" : ""}" data-action="basic-tab" data-tab="org">学院与专业</button></div>${tab === "courses" ? coursePanel : orgPanel}</div>`;
  }

  function renderApplicationsAdmin() {
    const filter = state.ui.applicationFilter;
    const list = state.applications.filter((a) => filter === "全部" || a.status === filter);
    return `<section class="card"><div class="card-header"><div><h2>报名审核</h2><p>${esc(currentPeriod().shortName)} · 共 ${state.applications.length} 条示例记录</p></div><button class="button secondary" data-action="export-applications">导出当前名单 CSV</button></div><div class="card-body"><div class="toolbar" style="margin-bottom:14px"><select id="application-filter"><option>全部</option>${["审核中","已通过","已驳回"].map((v) => `<option ${filter === v ? "selected" : ""}>${v}</option>`).join("")}</select><button class="button secondary" data-action="apply-application-filter">筛选</button><span class="spacer"></span><span class="muted">通过 ${state.applications.filter((a) => a.status === "已通过").length} · 待审核 ${state.applications.filter((a) => a.status === "审核中").length}</span></div><div class="table-wrap"><table><thead><tr><th>学生</th><th>身份</th><th>年级 / 专业</th><th>全部课程绩点</th><th>手填科目</th><th>初筛</th><th>状态</th><th>操作</th></tr></thead><tbody>${list.map((a) => {
      const autoPass = a.role === "climber" || (a.gpa >= currentPeriod().minGpa && a.failed === "无");
      return `<tr><td><strong>${esc(a.name)}</strong><br><span class="muted">${esc(a.studentNo)}</span></td><td>${a.role === "navigator" ? "领航者" : "攀登者"}</td><td>${esc(a.grade)} · ${esc(a.major)}</td><td>${a.gpa}</td><td>${tags(a.subjects.slice(0,3))}</td><td>${status(autoPass ? "已通过" : "存在异常")}</td><td>${status(a.status)}</td><td><div class="table-actions"><button class="button small secondary" data-action="view-application" data-id="${a.id}">详情</button>${a.status === "审核中" ? `<button class="button small success" data-action="approve-application" data-id="${a.id}">通过</button><button class="button small danger" data-action="reject-application" data-id="${a.id}">驳回</button>` : ""}</div></td></tr>`;
    }).join("")}</tbody></table></div>${list.length ? "" : `<div class="empty-state"><h3>该筛选条件下没有报名</h3><p>切换状态筛选查看其他记录。</p></div>`}</div></section>`;
  }

  function renderMatchingAdmin() {
    const results = state.matchingResults;
    return `<div class="page-stack"><div class="notice warning">运行匹配会根据当前审核通过人员重新生成本地推荐方案。已发布且有人确认的配对不会被静默覆盖。</div>
      <div class="grid four">${metric("可用领航者", state.applications.filter((a) => a.role === "navigator" && a.status === "已通过").length, "审核通过且未退出")}${metric("可用攀登者", state.applications.filter((a) => a.role === "climber" && a.status === "已通过").length, "审核通过且未退出")}${metric("推荐组合", results.length, "最低分 60")}${metric("平均匹配分", results.length ? Math.round(results.reduce((sum, r) => sum + r.score, 0) / results.length) : 0, "满分 100")}</div>
      <section class="card"><div class="card-header"><div><h2>匹配方案</h2><p>默认权重：科目 40、风格 15、性格 10、专业 25、年级 5、绩点差距 5</p></div><div class="toolbar"><button class="button secondary" data-action="run-matching">重新运行匹配</button><button class="button primary" data-action="publish-matching">发布待发布结果</button></div></div><div class="card-body"><div class="table-wrap"><table><thead><tr><th>领航者</th><th>攀登者</th><th>总分</th><th>科目</th><th>风格</th><th>性格</th><th>专业</th><th>年级</th><th>差距</th><th>状态</th><th>操作</th></tr></thead><tbody>${results.map((r) => `<tr><td>${esc(applicant(r.navigatorId).name)}</td><td>${esc(applicant(r.climberId).name)}</td><td><strong>${r.score}</strong></td><td>${r.subject}/40</td><td>${r.style}/15</td><td>${r.personality}/10</td><td>${r.major}/25</td><td>${r.grade}/5</td><td>${r.gap}/5</td><td>${status(r.status)}</td><td><button class="button small secondary" data-action="view-score" data-id="${r.id}">得分详情</button></td></tr>`).join("")}</tbody></table></div>${results.length ? "" : `<div class="empty-state"><h3>还没有匹配方案</h3><p>运行匹配后，系统会基于当前匹配池生成一对一推荐。</p><button class="button primary" data-action="run-matching">运行匹配</button></div>`}</div></section></div>`;
  }

  function renderPairs() {
    return `<section class="card"><div class="card-header"><div><h2>正式帮扶对</h2><p>查看阶段状态和异常处理</p></div><button class="button secondary" data-action="export-pairs">导出帮扶对 CSV</button></div><div class="card-body">${state.pair ? `<div class="table-wrap"><table><thead><tr><th>编号</th><th>领航者</th><th>攀登者</th><th>重点科目</th><th>有效照片</th><th>感悟</th><th>状态</th><th>操作</th></tr></thead><tbody><tr><td><strong>${esc(state.pair.id)}</strong></td><td>林晓</td><td>陈晨</td><td>${tags(["高等数学","数据结构"])}</td><td>${state.photos.filter((p) => p.peerConfirmed && p.adminStatus !== "驳回").length} 张</td><td>${Object.values(state.reflections).filter((r) => r.status === "审核通过").length}/2</td><td>${status(state.pair.status)}</td><td><div class="table-actions"><button class="button small secondary" data-action="view-pair">查看详情</button><button class="button small danger" data-action="dissolve-pair">解除</button></div></td></tr></tbody></table></div>` : `<div class="empty-state"><div class="empty-icon">◎</div><h3>当前没有正式帮扶对</h3><p>候选配对需要双方确认后才会出现在这里。</p><button class="button primary" data-nav="matching-admin">前往匹配管理</button></div>`}</div></section>`;
  }

  function renderMaterials() {
    const filter = state.ui.materialFilter;
    const materials = [
      ...state.photos.map((p) => ({ id: p.id, type: "过程照片", submitter: p.uploader, pair: state.pair?.id || "待结对", date: p.date, status: p.peerConfirmed ? p.adminStatus : "待对方确认" })),
      ...Object.entries(state.reflections).map(([role, r]) => ({ id: `rf-${role}`, type: `${role === "navigator" ? "领航者" : "攀登者"}感悟`, submitter: roleMeta[role].name, pair: state.pair?.id || "待结对", date: "—", status: r.status })),
    ].filter((m) => filter === "全部" || m.status === filter);
    return `<section class="card"><div class="card-header"><div><h2>材料审核队列</h2><p>只有满足前置确认条件的材料才进入管理员审核</p></div></div><div class="card-body"><div class="toolbar" style="margin-bottom:14px"><select id="material-filter"><option>全部</option>${["待审核","通过","草稿","待对方确认","审核通过"].map((v) => `<option ${filter === v ? "selected" : ""}>${v}</option>`).join("")}</select><button class="button secondary" data-action="apply-material-filter">筛选</button></div><div class="table-wrap"><table><thead><tr><th>材料类型</th><th>提交人</th><th>帮扶对</th><th>时间</th><th>状态</th><th>操作</th></tr></thead><tbody>${materials.map((m) => `<tr><td><strong>${esc(m.type)}</strong></td><td>${esc(m.submitter)}</td><td>${esc(m.pair)}</td><td>${esc(m.date)}</td><td>${status(m.status)}</td><td><div class="table-actions"><button class="button small secondary" data-action="view-material" data-id="${m.id}">查看</button>${m.status === "待审核" || m.status === "已提交" ? `<button class="button small success" data-action="approve-material" data-id="${m.id}">通过</button><button class="button small danger" data-action="reject-material" data-id="${m.id}">驳回</button>` : ""}</div></td></tr>`).join("")}</tbody></table></div>${materials.length ? "" : `<div class="empty-state"><h3>没有符合条件的材料</h3><p>尝试切换筛选状态。</p></div>`}</div></section>`;
  }

  function renderGrades() {
    const previous = state.gradeImports.previous;
    const current = state.gradeImports.current;
    const hasErrors = [...previous, ...current].some((row) => row._error);
    const previewRows = Array.from(new Set([...previous, ...current].map((row) => row.studentNo))).map((studentNo) => ({ previous: previous.find((row) => row.studentNo === studentNo), current: current.find((row) => row.studentNo === studentNo) }));
    const importPanel = `<section class="card"><div class="card-header"><div><h2>导入两学期成绩单</h2><p>分别提取全部课程绩点和专业排名；原型支持 CSV，正式系统可接入 XLSX 解析</p></div><button class="button secondary" data-action="download-grade-template">下载 CSV 模板</button></div><div class="card-body"><div class="grid two"><div class="field"><label for="transcript-previous">上学期期末成绩单 *</label><input id="transcript-previous" type="file" accept=".csv,text/csv"><span class="hint">${previous.length ? `已解析 ${previous.length} 行` : "尚未选择文件"}</span></div><div class="field"><label for="transcript-current">本学期期末成绩单 *</label><input id="transcript-current" type="file" accept=".csv,text/csv"><span class="hint">${current.length ? `已解析 ${current.length} 行` : "尚未选择文件"}</span></div></div><div class="notice" style="margin-top:14px">模板字段：studentNo,name,gpa,rank,cohort。学生端不展示排名，排名仅供管理员统计。</div>${previewRows.length ? `<div class="table-wrap" style="margin-top:14px"><table><thead><tr><th>学号</th><th>姓名</th><th>上学期绩点/排名</th><th>本学期绩点/排名</th><th>校验</th></tr></thead><tbody>${previewRows.map((row) => `<tr><td>${esc(row.previous?.studentNo || row.current?.studentNo)}</td><td>${esc(row.previous?.name || row.current?.name)}</td><td>${row.previous ? `${esc(row.previous.gpa)} · ${esc(row.previous.rank)}/${esc(row.previous.cohort)}` : "未导入"}</td><td>${row.current ? `${esc(row.current.gpa)} · ${esc(row.current.rank)}/${esc(row.current.cohort)}` : "未导入"}</td><td>${status(row.previous && row.current && !row.previous._error && !row.current._error ? "已通过" : "存在异常")}</td></tr>`).join("")}</tbody></table></div><div class="form-actions"><button class="button ghost" data-action="clear-grade-preview">取消预览</button><button class="button primary" data-action="commit-grade-import" ${!previous.length || !current.length || hasErrors ? "disabled title=\"请导入两张无异常的成绩单\"" : ""}>提取并保存绩点与排名</button></div>` : ""}</div></section>`;
    return `<div class="page-stack">${importPanel}
      <section class="card"><div class="card-header"><div><h2>两学期期末成绩对比</h2><p>排名仅管理员可见</p></div><button class="button secondary" data-action="export-grades">导出当前数据 CSV</button></div><div class="card-body"><div class="table-wrap"><table><thead><tr><th>学生</th><th>身份</th><th>上学期期末全部课程绩点</th><th>本学期期末全部课程绩点</th><th>上学期排名</th><th>本学期排名</th><th>排名提升</th><th>结果</th></tr></thead><tbody>${state.grades.map((g) => { const lift = improvement(g); return `<tr><td><strong>${esc(g.name)}</strong><br><span class="muted">${esc(g.studentNo)}</span></td><td>${esc(g.role)}</td><td>${esc(g.baseGpa)}</td><td>${esc(g.finalGpa)}</td><td>${esc(g.baseRank)}/${esc(g.baseCohort)}</td><td>${esc(g.finalRank)}/${esc(g.finalCohort)}</td><td class="${lift != null && lift >= 0 ? "number-positive" : "number-negative"}">${lift == null ? "数据不完整" : `${lift >= 0 ? "+" : ""}${lift.toFixed(1)}pp`}</td><td>${status(lift == null ? "存在异常" : lift >= 10 ? "已通过" : "待判定")}</td></tr>`; }).join("")}</tbody></table></div></div></section></div>`;
  }

  function renderRewards() {
    return `<div class="page-stack"><form id="reward-file-form" class="card" novalidate><div class="card-header"><div><h2>上传德育分名单</h2><p>上传后可发布给学生下载；证书和其他奖励不在平台展示</p></div></div><div class="card-body"><div class="notice">本地原型会将不超过 2 MB 的文件保存在当前浏览器。支持 CSV、XLS/XLSX 和 PDF。</div><div class="form-grid" style="margin-top:14px"><div class="field"><label for="reward-file-title">文件标题 *</label><input id="reward-file-title" name="title" maxlength="80" placeholder="例如：2026秋德育分加分名单（第一批）"></div><div class="field"><label for="reward-file-input">选择名单文件 *</label><input id="reward-file-input" name="file" type="file" accept=".csv,.xls,.xlsx,.pdf"></div></div><span class="error" id="reward-file-error"></span></div><div class="card-footer"><button type="submit" class="button primary">上传并发布</button></div></form><section class="card"><div class="card-header"><div><h2>已上传文件</h2><p>学生只能下载状态为“已发布”的文件</p></div></div><div class="card-body">${state.rewardFiles.length ? `<div class="table-wrap"><table><thead><tr><th>标题</th><th>文件名</th><th>上传时间</th><th>状态</th><th>操作</th></tr></thead><tbody>${state.rewardFiles.map((file) => `<tr><td><strong>${esc(file.title)}</strong></td><td>${esc(file.fileName)}</td><td>${esc(file.uploadedAt)}</td><td>${status(file.status)}</td><td><div class="table-actions"><button class="button small secondary" data-action="download-reward-file" data-id="${file.id}">下载</button><button class="button small ghost" data-action="toggle-reward-file" data-id="${file.id}">${file.status === "已发布" ? "撤回" : "发布"}</button><button class="button small danger" data-action="delete-reward-file" data-id="${file.id}">删除</button></div></td></tr>`).join("")}</tbody></table></div>` : `<div class="empty-state"><h3>暂无德育分文件</h3><p>上传名单后，学生可在“成绩与德育分”页面下载。</p></div>`}</div></section></div>`;
  }

  function renderNoticesAdmin() {
    const sent = state.notifications;
    return `<div class="grid sidebar-layout"><form id="notice-form" class="card" novalidate><div class="card-header"><div><h2>发送站内通知</h2><p>不会发送短信、微信或邮件</p></div></div><div class="card-body"><div class="form-grid"><div class="field"><label>接收范围 *</label><select name="audience"><option value="all">全体参与者</option><option value="navigator">仅领航者</option><option value="climber">仅攀登者</option></select></div><div class="field"><label>发送时间</label><input value="立即发送" disabled></div><div class="field full"><label>通知标题 *</label><input name="title" maxlength="50" placeholder="例如：验收阶段材料提醒"></div><div class="field full"><label>通知内容 *</label><textarea name="content" maxlength="300" placeholder="说明截止时间和处理入口"></textarea></div></div></div><div class="card-footer"><button type="submit" class="button primary">发送站内通知</button></div></form><section class="card"><div class="card-header"><div><h2>发送记录</h2><p>本地原型中的实际通知记录</p></div></div><div class="card-body"><ul class="list">${sent.slice(0,8).map((n) => `<li class="list-item"><div class="content"><strong>${esc(n.title)}</strong><small>${esc(n.content)}</small><small>${esc(n.date)} · ${n.audience === "all" ? "全体" : roleMeta[n.audience]?.label || n.audience}</small></div></li>`).join("")}</ul></div></section></div>`;
  }

  function renderLogs() {
    return `<section class="card"><div class="card-header"><div><h2>操作日志</h2><p>原型中的关键操作会真实追加到此列表</p></div><button class="button secondary" data-action="export-logs">导出日志 CSV</button></div><div class="card-body"><div class="table-wrap"><table><thead><tr><th>时间</th><th>操作人</th><th>动作</th><th>对象</th><th>说明</th></tr></thead><tbody>${state.logs.map((l) => `<tr><td class="nowrap">${esc(l.time)}</td><td>${esc(l.actor)}</td><td><strong>${esc(l.action)}</strong></td><td>${esc(l.target)}</td><td>${esc(l.detail)}</td></tr>`).join("")}</tbody></table></div></div></section>`;
  }

  function downloadCsv(filename, rows) {
    if (!rows.length) {
      toast("当前没有可导出的数据", "error");
      return;
    }
    const headers = Object.keys(rows[0]);
    const csv = [headers.join(","), ...rows.map((row) => headers.map((key) => `"${String(row[key] ?? "").replaceAll('"', '""')}"`).join(","))].join("\r\n");
    const blob = new Blob(["\ufeff" + csv], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
    toast(`已生成 ${filename}`);
  }

  function downloadStoredFile(file) {
    if (!file?.dataUrl) { toast("文件内容不可用，请联系管理员重新上传", "error"); return; }
    const link = document.createElement("a");
    link.href = file.dataUrl;
    link.download = file.fileName;
    document.body.appendChild(link);
    link.click();
    link.remove();
    toast(`已下载 ${file.fileName}`);
  }

  function confirmMatch() {
    openModal("确认结对", `<div class="notice warning">确认后将等待对方操作。双方均确认时，系统会生成正式帮扶对并开放联系方式。</div><label class="check-option" style="margin-top:14px"><input id="confirm-truth" type="checkbox" required>我已阅读匿名资料并确认愿意结对</label><div class="error" id="confirm-error"></div>`, "确认结对", () => {
      if (!document.getElementById("confirm-truth").checked) {
        document.getElementById("confirm-error").textContent = "请先勾选确认声明";
        return false;
      }
      state.candidate.confirmations[state.currentRole] = true;
      if (state.candidate.confirmations.navigator && state.candidate.confirmations.climber) {
        state.candidate.status = "双方已确认";
        state.pair = { id: "BF-2026-008", navigatorId: "N001", climberId: "C001", status: "进行中", confirmedAt: new Date().toLocaleString("zh-CN", { hour12: false }) };
        addNotification("all", "正式帮扶对已生成", "双方已完成确认，现可进入“我的帮扶”查看联系方式和阶段任务。");
        addLog("确认结对", "BF-2026-008", "双方确认，生成正式帮扶对");
      } else {
        state.candidate.status = "单方已确认";
        addNotification(state.currentRole === "navigator" ? "climber" : "navigator", "对方已确认结对", "请在截止时间前完成你的确认。" );
      }
      commit("结对确认已保存");
      return true;
    });
  }

  function rejectMatch() {
    openModal("拒绝本次配对", `<div class="notice danger">拒绝后本候选配对立即失效，双方将回到待匹配池。本期后续轮次默认不会再次推荐同一组合。</div><div class="field" style="margin-top:14px"><label for="reject-reason">拒绝原因（选填）</label><select id="reject-reason"><option>时间安排不合适</option><option>帮扶科目不符合预期</option><option>学习方式不合适</option><option>其他原因</option></select></div>`, "确认拒绝", () => {
      const reason = document.getElementById("reject-reason").value;
      state.candidate.status = "已拒绝";
      state.candidate.rejectedPairs.push({ by: state.currentRole, reason, at: new Date().toISOString() });
      addLog("拒绝配对", state.candidate.id, reason);
      addNotification("admin", "候选配对被拒绝", `${roleMeta[state.currentRole].label}拒绝了候选配对，需进入下一轮匹配。`);
      commit("已拒绝本次配对，管理员将处理下一轮", "error");
      return true;
    });
  }

  function createOrEditPeriod(id) {
    const existing = state.periods.find((p) => p.id === id);
    openModal(existing ? "编辑期次" : "创建期次", `<div class="form-grid"><div class="field full"><label>期次名称 *</label><input id="period-name" value="${esc(existing?.name || "2027—2028 学年春期")}"></div><div class="field"><label>期次简称 *</label><input id="period-short" value="${esc(existing?.shortName || "2028 春")}"></div><div class="field"><label>当前状态</label><select id="period-status">${["预告","报名中","匹配中","确认中","跟踪","验收","已结束"].map((v) => `<option ${existing?.status === v ? "selected" : ""}>${v}</option>`).join("")}</select></div><div class="field"><label>领航者最低全部课程绩点</label><input id="period-gpa" type="number" min="0" max="5" step="0.1" value="${existing?.minGpa ?? 3.5}"></div><div class="field"><label>照片数量下限</label><input id="period-photo-min" type="number" min="0" max="30" value="${existing?.photoMin ?? 4}"></div><div class="field"><label>照片数量上限</label><input id="period-photo-max" type="number" min="1" max="30" value="${existing?.photoMax ?? 8}"></div><div class="field full"><span class="error" id="period-error"></span></div></div>`, existing ? "保存修改" : "创建期次", () => {
      const name = document.getElementById("period-name").value.trim();
      const shortName = document.getElementById("period-short").value.trim();
      const minGpa = Number(document.getElementById("period-gpa").value);
      const photoMin = Number(document.getElementById("period-photo-min").value);
      const photoMax = Number(document.getElementById("period-photo-max").value);
      if (!name || !shortName || minGpa < 0 || minGpa > 5 || photoMin < 0 || photoMax < photoMin) {
        document.getElementById("period-error").textContent = "请填写完整期次名称，并检查门槛数值";
        return false;
      }
      if (existing) {
        Object.assign(existing, { name, shortName, status: document.getElementById("period-status").value, minGpa, photoMin, photoMax });
        addLog("编辑期次", existing.shortName, "更新名称、状态或门槛");
      } else {
        const newPeriod = { id: `p${Date.now()}`, name, shortName, status: document.getElementById("period-status").value, registration: "待配置", matching: "待配置", confirmationDeadline: "待配置", tracking: "待配置", acceptance: "待配置", minGpa, rankThreshold: 30, photoMin, photoMax, publicBoard: false };
        state.periods.unshift(newPeriod);
        state.currentPeriodId = newPeriod.id;
        addLog("创建期次", shortName, "新期次已创建，时间节点待配置");
      }
      commit(existing ? "期次设置已保存" : "新期次已创建");
      return true;
    });
  }

  function addCourse() {
    openModal("新增课程", `<div class="form-grid"><div class="field"><label>课程编号 *</label><input id="course-code" placeholder="例如 CS301"></div><div class="field"><label>课程名称 *</label><input id="course-name"></div><div class="field"><label>所属学院 *</label><select id="course-college"><option>信息管理与人工智能学院</option><option>全校</option><option>数据科学学院</option></select></div><div class="field"><label>课程分类 *</label><select id="course-category"><option>专业必修</option><option>学科必修</option><option>通识分层必修</option></select></div><div class="field full"><span class="error" id="course-error"></span></div></div>`, "新增课程", () => {
      const code = document.getElementById("course-code").value.trim();
      const name = document.getElementById("course-name").value.trim();
      if (!code || !name) { document.getElementById("course-error").textContent = "课程编号和名称不能为空"; return false; }
      if (state.courses.some((c) => c.code.toLowerCase() === code.toLowerCase())) { document.getElementById("course-error").textContent = "课程编号已存在"; return false; }
      state.courses.push({ id: `c${Date.now()}`, code, name, college: document.getElementById("course-college").value, category: document.getElementById("course-category").value, enabled: true });
      addLog("新增课程", code, name);
      commit("课程已新增");
      return true;
    });
  }

  function runMatching() {
    const navigators = state.applications.filter((a) => a.role === "navigator" && a.status === "已通过");
    const climbers = state.applications.filter((a) => a.role === "climber" && a.status === "已通过");
    const candidates = [];
    navigators.forEach((n) => climbers.forEach((c) => {
      const sharedSubjects = n.subjects.filter((s) => c.subjects.includes(s)).length;
      if (!sharedSubjects) return;
      const sharedStyles = n.styles.filter((s) => c.styles.includes(s)).length;
      const sharedPersonality = n.personality.filter((p) => c.personality.includes(p)).length;
      const subject = Math.min(40, 24 + sharedSubjects * 7);
      const style = Math.min(15, sharedStyles * 8);
      const personality = Math.min(10, sharedPersonality * 5);
      const major = n.major === c.major ? 25 : n.college === c.college ? 12 : 0;
      const gradeGap = Math.abs(Number.parseInt(n.grade, 10) - Number.parseInt(c.grade, 10));
      const grade = gradeGap === 0 ? 5 : gradeGap === 1 ? 3 : 0;
      const gap = Math.min(5, Math.max(0, Math.round((n.gpa - c.gpa) * 3)));
      candidates.push({ navigatorId: n.userId, climberId: c.userId, subject, style, personality, major, grade, gap, score: subject + style + personality + major + grade + gap });
    }));
    candidates.sort((a, b) => b.score - a.score);
    const usedN = new Set(), usedC = new Set(), selected = [];
    candidates.forEach((c) => {
      if (c.score < 60 || usedN.has(c.navigatorId) || usedC.has(c.climberId)) return;
      usedN.add(c.navigatorId); usedC.add(c.climberId);
      selected.push({ ...c, id: `mr${Date.now()}${selected.length}`, status: "待发布" });
    });
    state.matchingResults = selected;
    addLog("运行匹配", currentPeriod().shortName, `生成 ${selected.length} 组推荐`);
    commit(`匹配完成，生成 ${selected.length} 组推荐`);
  }

  function parseTranscriptCsv(text) {
    const lines = text.replace(/^\uFEFF/, "").split(/\r?\n/).filter((line) => line.trim());
    if (lines.length < 2) throw new Error("CSV 至少需要表头和一行数据");
    const parseLine = (line) => {
      const out = []; let value = ""; let quoted = false;
      for (let i = 0; i < line.length; i += 1) {
        const char = line[i];
        if (char === '"' && quoted && line[i + 1] === '"') { value += '"'; i += 1; }
        else if (char === '"') quoted = !quoted;
        else if (char === "," && !quoted) { out.push(value.trim()); value = ""; }
        else value += char;
      }
      out.push(value.trim()); return out;
    };
    const headers = parseLine(lines[0]);
    const required = ["studentNo","name","gpa","rank","cohort"];
    if (required.some((key) => !headers.includes(key))) throw new Error(`缺少必要字段：${required.filter((key) => !headers.includes(key)).join("、")}`);
    return lines.slice(1).map((line) => {
      const values = parseLine(line); const row = {};
      headers.forEach((key, index) => { row[key] = values[index] ?? ""; });
      ["gpa","rank","cohort"].forEach((key) => { row[key] = row[key] === "" ? "" : Number(row[key]); });
      const errors = [];
      if (!row.studentNo || !row.name) errors.push("学号或姓名为空");
      if (row.gpa === "" || Number.isNaN(row.gpa) || row.gpa < 0 || row.gpa > 5) errors.push("绩点超出 0—5");
      if (!row.rank || !row.cohort || row.rank > row.cohort) errors.push("排名或总人数无效");
      row._error = errors.join("；");
      return row;
    });
  }

  function handleGradeFile(file, semester) {
    if (!file) return;
    if (!file.name.toLowerCase().endsWith(".csv")) { toast("原型当前仅支持 CSV 文件", "error"); return; }
    const reader = new FileReader();
    reader.onload = () => {
      try { state.gradeImports[semester] = parseTranscriptCsv(String(reader.result)); commit(`已解析${semester === "previous" ? "上学期" : "本学期"}成绩单 ${state.gradeImports[semester].length} 行，请检查预览`); }
      catch (error) { toast(error.message, "error"); }
    };
    reader.onerror = () => toast("文件读取失败，请重试", "error");
    reader.readAsText(file, "UTF-8");
  }

  function materialById(id) {
    if (id.startsWith("rf-")) return { type: "reflection", role: id.replace("rf-", ""), item: state.reflections[id.replace("rf-", "")] };
    return { type: "photo", item: state.photos.find((p) => p.id === id) };
  }

  function navigate(page) {
    state.currentPage = page;
    saveState();
    history.replaceState(null, "", `#${state.currentRole}/${page}`);
    render();
    app.focus();
    document.getElementById("sidebar").classList.remove("open");
  }

  document.addEventListener("click", (event) => {
    const navTarget = event.target.closest("[data-nav]");
    if (navTarget) { navigate(navTarget.dataset.nav); return; }
    const button = event.target.closest("[data-action]");
    if (!button) return;
    const action = button.dataset.action;
    const id = button.dataset.id;

    if (action === "toggle-sidebar") document.getElementById("sidebar").classList.toggle("open");
    else if (action === "close-modal") closeModal();
    else if (action === "reset-demo") openModal("重置演示数据", `<div class="notice danger">这会清除你在当前浏览器中的所有原型操作并恢复初始示例数据。此操作不可撤销。</div>`, "确认重置", () => { localStorage.removeItem(STORAGE_KEY); state = seedState(); commit("演示数据已重置"); return true; });
    else if (action === "confirm-match") confirmMatch();
    else if (action === "reject-match") rejectMatch();
    else if (action === "reset-confirmation") openModal("重新体验确认流程", `<p>将删除当前原型中的正式帮扶对，并恢复为“领航者已确认、攀登者待确认”的状态。已上传的照片和感悟不会删除。</p>`, "恢复确认流程", () => { state.pair = null; state.candidate.status = "待双方确认"; state.candidate.confirmations = { navigator: true, climber: false }; commit("已恢复到匹配确认阶段"); return true; });
    else if (action === "open-photo-upload") openModal("上传过程照片", `<div class="notice">交互原型仅保存文件名、大小和说明，不上传图片二进制。正式系统需接入受权限保护的文件存储。</div><div class="field" style="margin-top:14px"><label>选择图片 *</label><input id="photo-file" type="file" accept="image/jpeg,image/png"><span class="hint">JPG/PNG，单张不超过 5 MB</span><span class="error" id="photo-error"></span></div><div class="field" style="margin-top:12px"><label>简短说明</label><input id="photo-note" maxlength="80" placeholder="例如：周三下午图书馆答疑"></div>`, "保存材料记录", () => { const file = document.getElementById("photo-file").files[0]; if (!file) { document.getElementById("photo-error").textContent = "请选择一张图片"; return false; } if (file.size > 5 * 1024 * 1024) { document.getElementById("photo-error").textContent = "图片大小不能超过 5 MB"; return false; } if (!file.type.match(/^image\/(jpeg|png)$/)) { document.getElementById("photo-error").textContent = "只支持 JPG 或 PNG"; return false; } state.photos.unshift({ id: `ph${Date.now()}`, name: file.name, size: file.size, note: document.getElementById("photo-note").value.trim(), uploader: roleMeta[state.currentRole].name, date: new Date().toLocaleString("zh-CN", { hour12: false }), peerConfirmed: false, adminStatus: "待对方确认" }); addNotification(state.currentRole === "navigator" ? "climber" : "navigator", "有新的过程照片待确认", `${roleMeta[state.currentRole].name}上传了 ${file.name}`); commit("照片材料记录已保存，等待对方确认"); return true; });
    else if (action === "confirm-photo") { const photo = state.photos.find((p) => p.id === id); if (photo) { photo.peerConfirmed = true; photo.adminStatus = "待审核"; addNotification("admin", "有新的过程照片待审核", `${photo.name} 已由双方确认。`); commit("照片已确认并进入管理员审核"); } }
    else if (action === "mark-read") { const item = state.notifications.find((n) => n.id === id); if (item) { item.read = true; commit("已标记为已读"); } }
    else if (action === "mark-all-read") { state.notifications.filter((n) => n.audience === state.currentRole || n.audience === "all").forEach((n) => { n.read = true; }); commit("当前角色的通知已全部标记为已读"); }
    else if (action === "restore-application") { state.ui.errors.application = {}; render(); toast("已恢复当前浏览器中最近保存的报名内容"); }
    else if (action === "edit-profile") { const item = currentApplication(); const profile = state.profiles[state.currentRole] || {}; openModal("编辑账号资料", `<div class="form-grid"><div class="field"><label>姓名</label><input value="${esc(item.name)}" disabled></div><div class="field"><label>学号</label><input value="${esc(item.studentNo)}" disabled></div><div class="field"><label>年级 *</label><select id="profile-grade">${["2021级","2022级","2023级","2024级","2025级","2026级"].map((grade) => `<option ${profile.grade === grade ? "selected" : ""}>${grade}</option>`).join("")}</select></div><div class="field"><label>联系电话 *</label><input id="profile-phone" value="${esc(profile.phone || "")}"></div><div class="field full"><label>微信号 *</label><input id="profile-wechat" value="${esc(profile.wechat || "")}" maxlength="40"><span class="error" id="profile-error"></span></div></div>`, "保存资料", () => { const phone = document.getElementById("profile-phone").value.trim(); const wechat = document.getElementById("profile-wechat").value.trim(); const grade = document.getElementById("profile-grade").value; if (!/^1\d{10}$/.test(phone)) { document.getElementById("profile-error").textContent = "请输入 11 位中国大陆手机号"; return false; } if (!wechat) { document.getElementById("profile-error").textContent = "请填写微信号"; return false; } state.profiles[state.currentRole] = { phone, wechat, grade }; item.grade = grade; commit("账号资料已保存到当前浏览器"); return true; }); }
    else if (action === "create-period") createOrEditPeriod();
    else if (action === "edit-period") createOrEditPeriod(id);
    else if (action === "switch-period-status") { const p = state.periods.find((item) => item.id === id); if (!p) return; const order = ["预告","报名中","匹配中","确认中","跟踪","验收","已结束"]; const next = p.status === "已结束" ? "验收" : order[Math.min(order.length - 1, order.indexOf(p.status) + 1)]; openModal(p.status === "已结束" ? "解除归档" : "切换期次阶段", `<div class="notice warning">将“${esc(p.shortName)}”从 ${esc(p.status)} 切换为 ${esc(next)}。系统会记录此次人工操作。</div><div class="field" style="margin-top:14px"><label>操作原因 *</label><input id="status-reason"><span class="error" id="status-error"></span></div>`, "确认切换", () => { const reason = document.getElementById("status-reason").value.trim(); if (!reason) { document.getElementById("status-error").textContent = "请填写操作原因"; return false; } const old = p.status; p.status = next; addLog("切换阶段", p.shortName, `${old} → ${next}；${reason}`); commit("期次阶段已切换"); return true; }); }
    else if (action === "basic-tab") { state.ui.basicTab = button.dataset.tab; commit(); }
    else if (action === "apply-basic-search") { state.ui.search = document.getElementById("basic-search").value.trim(); commit(); }
    else if (action === "add-course") addCourse();
    else if (action === "toggle-course") { const course = state.courses.find((c) => c.id === id); if (course) { course.enabled = !course.enabled; addLog(course.enabled ? "启用课程" : "停用课程", course.code, course.name); commit(`课程已${course.enabled ? "启用" : "停用"}`); } }
    else if (action === "download-course-template") downloadCsv("课程导入模板.csv", [{ code: "CS301", name: "课程名称", college: "信息管理与人工智能学院", category: "专业必修" }]);
    else if (action === "add-major") openModal("新增专业", `<div class="field"><label>所属学院 *</label><select id="major-college">${state.colleges.filter((c) => c.name !== "全校公共课程").map((c) => `<option value="${c.id}">${esc(c.name)}</option>`).join("")}</select></div><div class="field" style="margin-top:12px"><label>专业名称 *</label><input id="major-name"><span class="error" id="major-error"></span></div>`, "新增专业", () => { const name = document.getElementById("major-name").value.trim(); const college = state.colleges.find((c) => c.id === document.getElementById("major-college").value); if (!name) { document.getElementById("major-error").textContent = "请填写专业名称"; return false; } if (college.majors.includes(name)) { document.getElementById("major-error").textContent = "该专业已存在"; return false; } college.majors.push(name); addLog("新增专业", college.name, name); commit("专业已新增并保存到当前浏览器"); return true; });
    else if (action === "apply-application-filter") { state.ui.applicationFilter = document.getElementById("application-filter").value; commit(); }
    else if (action === "view-application") { const a = state.applications.find((x) => x.id === id); openModal("报名详情", `<dl><div class="profile-row"><dt>学生</dt><dd>${esc(a.name)} · ${esc(a.studentNo)}</dd></div><div class="profile-row"><dt>身份</dt><dd>${a.role === "navigator" ? "学业领航者" : "学业攀登者"}</dd></div><div class="profile-row"><dt>年级专业</dt><dd>${esc(a.grade)} · ${esc(a.major)}</dd></div><div class="profile-row"><dt>全部课程绩点</dt><dd>${a.gpa}</dd></div><div class="profile-row"><dt>手填科目</dt><dd>${tags(a.subjects)}</dd></div><div class="profile-row"><dt>自述</dt><dd>${esc(a.statement)}</dd></div></dl>`, "关闭", () => true); }
    else if (action === "approve-application") { const a = state.applications.find((x) => x.id === id); openModal("通过报名", `<p>确认通过 <strong>${esc(a.name)}</strong> 的${a.role === "navigator" ? "领航者" : "攀登者"}报名？通过后该记录将进入匹配池。</p>`, "审核通过", () => { a.status = "已通过"; a.rejectReason = ""; addLog("审核报名", a.studentNo, "审核通过"); addNotification(a.role, "报名审核通过", `你在 ${currentPeriod().shortName} 的报名已通过。`); commit("报名已审核通过"); return true; }); }
    else if (action === "reject-application") { const a = state.applications.find((x) => x.id === id); openModal("驳回报名", `<div class="field"><label>驳回原因 *</label><textarea id="application-reject-reason"></textarea><span class="error" id="application-reject-error"></span></div>`, "确认驳回", () => { const reason = document.getElementById("application-reject-reason").value.trim(); if (!reason) { document.getElementById("application-reject-error").textContent = "请填写驳回原因"; return false; } a.status = "已驳回"; a.rejectReason = reason; addLog("审核报名", a.studentNo, `驳回：${reason}`); addNotification(a.role, "报名审核被驳回", reason); commit("报名已驳回", "error"); return true; }); }
    else if (action === "export-applications") downloadCsv("报名名单.csv", state.applications.map(({ studentNo, name, role, grade, major, gpa, subjects, status: s }) => ({ studentNo, name, role, grade, major, allCourseGpa: gpa, subjects: subjects.join("；"), status: s })));
    else if (action === "run-matching") openModal("重新运行匹配", `<div class="notice warning">将根据当前审核通过人员重新计算待发布方案。系统使用确定性的本地评分规则，不调用 AI 或远程服务。</div>`, "运行匹配", () => { runMatching(); return true; });
    else if (action === "publish-matching") { const waiting = state.matchingResults.filter((r) => r.status === "待发布"); if (!waiting.length) { toast("当前没有待发布的匹配结果", "error"); return; } openModal("发布匹配结果", `<p>将向 ${waiting.length} 组候选配对发布结果，并生成双方的站内通知。</p>`, "确认发布", () => { waiting.forEach((r) => { r.status = "已发布"; }); addNotification("all", "匹配结果已发布", "请进入匹配中心查看匿名信息并在截止前确认。" ); addLog("发布匹配", currentPeriod().shortName, `${waiting.length} 组候选配对`); commit(`已发布 ${waiting.length} 组匹配结果`); return true; }); }
    else if (action === "view-score") { const r = state.matchingResults.find((x) => x.id === id); openModal("匹配得分详情", `<div class="score-overview"><div class="score-circle"><strong>${r.score}<span>综合匹配分</span></strong></div><div>${progress("科目匹配", r.subject / 40 * 100, `${r.subject}/40`)}${progress("专业匹配", r.major / 25 * 100, `${r.major}/25`)}${progress("学习风格", r.style / 15 * 100, `${r.style}/15`)}</div></div><div class="notice" style="margin-top:16px">其他得分：性格 ${r.personality}/10、年级 ${r.grade}/5、绩点差距 ${r.gap}/5。匹配不再使用时间维度。</div>`, "关闭", () => true); }
    else if (action === "export-pairs") { if (!state.pair) toast("暂无正式帮扶对可导出", "error"); else downloadCsv("正式帮扶对.csv", [{ pairId: state.pair.id, navigator: "林晓", climber: "陈晨", subjects: "高等数学；数据结构", status: state.pair.status }]); }
    else if (action === "view-pair") openModal("帮扶对详情", `<dl><div class="profile-row"><dt>编号</dt><dd>${esc(state.pair?.id || "—")}</dd></div><div class="profile-row"><dt>双方</dt><dd>林晓（领航者） × 陈晨（攀登者）</dd></div><div class="profile-row"><dt>有效照片</dt><dd>${state.photos.filter((p) => p.peerConfirmed && p.adminStatus !== "驳回").length} 张 · 建议 ${currentPeriod().photoMin}—${currentPeriod().photoMax} 张</dd></div><div class="profile-row"><dt>感悟</dt><dd>${Object.values(state.reflections).filter((r) => r.status === "审核通过").length}/2 已通过</dd></div></dl>`, "关闭", () => true);
    else if (action === "dissolve-pair") openModal("解除帮扶对", `<div class="notice danger">解除后双方回到待匹配状态，历史材料会保留。此操作需要填写原因。</div><div class="field" style="margin-top:14px"><label>解除原因 *</label><textarea id="dissolve-reason"></textarea><span class="error" id="dissolve-error"></span></div>`, "确认解除", () => { const reason = document.getElementById("dissolve-reason").value.trim(); if (!reason) { document.getElementById("dissolve-error").textContent = "请填写解除原因"; return false; } addLog("解除帮扶对", state.pair.id, reason); state.pair = null; state.candidate.status = "待双方确认"; state.candidate.confirmations = { navigator: false, climber: false }; commit("帮扶对已解除，历史材料仍保留", "error"); return true; });
    else if (action === "apply-material-filter") { state.ui.materialFilter = document.getElementById("material-filter").value; commit(); }
    else if (action === "view-material") { const m = materialById(id); const content = m.type === "photo" ? `<p><strong>${esc(m.item.name)}</strong></p><p>${esc(m.item.note || "无说明")}</p>` : `<p>${esc(m.item.text || "尚未填写正文")}</p>`; openModal("材料详情", content, "关闭", () => true); }
    else if (action === "approve-material") { const m = materialById(id); if (m.type === "photo") m.item.adminStatus = "通过"; else m.item.status = "审核通过"; addLog("审核材料", id, "审核通过"); commit("材料已审核通过"); }
    else if (action === "reject-material") { const m = materialById(id); openModal("驳回材料", `<div class="field"><label>驳回原因 *</label><textarea id="material-reject-reason"></textarea><span class="error" id="material-reject-error"></span></div>`, "确认驳回", () => { const reason = document.getElementById("material-reject-reason").value.trim(); if (!reason) { document.getElementById("material-reject-error").textContent = "请填写驳回原因"; return false; } if (m.type === "photo") m.item.adminStatus = "驳回"; else { m.item.status = "审核驳回"; m.item.rejectReason = reason; } addLog("审核材料", id, `驳回：${reason}`); commit("材料已驳回", "error"); return true; }); }
    else if (action === "download-grade-template") downloadCsv("单学期成绩单导入模板.csv", [{ studentNo: "23020116", name: "陈晨", gpa: 2.61, rank: 62, cohort: 96 }]);
    else if (action === "clear-grade-preview") { state.gradeImports = { previous: [], current: [] }; commit("已取消两张成绩单的导入预览"); }
    else if (action === "commit-grade-import") { const studentNos = Array.from(new Set([...state.gradeImports.previous, ...state.gradeImports.current].map((row) => row.studentNo))); studentNos.forEach((studentNo) => { const previous = state.gradeImports.previous.find((row) => row.studentNo === studentNo); const current = state.gradeImports.current.find((row) => row.studentNo === studentNo); if (!previous || !current) return; const application = state.applications.find((item) => item.studentNo === studentNo); const existing = state.grades.find((g) => g.studentNo === studentNo); const merged = { studentNo, name: current.name || previous.name, role: application?.role === "navigator" ? "领航者" : "攀登者", baseGpa: previous.gpa, finalGpa: current.gpa, baseRank: previous.rank, baseCohort: previous.cohort, finalRank: current.rank, finalCohort: current.cohort }; if (existing) Object.assign(existing, merged); else state.grades.push(merged); }); addLog("导入成绩", currentPeriod().shortName, `提取 ${studentNos.length} 人的两学期绩点与排名`); state.gradeImports = { previous: [], current: [] }; commit("两张成绩单已合并，绩点与排名已提取"); }
    else if (action === "export-grades") downloadCsv("两学期期末成绩明细.csv", state.grades.map(({ studentNo, name, role, baseGpa, finalGpa, baseRank, baseCohort, finalRank, finalCohort }) => ({ studentNo, name, role, previousSemesterGpa: baseGpa, currentSemesterGpa: finalGpa, previousRank: baseRank, previousCohort: baseCohort, currentRank: finalRank, currentCohort: finalCohort })));
    else if (action === "export-logs") downloadCsv("操作日志.csv", state.logs);
    else if (action === "download-reward-file") { const file = state.rewardFiles.find((item) => item.id === id); downloadStoredFile(file); }
    else if (action === "toggle-reward-file") { const file = state.rewardFiles.find((item) => item.id === id); if (file) { file.status = file.status === "已发布" ? "已撤回" : "已发布"; addLog(file.status === "已发布" ? "发布德育分名单" : "撤回德育分名单", file.fileName, file.title); commit(`文件已${file.status === "已发布" ? "发布" : "撤回"}`); } }
    else if (action === "delete-reward-file") { const file = state.rewardFiles.find((item) => item.id === id); if (!file) return; openModal("删除德育分文件", `<div class="notice danger">将删除“${esc(file.title)}”。删除后学生无法继续下载，操作不可恢复。</div>`, "确认删除", () => { state.rewardFiles = state.rewardFiles.filter((item) => item.id !== id); addLog("删除德育分名单", file.fileName, file.title); commit("德育分文件已删除", "error"); return true; }); }
    else if (action === "view-history") { const record = state.historyRecords.find((item) => item.id === id); if (!record) return; openModal("往期帮扶详情", `<div class="notice">历史期次内容只读，仅本人可查看。</div><dl><div class="profile-row"><dt>活动期次</dt><dd>${esc(record.period)}</dd></div><div class="profile-row"><dt>当期身份</dt><dd>${esc(record.role)}</dd></div><div class="profile-row"><dt>帮扶对象</dt><dd>${esc(record.partner)}</dd></div><div class="profile-row"><dt>帮扶科目</dt><dd>${tags(record.subjects)}</dd></div><div class="profile-row"><dt>过程照片</dt><dd>${record.photos} 张</dd></div><div class="profile-row"><dt>绩点变化</dt><dd>${record.gpaBefore} → ${record.gpaAfter}</dd></div><div class="profile-row"><dt>状态</dt><dd>${status(record.result)}</dd></div></dl>`, "关闭", () => true); }
  });

  document.addEventListener("submit", (event) => {
    if (event.target === modalForm) {
      event.preventDefault();
      if (!modalSubmitHandler) { closeModal(); return; }
      const shouldClose = modalSubmitHandler(new FormData(modalForm));
      if (shouldClose !== false) closeModal();
      return;
    }
    event.preventDefault();
    const form = event.target;
    const data = new FormData(form);
    const intent = event.submitter?.value || "submit";

    if (form.id === "application-form") {
      const item = currentApplication();
      const subjects = String(data.get("subjectsText") || "").split(/[、,，;；\n]/).map((value) => value.trim()).filter(Boolean);
      const next = { gpa: Number(data.get("gpa")), subjects: Array.from(new Set(subjects)), statement: String(data.get("statement") || "").trim() };
      const errors = {};
      if (Number.isNaN(next.gpa) || next.gpa < 0 || next.gpa > 5) errors.gpa = "绩点必须在 0—5 之间";
      if (!next.subjects.length) errors.subjects = "请至少手动填写一门课程";
      if (next.statement.length < 20) errors.statement = "请至少填写 20 字，正式版本要求 100—300 字";
      state.ui.errors.application = errors;
      if (Object.keys(errors).length) { Object.assign(item, next); commit("请修正表单中的问题", "error"); return; }
      Object.assign(item, next, { status: intent === "draft" ? "草稿" : "审核中", submittedAt: new Date().toLocaleString("zh-CN", { hour12: false }) });
      state.ui.errors.application = {};
      addLog(intent === "draft" ? "保存报名草稿" : "提交报名", item.studentNo, roleMeta[state.currentRole].label);
      commit(intent === "draft" ? "报名草稿已保存到当前浏览器" : "报名已提交审核");
    } else if (form.id === "reflection-form") {
      const text = String(data.get("text") || "").trim();
      const errors = {};
      if (intent === "submit" && (text.length < 300 || text.length > 500)) errors.text = "正式提交要求 300—500 字；可以先保存草稿";
      state.ui.errors.reflection = errors;
      state.reflections[state.currentRole].text = text;
      if (Object.keys(errors).length) { commit("感悟字数不符合提交要求，内容已保留", "error"); return; }
      state.reflections[state.currentRole].status = intent === "draft" ? "草稿" : "待审核";
      state.ui.errors.reflection = {};
      if (intent === "submit") addNotification("admin", "有新的活动感悟待审核", `${roleMeta[state.currentRole].name}已提交活动感悟。`);
      commit(intent === "draft" ? "感悟草稿已保存" : "活动感悟已提交审核");
    } else if (form.id === "reward-file-form") {
      const title = String(data.get("title") || "").trim();
      const file = data.get("file");
      const errorNode = document.getElementById("reward-file-error");
      if (!title || !(file instanceof File) || !file.name) { errorNode.textContent = "请填写文件标题并选择名单文件"; return; }
      if (file.size > 2 * 1024 * 1024) { errorNode.textContent = "原型仅支持不超过 2 MB 的文件"; return; }
      const allowed = /\.(csv|xls|xlsx|pdf)$/i;
      if (!allowed.test(file.name)) { errorNode.textContent = "仅支持 CSV、XLS、XLSX 或 PDF"; return; }
      const reader = new FileReader();
      reader.onload = () => { state.rewardFiles.unshift({ id: `reward-file-${Date.now()}`, title, fileName: file.name, mimeType: file.type || "application/octet-stream", dataUrl: String(reader.result), uploadedAt: new Date().toLocaleString("zh-CN", { hour12: false }), status: "已发布" }); addLog("上传并发布德育分名单", file.name, title); addNotification("all", "德育分名单已发布", `管理员已发布“${title}”，可在成绩与德育分页面下载。`); commit("德育分名单已上传并向学生发布"); };
      reader.onerror = () => { errorNode.textContent = "文件读取失败，请重新选择"; };
      reader.readAsDataURL(file);
    } else if (form.id === "notice-form") {
      const title = String(data.get("title") || "").trim();
      const content = String(data.get("content") || "").trim();
      if (!title || !content) { toast("通知标题和内容不能为空", "error"); return; }
      const audience = String(data.get("audience"));
      addNotification(audience, title, content);
      addLog("发送通知", audience === "all" ? "全体参与者" : roleMeta[audience].label, title);
      commit("站内通知已发送；未调用短信或微信服务");
    }
  });

  document.addEventListener("input", (event) => {
    if (event.target.matches("textarea")) {
      const counter = document.querySelector(`[data-word-count="${event.target.id}"]`);
      if (counter) counter.textContent = event.target.value.length;
    }
  });

  document.addEventListener("change", (event) => {
    if (event.target.id === "transcript-previous") handleGradeFile(event.target.files[0], "previous");
    if (event.target.id === "transcript-current") handleGradeFile(event.target.files[0], "current");
  });

  roleSwitcher.addEventListener("change", () => {
    state.currentRole = roleSwitcher.value;
    state.currentPage = "dashboard";
    saveState();
    history.replaceState(null, "", `#${state.currentRole}/dashboard`);
    render();
  });

  periodSwitcher.addEventListener("change", () => {
    state.currentPeriodId = periodSwitcher.value;
    state.currentPage = "dashboard";
    commit("已切换查看期次");
  });

  const initialHash = location.hash.replace(/^#/, "").split("/");
  if (roleMeta[initialHash[0]]) {
    state.currentRole = initialHash[0];
    const allowed = (state.currentRole === "admin" ? adminNav : studentNav).map((item) => item[2]);
    if (allowed.includes(initialHash[1])) state.currentPage = initialHash[1];
  }
  render();
})();
