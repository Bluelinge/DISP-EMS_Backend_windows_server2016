<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import * as XLSX from "xlsx";
import { useMesStore } from "../store";
import { api, apiMode, ApiError, endSession } from "../api/request";
import { normalizeVideoName, resolveVideoBinding } from "../utils/videoBinding";
import { isRangeValueValid, normalizeRangeReference, rangeReferenceError } from "../utils/parameterRange";
import ProjectionCanvas from "../components/ProjectionCanvas.vue";
import ProjectorEditor from "../components/ProjectorEditor.vue";
import UserProfileDialog from "../components/UserProfileDialog.vue";
import ProductionInsightsCharts from "../components/ProductionInsightsCharts.vue";
const route = useRoute(),
  router = useRouter(),
  store = useMesStore();
const menus = [
  {
    group: "工艺与资源",
    items: [
      ["/admin/processes", "工艺文件", "Files"],
      ["/admin/videos", "视频资源", "VideoCamera"],
      ["/admin/icons", "投影素材", "Picture"],
      ["/admin/projector/templates", "投影模板", "Monitor"],
    ],
  },
  {
    group: "生产与记录",
    items: [
      ["/admin/tasks", "任务记录", "Tickets"],
      ["/admin/stats", "生产统计", "DataAnalysis"],
    ],
  },
  {
    group: "系统配置",
    items: [
      ["/admin/workstations", "工位管理", "Location"],
      ["/admin/users", "人员管理", "User"],
    ],
  },
];
const section = computed(() => {
  const p = route.path;
  if (p.includes("projector-preview")) return "preview";
  if (/^\/admin\/processes\/[^/]+$/.test(p)) return "detail";
  if (p.startsWith("/admin/processes")) return "processes";
  if (/^\/admin\/projector\/templates\/[^/]+$/.test(p)) return "templateDetail";
  if (p.startsWith("/admin/projector/templates")) return "templates";
  if (p.includes("/audit")) return "audit";
  return p.split("/").pop();
});
const title = computed(
  () =>
    ({
      processes: "工艺文件",
      detail: "工艺步骤编辑",
      preview: "步骤投影预览",
      videos: "视频资源",
      icons: "投影素材",
      templates: "投影模板",
      templateDetail: "模板编辑",
      tasks: "任务记录",
      audit: "工位操作明细",
      workstations: "工位管理",
      users: "人员管理",
      stats: "生产统计",
    })[section.value] || "管理端",
);
const permitted = (item) => store.state.role === "admin";
const isSuperAdmin = computed(() => Number(store.state.currentUserRoleCode) === 2);
function menuDisabled(path) {
  return path === "/admin/users" && !isSuperAdmin.value;
}
function clickMenu(path) {
  router.push(path);
}
async function logout() {
  await endSession();
  store.logout();
  router.replace("/login");
}
const selectedStepNo = ref(1),
  selectedTemplateId = ref(""),
  stepForm = ref({}),
  search = ref(""),
  category = ref("ALL"),
  collapsedVideoCategories = ref({});
const selectedStep = computed(
  () =>
    store.steps.find((s) => s.stepNo === selectedStepNo.value) ||
    store.steps[0],
);
const filteredSteps = computed(() =>
  store.steps.filter((s) => `${s.stepNo}${s.title}`.includes(search.value)),
);
const activeVideos = computed(() =>
  store.state.videos.filter((v) => !store.state.deleted.videos.includes(v.id)),
);
const videoCategoryMap = computed(
  () => new Map(store.state.videoCategories.map((item) => [item.id, item])),
);
const videoCategoryTree = computed(() =>
  store.state.videoCategories
    .filter((item) => !item.parentId)
    .sort((a, b) => a.sortOrder - b.sortOrder)
    .map((parent) => ({
      ...parent,
      children: store.state.videoCategories
        .filter((item) => item.parentId === parent.id)
        .sort((a, b) => a.sortOrder - b.sortOrder),
    })),
);
const videoCategoryOptions = computed(() =>
  videoCategoryTree.value.map((parent) => ({
    value: parent.id,
    label: parent.name,
    children: parent.children.map((child) => ({
      value: child.id,
      label: child.name,
    })),
  })),
);
const selectedCategoryIds = computed(() => {
  if (category.value === "ALL") return null;
  const selected = videoCategoryMap.value.get(category.value);
  return selected?.parentId
    ? new Set([selected.id])
    : new Set([
        selected?.id,
        ...(
          videoCategoryTree.value.find((item) => item.id === selected?.id)
            ?.children || []
        ).map((item) => item.id),
      ]);
});
const filteredVideos = computed(() =>
  activeVideos.value.filter(
    (video) =>
      (!selectedCategoryIds.value ||
        selectedCategoryIds.value.has(video.categoryId)) &&
      video.name.includes(search.value),
  ),
);
const videoPage = ref(1),
  videoPageSize = ref(10);
const pagedVideos = computed(() => {
  const start = (videoPage.value - 1) * videoPageSize.value;
  return filteredVideos.value.slice(start, start + videoPageSize.value);
});
watch(
  [filteredVideos, videoPageSize],
  () => {
    const lastPage = Math.max(
      1,
      Math.ceil(filteredVideos.value.length / videoPageSize.value),
    );
    if (videoPage.value > lastPage) videoPage.value = lastPage;
  },
  { immediate: true },
);
watch([category, search], () => {
  videoPage.value = 1;
});
const stepVideoSecondaries = computed(() => {
  const parent = videoCategoryTree.value.find(
    (item) => item.id === stepForm.value.videoParentId,
  );
  if (!parent) return [];
  return parent.children.length
    ? parent.children
    : [{ ...parent, name: "未细分（直接归类）" }];
});
const stepVideoOptions = computed(() =>
  activeVideos.value.filter(
    (video) => video.categoryId === stepForm.value.videoCategoryId,
  ),
);
function videoCategoryPath(id) {
  const categoryItem = videoCategoryMap.value.get(id);
  if (!categoryItem) return { primary: "未分类", secondary: "—" };
  const parent = categoryItem.parentId
    ? videoCategoryMap.value.get(categoryItem.parentId)
    : categoryItem;
  return {
    primary: parent?.name || "未分类",
    secondary: categoryItem.parentId ? categoryItem.name : "—",
  };
}
function toggleVideoCategory(id) {
  collapsedVideoCategories.value = {
    ...collapsedVideoCategories.value,
    [id]: !collapsedVideoCategories.value[id],
  };
}
const activePeople = computed(() =>
  store.state.people.filter((p) => !store.state.deleted.people.includes(p.id)),
);
const roleCodes = { 操作员: 0, 管理员: 1, 超级管理员: 2 };
const roleNames = ["操作员", "管理员", "超级管理员"];
function userFromApi(item) {
  return {
    id: String(item.id), no: item.no, name: item.name,
    role: roleNames[Number(item.roleCode)] || "操作员",
    dept: item.department || "", phone: item.phone || "",
    status: Number(item.statusCode) === 1 ? "在岗" : "停用",
    rowVersion: Number(item.rowVersion),
  };
}
function templateFromApi(item) {
  return {
    ...item,
    id: String(item.id),
    isDefault: Number(item.isDefault) === 1 || item.isDefault === true,
    elements: Array.isArray(item.elements) ? item.elements : [],
    rowVersion: Number(item.rowVersion),
  };
}
function workstationFromApi(item) {
  return {
    ...item,
    id: Number(item.id),
    stationNo: Number(item.stationNo),
    captureMode: Number(item.captureMode) === 0 ? "manual" : "auto",
    rowVersion: Number(item.rowVersion),
  };
}
const activeTemplates = computed(() =>
  store.state.templates.filter(
    (t) => !store.state.deleted.templates.includes(t.id),
  ),
);
const processRows = computed(() =>
  store.state.processFiles
    .filter((file) => !store.state.deleted.processes.includes(file.id))
    .map((file) => ({ ...file, count: (file.steps || []).length })),
);
const allIcons = computed(() => {
  const icons = new Map();
  store.iconNames.forEach((name) =>
    icons.set(name, {
      id: null,
      rowVersion: null,
      name,
      dataUrl: store.iconUrl(name),
      source: "随附 ICON 库",
      builtIn: true,
    }),
  );
  store.state.customIcons.forEach((item) =>
    icons.set(item.name, {
      ...item,
      name: item.name,
      dataUrl: item.dataUrl,
      source: "本地上传",
      builtIn: false,
    }),
  );
  return [...icons.values()].filter(
    (item) => apiMode || !store.state.deleted.icons.includes(item.name),
  );
});
const currentTemplate = computed(() =>
  store.state.templates.find((t) => t.id === route.params.id),
);
const previewStep = computed(
  () =>
    store.steps.find((s) => s.stepNo === Number(route.query.step || 1)) ||
    store.steps[0],
);
const taskTableRows = ref([]),
  taskTotal = ref(0),
  taskPage = ref(1),
  taskPageSize = ref(20),
  taskStartedRange = ref([]),
  taskModel = ref(""),
  taskModelOptions = ref([]),
  taskLoading = ref(false),
  adminDataReady = ref(false),
  auditTaskDetail = ref(null);
const taskRows = computed(() => {
  const rows = [
      ...(store.state.taskHistory || []),
      ...Object.values(store.state.inProgressTasks || []),
    ],
    current = store.state.task;
  if (current && !rows.some((item) => item.id === current.id))
    rows.push(current);
  return rows.sort(
    (a, b) =>
      new Date(b.endedAt || b.startedAt || 0) -
      new Date(a.endedAt || a.startedAt || 0),
  );
});
const taskFilterModelOptions = computed(() => {
  if (apiMode) return taskModelOptions.value;
  return [...new Set(taskRows.value.map((task) => String(task.model || "").trim()).filter(Boolean))].sort((a, b) => a.localeCompare(b, "zh-CN"));
});
const mockFilteredTaskRows = computed(() => {
  const [from, to] = taskStartedRange.value || [];
  return taskRows.value.filter((task) => {
    const modelMatched = !taskModel.value || String(task.model || "") === taskModel.value;
    const startedDate = task.startedAt ? localDate(task.startedAt) : "";
    const dateMatched = (!from || startedDate >= from) && (!to || startedDate <= to);
    return modelMatched && dateMatched;
  });
});
const displayedTaskRows = computed(() => {
  if (apiMode) return taskTableRows.value;
  const start = (taskPage.value - 1) * taskPageSize.value;
  return mockFilteredTaskRows.value.slice(start, start + taskPageSize.value);
});
function setServerTaskPage(page) {
  const items = Array.isArray(page?.items) ? page.items : [];
  taskTableRows.value = items;
  taskTotal.value = Number(page?.total) || 0;
  store.state.taskHistory = items.filter((item) => item.status === "已完成");
  store.state.inProgressTasks = Object.fromEntries(items.filter((item) => item.status === "进行中").map((item) => [String(item.id), item]));
}
async function loadTaskPage(resetPage = false) {
  if (resetPage) taskPage.value = 1;
  taskLoading.value = true;
  try {
    if (apiMode) {
      const page = await api.tasks({
        page: taskPage.value,
        pageSize: taskPageSize.value,
        startedFrom: taskStartedRange.value?.[0],
        startedTo: taskStartedRange.value?.[1],
        model: taskModel.value,
      });
      setServerTaskPage(page);
    } else {
      taskModelOptions.value = taskFilterModelOptions.value;
      taskTotal.value = mockFilteredTaskRows.value.length;
    }
  } catch (error) {
    ElMessage.error(error?.message || "任务记录加载失败，请稍后重试");
  } finally {
    taskLoading.value = false;
  }
}
function applyTaskFilters() {
  loadTaskPage(true);
}
function resetTaskFilters() {
  taskStartedRange.value = [];
  taskModel.value = "";
  loadTaskPage(true);
}
function changeTaskPage(page) {
  taskPage.value = page;
  loadTaskPage();
}
function changeTaskPageSize(size) {
  taskPageSize.value = size;
  taskPage.value = 1;
  loadTaskPage();
}
function formatTaskDateTime(value) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "—";
  return date.toLocaleString("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  }).replace(/\//g, "-");
}
function openTaskAudit(id) {
  auditTaskDetail.value = null;
  router.push(`/admin/tasks/${id}/audit`);
}
async function loadAuditTask(id) {
  if (!id) return;
  auditTaskDetail.value = null;
  try {
    auditTaskDetail.value = apiMode
      ? await api.task(id)
      : taskRows.value.find((task) => String(task.id) === String(id)) || null;
  } catch (error) {
    ElMessage.error(error?.message || "任务明细加载失败，请稍后重试");
  }
}
const auditTask = computed(
  () => auditTaskDetail.value || taskRows.value.find((task) => String(task.id) === String(route.params.id)) || store.state.task,
);
function stationName(id) {
  return (
    store.state.workstations.find((item) => item.id === Number(id))?.name ||
    `工位${id || 1}`
  );
}
function stationNo(id) {
  return (
    store.state.workstations.find((item) => item.id === Number(id))?.stationNo ||
    Number(id) ||
    1
  );
}
const taskStepDetails = computed(() => {
  const task = auditTask.value,
    process = store.state.processFiles.find(
      (file) => file.id === task?.processId,
    ),
    steps = task?.processSnapshot?.length
      ? task.processSnapshot
      : process?.steps || [];
  return [...steps]
    .sort((a, b) => a.stepNo - b.stepNo)
    .map((step) => {
      const log = task?.logs?.[step.stepNo],
        stationId = Number(step.stationId || 1),
        result =
          log?.actionCode === 0
            ? "已完成"
            : log?.actionCode === 1
              ? "普通工序跳过"
              : log?.actionCode === 2
                ? "重要工序跳过"
                : "未操作";
      const parameterLines = (step.parameters || []).map((parameter, index) => {
        const key = String(parameter.index ?? index + 1);
        const value = log?.values?.[key];
        return `${parameter.name || "参数" + (index + 1)}：${value === undefined || value === null || String(value).trim() === "" ? "—" : value}`;
      });
      return {
        stepNo: step.stepNo,
        title: step.title,
        stationName:
          step.stationName ||
          log?.stationName ||
          task?.stationConfirmations?.[stationId]?.stationName ||
          stationName(stationId),
        operatorName: log?.operatorName || "—",
        result,
        time: log?.time || "—",
        parameters: parameterLines.length ? parameterLines : ["—"],
        reason: log?.reason || "—",
      };
    });
});
const steelNoAuditDetails = computed(() =>
  (auditTask.value?.steelNoAudit || []).map((item, index) => ({
    id: `steel-no-${item.changedAt || index}-${index}`,
    stepNo: "—",
    title: "钢印编号修改",
    stationName: item.stationName || stationName(item.stationId),
    result: "已修改",
    operatorName: item.operatorName || "—",
    time: item.changedAt || "—",
    parameters: ["—"],
    reason: `修改前：${item.previousSteelNo || "—"}；修改后：${item.steelNo || "—"}`,
  })),
);
const taskOperationDetails = computed(() => [
  ...taskStepDetails.value,
  ...steelNoAuditDetails.value,
]);
const doneCount = computed(() => store.state.taskHistory?.length || 0),
  activeCount = computed(
    () => Object.keys(store.state.inProgressTasks || {}).length,
  );
const localDate = (date) => {
  const value = new Date(date);
  return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, "0")}-${String(value.getDate()).padStart(2, "0")}`;
};
const calendarRange = (preset) => {
  const end = new Date(),
    start = new Date(end);
  start.setDate(start.getDate() - (preset === "7d" ? 6 : 29));
  return [localDate(start), localDate(end)];
};
const previousDateRange = (range) => {
  if (!range?.[0] || !range?.[1]) return [];
  const start = new Date(`${range[0]}T12:00:00`),
    end = new Date(`${range[1]}T12:00:00`),
    days = Math.round((end - start) / 86400000) + 1,
    previousEnd = new Date(start),
    previousStart = new Date(start);
  previousEnd.setDate(previousEnd.getDate() - 1);
  previousStart.setDate(previousStart.getDate() - days);
  return [localDate(previousStart), localDate(previousEnd)];
};
const demoStatsTasks = (() => {
  const operators = ["李工", "赵工", "陈工", "刘工"],
    durationBase = { 李工: 1680, 赵工: 1840, 陈工: 1560, 刘工: 2020 },
    durationJitter = [0, 60, -40, 90],
    models = store.productModels.length ? store.productModels : ["演示型号"],
    now = new Date();
  const dateForDay = (dayAgo, hour) => {
    const value = new Date(now);
    value.setDate(value.getDate() - dayAgo);
    value.setHours(hour, 20, 0, 0);
    return value;
  };
  const demoSteelNo = (dayAgo, index) => {
    if (index !== 0) return `GY-DEMO-${dayAgo}-${index}`;
    if ([1, 5].includes(dayAgo)) return "GY-DEMO-CURRENT-REWORK";
    if ([7, 10, 13].includes(dayAgo)) return "GY-DEMO-PREV7-REWORK";
    if ([31, 35].includes(dayAgo)) return "GY-DEMO-PREV30-REWORK-01";
    if ([40, 45].includes(dayAgo)) return "GY-DEMO-PREV30-REWORK-02";
    if ([50, 56].includes(dayAgo)) return "GY-DEMO-PREV30-REWORK-03";
    return `GY-DEMO-${dayAgo}-${index}`;
  };
  const completed = Array.from({ length: 60 }, (_, dayAgo) => {
    const taskCount = dayAgo < 7 ? 3 + (dayAgo % 2) : dayAgo < 30 ? 2 + (dayAgo % 2) : 1 + (dayAgo % 2);
    return Array.from({ length: taskCount }, (_, index) => {
      const operator = operators[(dayAgo + index) % operators.length],
        phaseAdjustment = dayAgo < 7 ? -120 : dayAgo < 30 ? 0 : 180,
        durationSec = durationBase[operator] + phaseAdjustment + durationJitter[(dayAgo + index) % durationJitter.length],
        endedAt = dateForDay(dayAgo, 9 + index * 2);
      return {
        id: `STATS-DEMO-DONE-${dayAgo}-${index}`,
        status: "已完成",
        operatorName: operator,
        model: models[(dayAgo + index) % models.length],
        steelNo: demoSteelNo(dayAgo, index),
        startedAt: new Date(endedAt.getTime() - durationSec * 1000).toISOString(),
        endedAt: endedAt.toISOString(),
        durationSec,
        isDemo: true,
      };
    });
  }).flat();
  const inProgress = [0, 1, 4, 8, 11, 17, 24, 33, 39, 46, 54].map(
    (dayAgo, index) => ({
      id: `STATS-DEMO-RUN-${dayAgo}`,
      status: "进行中",
      operatorName: operators[index % operators.length],
      model: models[index % models.length],
      steelNo: `GY-DEMO-RUN-${dayAgo}`,
      startedAt: dateForDay(dayAgo, 8 + (index % 3)).toISOString(),
      isDemo: true,
    }),
  );
  return [...completed, ...inProgress];
})();
const statsDateRange = ref(calendarRange("7d")),
  statsPreset = ref("7d");
const productionStatistics = ref(null),
  productionStatisticsLoading = ref(false);
const PRODUCTION_STATISTICS_REFRESH_MS = 10_000;
let productionStatisticsRequest = null,
  productionStatisticsRefreshQueued = false,
  productionStatisticsQueuedBackground = true,
  productionStatisticsRefreshTimer = null;
async function loadProductionStatistics({ background = false } = {}) {
  if (!apiMode || !statsDateRange.value?.[0] || !statsDateRange.value?.[1]) return;
  if (productionStatisticsRequest) {
    if (!background) {
      productionStatisticsRefreshQueued = true;
      productionStatisticsQueuedBackground = false;
    }
    return productionStatisticsRequest;
  }
  const runRequest = async () => {
    let requestBackground = background;
    do {
      productionStatisticsRefreshQueued = false;
      productionStatisticsQueuedBackground = true;
      const from = statsDateRange.value[0],
        to = statsDateRange.value[1];
      if (!requestBackground && !productionStatistics.value) {
        productionStatisticsLoading.value = true;
      }
      try {
        const result = await api.productionStatistics({ from, to });
        if (from === statsDateRange.value[0] && to === statsDateRange.value[1]) {
          productionStatistics.value = result;
        } else {
          productionStatisticsRefreshQueued = true;
          productionStatisticsQueuedBackground = false;
        }
      } catch (error) {
        if (!requestBackground) {
          ElMessage.error(error?.message || "生产统计数据加载失败");
        }
      } finally {
        productionStatisticsLoading.value = false;
      }
      requestBackground = productionStatisticsQueuedBackground;
    } while (productionStatisticsRefreshQueued && section.value === "stats" && adminDataReady.value);
  };
  const request = runRequest();
  productionStatisticsRequest = request;
  try {
    await request;
  } finally {
    if (productionStatisticsRequest === request) productionStatisticsRequest = null;
  }
}
function stopProductionStatisticsPolling() {
  if (productionStatisticsRefreshTimer !== null) {
    window.clearTimeout(productionStatisticsRefreshTimer);
    productionStatisticsRefreshTimer = null;
  }
}
function scheduleProductionStatisticsPolling() {
  stopProductionStatisticsPolling();
  if (!apiMode || !adminDataReady.value || section.value !== "stats") return;
  productionStatisticsRefreshTimer = window.setTimeout(async () => {
    productionStatisticsRefreshTimer = null;
    await loadProductionStatistics({ background: true });
    scheduleProductionStatisticsPolling();
  }, PRODUCTION_STATISTICS_REFRESH_MS);
}
function refreshProductionStatistics() {
  stopProductionStatisticsPolling();
  if (!apiMode || !adminDataReady.value || section.value !== "stats") return;
  void loadProductionStatistics().finally(scheduleProductionStatisticsPolling);
}
function setStatsRange(preset) {
  statsPreset.value = preset;
  statsDateRange.value = calendarRange(preset);
}
function changeStatsRange(value) {
  if (value?.length === 2) statsPreset.value = "custom";
}
function taskEndDate(task) {
  return task.endedAt || task.endTime || task.completionForm?.savedAt || "";
}
function taskStartDate(task) {
  return task.startedAt || task.startTime || "";
}
function taskDuration(task) {
  if (Number(task.durationSec) > 0) return Number(task.durationSec);
  const end = taskEndDate(task),
    start = task.startedAt || task.startTime;
  return end && start
    ? Math.max(0, Math.round((new Date(end) - new Date(start)) / 1000))
    : 0;
}
const dateInRange = (date, range) => {
  if (!date || !range?.[0] || !range?.[1]) return false;
  const value = localDate(date);
  return value >= range[0] && value <= range[1];
};
const statsTaskRecords = computed(() => taskRows.value);
const actualCompletedTasks = computed(() =>
  statsTaskRecords.value.filter(
    (task) => task.status === "已完成" && taskEndDate(task),
  ),
);
const actualInProgressTasks = computed(() =>
  statsTaskRecords.value.filter(
    (task) => task.status === "进行中" && taskStartDate(task),
  ),
);
const useDemoStats = computed(
  () =>
    !apiMode && !actualCompletedTasks.value.length && !actualInProgressTasks.value.length,
);
const completedStatsSource = computed(() =>
  useDemoStats.value
    ? demoStatsTasks.filter((task) => task.status === "已完成")
    : actualCompletedTasks.value,
);
const inProgressStatsSource = computed(() =>
  useDemoStats.value
    ? demoStatsTasks.filter((task) => task.status === "进行中")
    : actualInProgressTasks.value,
);
const previousStatsDateRange = computed(() =>
  previousDateRange(statsDateRange.value),
);
const filteredStatsTasks = computed(() =>
  completedStatsSource.value.filter((task) =>
    dateInRange(taskEndDate(task), statsDateRange.value),
  ),
);
const previousCompletedStatsTasks = computed(() =>
  completedStatsSource.value.filter((task) =>
    dateInRange(taskEndDate(task), previousStatsDateRange.value),
  ),
);
const filteredInProgressTasks = computed(() =>
  inProgressStatsSource.value.filter((task) =>
    dateInRange(taskStartDate(task), statsDateRange.value),
  ),
);
const previousInProgressTasks = computed(() =>
  inProgressStatsSource.value.filter((task) =>
    dateInRange(taskStartDate(task), previousStatsDateRange.value),
  ),
);
const steelNo = (value) => String(value?.steelNo || "").trim();
const completedPieces = (tasks) =>
  new Set(tasks.map(steelNo).filter(Boolean)).size;
const averageDuration = (tasks) => {
  const durations = tasks.map(taskDuration).filter(Boolean);
  return durations.length
    ? Math.round(
        durations.reduce((sum, value) => sum + value, 0) /
          durations.length /
          60,
      )
    : null;
};
const durationHours = (duration) =>
  duration === null ? null : Number((duration / 60).toFixed(1));
const firstPassRate = (tasks) => {
  const records = tasks.filter((task) => steelNo(task));
  if (!records.length) return null;
  const counts = new Map();
  records.forEach((task) => {
    const no = steelNo(task);
    counts.set(no, (counts.get(no) || 0) + 1);
  });
  return Number(
    (
      (records.filter((task) => counts.get(steelNo(task)) === 1).length /
        records.length) *
      100
    ).toFixed(1),
  );
};
const comparePercent = (current, previous) => {
  if (current === null || previous === null)
    return { label: "无同期数据", tone: "neutral" };
  if (previous === 0)
    return current === 0
      ? { label: "持平", tone: "neutral" }
      : { label: "无同期数据", tone: "neutral" };
  const difference = ((current - previous) / previous) * 100;
  if (Math.abs(difference) < 0.05) return { label: "持平", tone: "neutral" };
  return {
    label: `${difference > 0 ? "↑" : "↓"} ${Math.abs(difference).toFixed(1)}%`,
    tone: difference > 0 ? "up" : "down",
  };
};
const comparePoints = (current, previous) => {
  if (current === null || previous === null)
    return { label: "无同期数据", tone: "neutral" };
  const difference = current - previous;
  if (Math.abs(difference) < 0.05) return { label: "持平", tone: "neutral" };
  return {
    label: `${difference > 0 ? "↑" : "↓"} ${Math.abs(difference).toFixed(1)} 个百分点`,
    tone: difference > 0 ? "up" : "down",
  };
};
const statsPeriodLabel = computed(() =>
  statsPreset.value === "7d"
    ? "近7日"
    : statsPreset.value === "30d"
      ? "近30日"
      : "所选期间",
);
const modelDurationRows = computed(() =>
  [...new Set(filteredStatsTasks.value.map((task) => String(task.model || "").trim()).filter(Boolean))]
    .map((model) => ({
      model,
      duration: averageDuration(
        filteredStatsTasks.value.filter((task) => task.model === model),
      ),
    }))
    .filter((item) => item.duration !== null),
);
const longestDurationModel = computed(() =>
  modelDurationRows.value.reduce(
    (selected, item) => (!selected || item.duration > selected.duration ? item : selected),
    null,
  ),
);
const bestDurationModel = computed(() =>
  modelDurationRows.value.reduce(
    (selected, item) => (!selected || item.duration < selected.duration ? item : selected),
    null,
  ),
);
const statsCards = computed(() => {
  const remote = productionStatistics.value?.overview;
  if (apiMode && remote) {
    const period = statsPeriodLabel.value,
      completed = remote.completed || {},
      backlog = remote.backlog || {},
      passRate = remote.firstPassRate || {},
      duration = remote.averageDurationSec || {},
      longestModel = remote.longestModel || {},
      bestModel = remote.bestModel || {},
      hours = (value) => value === null || value === undefined ? null : Number((Number(value) / 3600).toFixed(1));
    return [
      { title: `${period}完成件数`, value: completed.value || 0, unit: "件", comparison: comparePercent(completed.value, completed.previous), theme: "blue" },
      { title: "在修积压", value: backlog.value || 0, unit: "件", comparison: comparePercent(backlog.value, backlog.previous), theme: "cyan" },
      { title: "一次合格率", value: passRate.value ?? "—", unit: passRate.value === null || passRate.value === undefined ? "" : "%", comparison: comparePoints(passRate.value, passRate.previous), theme: "blue-1" },
      { title: "总平均耗时", value: hours(duration.value) ?? "—", unit: duration.value === null || duration.value === undefined ? "" : "h", comparison: comparePercent(duration.value, duration.previous), theme: "cyan-1" },
      { title: "平均最长耗时型号", value: hours(longestModel.averageDurationSec) ?? "—", unit: longestModel.averageDurationSec === null || longestModel.averageDurationSec === undefined ? "" : "h", detail: longestModel.model || "暂无完成数据", theme: "gray-1" },
      { title: "平均最佳耗时型号", value: hours(bestModel.averageDurationSec) ?? "—", unit: bestModel.averageDurationSec === null || bestModel.averageDurationSec === undefined ? "" : "h", detail: bestModel.model || "暂无完成数据", theme: "gray-1" },
    ];
  }
  const completed = completedPieces(filteredStatsTasks.value),
    previousCompleted = previousCompletedStatsTasks.value.length
      ? completedPieces(previousCompletedStatsTasks.value)
      : null,
    inProgress = filteredInProgressTasks.value.length,
    previousInProgress = previousInProgressTasks.value.length
      ? previousInProgressTasks.value.length
      : null,
    passRate = firstPassRate(filteredStatsTasks.value),
    previousPassRate = firstPassRate(previousCompletedStatsTasks.value),
    duration = averageDuration(filteredStatsTasks.value),
    previousDuration = averageDuration(previousCompletedStatsTasks.value),
    longestModel = longestDurationModel.value,
    bestModel = bestDurationModel.value,
    period = statsPeriodLabel.value;
  return [
    {
      title: `${period}完成件数`,
      value: completed,
      unit: "件",
      comparison: comparePercent(completed, previousCompleted),
      theme: "blue",
    },
    {
      title: "在修积压",
      value: inProgress,
      unit: "件",
      comparison: comparePercent(inProgress, previousInProgress),
      theme: "cyan",
    },
    {
      title: "一次合格率",
      value: passRate === null ? "—" : passRate,
      unit: passRate === null ? "" : "%",
      comparison: comparePoints(passRate, previousPassRate),
      theme: "blue-1",
    },
    {
      title: "总平均耗时",
      value: duration === null ? "—" : durationHours(duration),
      unit: duration === null ? "" : "h",
      comparison: comparePercent(duration, previousDuration),
      theme: "cyan-1",
    },
    {
      title: "平均最长耗时型号",
      value: longestModel ? durationHours(longestModel.duration) : "—",
      unit: longestModel ? "h" : "",
      detail: longestModel?.model || "暂无完成数据",
      theme: "gray-1",
    },
    {
      title: "平均最佳耗时型号",
      value: bestModel ? durationHours(bestModel.duration) : "—",
      unit: bestModel ? "h" : "",
      detail: bestModel?.model || "暂无完成数据",
      theme: "gray-1",
    },
  ];
});
const stationHeatMeta = {
  idle: "空闲",
  normal: "正常",
  busy: "繁忙",
  congested: "拥堵",
  unconfigured: "未配置",
};
const stationHeatStatus = (count) =>
  count === 0
    ? "idle"
    : count === 1
      ? "normal"
      : count <= 3
        ? "busy"
        : "congested";
const liveStationTiles = computed(() => {
  if (apiMode && productionStatistics.value?.liveStations) {
    return productionStatistics.value.liveStations.map((item) => ({
      id: Number(item.stationNo),
      name: item.name || `工位${item.stationNo}`,
      configured: !!item.configured,
      tasks: item.tasks || [],
      count: Number(item.count || 0),
      status: item.status || "unconfigured",
      operatorName: String(item.operatorName || "").trim(),
      label: stationHeatMeta[item.status] || "未配置",
    }));
  }
  const workstationsByNo = new Map(
    store.state.workstations.map((item) => [Number(item.stationNo || item.id), item]),
  );
  const maxStationNo = Math.max(
    11,
    ...store.state.workstations.map((item) => Number(item.stationNo || item.id) || 0),
  );
  return Array.from({ length: maxStationNo }, (_, index) => {
    const id = index + 1,
      workstation = workstationsByNo.get(id),
      tasks = store.liveStationTasks
        .filter((task) => task.liveStationId === Number(workstation?.id))
        .sort(
          (a, b) =>
            String(a.model || "").localeCompare(
              String(b.model || ""),
              "zh-CN",
            ) ||
            String(a.steelNo || "").localeCompare(
              String(b.steelNo || ""),
              "zh-CN",
            ),
        ),
      status = workstation ? stationHeatStatus(tasks.length) : "unconfigured";
    return {
      id,
      name: workstation?.name || `工位${id}`,
      configured: !!workstation,
      tasks,
      count: tasks.length,
      status,
      label: stationHeatMeta[status],
    };
  });
});
const normalizeParameterValue = (value) => String(value ?? "").trim();
const parameterDeviation = (value) => {
  const actual = normalizeParameterValue(value.actual),
    parameter = value.parameter || {},
    reference = normalizeParameterValue(parameter.referenceValue);
  if (!actual) return false;
  if (Number(parameter.type) === 1) {
    const options = (
      Array.isArray(parameter.options)
        ? parameter.options
        : String(parameter.options || reference).split(/\r?\n/)
    )
      .map(normalizeParameterValue)
      .filter(Boolean);
    return actual !== options[0];
  }
  if (Number(parameter.type) === 2) {
    return !isRangeValueValid(actual, parameter.referenceValue);
  }
  // 兼容旧任务快照中“17-23”一类尚未迁移到范围值的内联区间。
  const range = /^(-?\d+(?:\.\d+)?)\s*(?:-|~|至)\s*(-?\d+(?:\.\d+)?)$/.exec(
    reference,
  );
  if (range && Number.isFinite(Number(actual))) {
    const lower = Math.min(Number(range[1]), Number(range[2])),
      upper = Math.max(Number(range[1]), Number(range[2]));
    return Number(actual) < lower || Number(actual) > upper;
  }
  return actual !== reference;
};
const useAnalyticsDemo = computed(() => !apiMode && !actualCompletedTasks.value.length);
const analyticsTasks = computed(() =>
  useAnalyticsDemo.value ? [] : filteredStatsTasks.value,
);
const analyticModelOptions = computed(() => {
  if (apiMode && productionStatistics.value?.models) {
    return productionStatistics.value.models.map((model) => ({ value: model, label: model }));
  }
  const withData = [
      ...new Set(
        analyticsTasks.value.map((task) => task.model).filter(Boolean),
      ),
    ],
    known = [
      ...store.productModels,
      ...store.state.processFiles.map((file) => file.model),
    ].filter(Boolean);
  return [...new Set([...withData, ...known])].map((model) => ({
    value: model,
    label: model,
  }));
});
const selectedAnalysisModel = ref("");
const selectedDeviationModel = ref("");
watch(
  analyticModelOptions,
  (options) => {
    if (!options.some((option) => option.value === selectedAnalysisModel.value))
      selectedAnalysisModel.value = options[0]?.value || "";
    if (!options.some((option) => option.value === selectedDeviationModel.value))
      selectedDeviationModel.value = options[0]?.value || "";
  },
  { immediate: true },
);
const parameterDeviationRows = computed(() => {
  if (apiMode) {
    return productionStatistics.value?.parameterDeviationsByModel?.[selectedDeviationModel.value] || [];
  }
  if (useAnalyticsDemo.value) {
    const index = Math.max(
        0,
        analyticModelOptions.value.findIndex(
          (item) => item.value === selectedDeviationModel.value,
        ),
      ),
      patterns = [
        [
          { name: "工位1", value: 7 },
          { name: "工位4", value: 5 },
          { name: "工位2", value: 3 },
          { name: "工位6", value: 2 },
        ],
        [
          { name: "工位2", value: 6 },
          { name: "工位5", value: 4 },
          { name: "工位1", value: 3 },
        ],
        [
          { name: "工位3", value: 5 },
          { name: "工位4", value: 4 },
          { name: "工位6", value: 2 },
        ],
        [
          { name: "工位1", value: 4 },
          { name: "工位5", value: 3 },
          { name: "工位2", value: 1 },
        ],
      ];
    return patterns[index % patterns.length];
  }
  const groups = new Map();
  analyticsTasks.value
    .filter((task) => task.model === selectedDeviationModel.value)
    .forEach((task) => {
    const steps = task.processSnapshot || [],
      logs = task.logs || {};
    steps.forEach((step) => {
      const log = logs[step.stepNo];
      if (log?.actionCode !== 0) return;
      (step.parameters || []).forEach((parameter) => {
        const actual = log.values?.[parameter.index];
        if (!parameterDeviation({ actual, parameter })) return;
        const id = Number(step.stationId || 1),
          name =
            step.stationName ||
            log.stationName ||
            task.stationConfirmations?.[id]?.stationName ||
            stationName(id),
          item = groups.get(id) || { name, value: 0 };
        item.value++;
        groups.set(id, item);
      });
    });
    });
  return [...groups.values()].sort(
    (a, b) => b.value - a.value || a.name.localeCompare(b.name, "zh-CN"),
  );
});
const demoStepDurationRows = computed(() => {
  const steps = (
      store.state.processFiles.find(
        (file) => file.model === selectedAnalysisModel.value,
      )?.steps || store.steps
    ).slice(0, 5),
    weights = [42, 28, 17, 8, 5];
  return steps.map((step, index) => ({
    name: `${step.stepNo}. ${step.title}`,
    value: (weights[index] || 5) * 60,
  }));
});
const stepDurationRows = computed(() => {
  if (apiMode) {
    return productionStatistics.value?.stepDurationsByModel?.[selectedAnalysisModel.value] || [];
  }
  if (useAnalyticsDemo.value) return demoStepDurationRows.value;
  const groups = new Map();
  analyticsTasks.value
    .filter((task) => task.model === selectedAnalysisModel.value)
    .forEach((task) => {
      const steps = new Map(
          (task.processSnapshot || []).map((step) => [
            Number(step.stepNo),
            step,
          ]),
        ),
        logs = Object.values(task.logs || {})
          .filter((log) => log.actionCode === 0 && log.time)
          .sort((a, b) => new Date(a.time) - new Date(b.time));
      logs.forEach((log, index) => {
        const previous = index ? logs[index - 1].time : task.startedAt,
          duration = Math.max(
            0,
            (new Date(log.time) - new Date(previous)) / 1000,
          ),
          step = steps.get(Number(log.stepNo));
        if (!step || !duration) return;
        const key = Number(step.stepNo),
          item = groups.get(key) || {
            name: `${step.stepNo}. ${step.title}`,
            value: 0,
          };
        item.value += duration;
        groups.set(key, item);
      });
    });
  return [...groups.values()].sort((a, b) => b.value - a.value);
});
const monthKey = (date) => {
  const value = new Date(date);
  return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, "0")}`;
};
const analyticsEndDate = computed(
  () => statsDateRange.value?.[1] || localDate(new Date()),
);
const monthlyCapacityRows = computed(() => {
  if (apiMode) return productionStatistics.value?.dailyCapacity || [];
  const end = new Date(`${analyticsEndDate.value}T12:00:00`),
    months = [];
  for (let offset = 11; offset >= 0; offset--) {
    const cursor = new Date(end.getFullYear(), end.getMonth() - offset, 1);
    months.push({
      key: `${cursor.getFullYear()}-${String(cursor.getMonth() + 1).padStart(2, "0")}`,
      label: `${String(cursor.getFullYear()).slice(2)}-${String(cursor.getMonth() + 1).padStart(2, "0")}`,
      count: 0,
    });
  }
  if (useAnalyticsDemo.value)
    return months.map((item, index) => ({
      ...item,
      count: [18, 23, 21, 28, 31, 27, 35, 39, 36, 42, 46, 51][index],
    }));
  const values = new Map(months.map((item) => [item.key, new Set()]));
  actualCompletedTasks.value.forEach((task) => {
    const key = monthKey(taskEndDate(task));
    if (values.has(key) && steelNo(task)) values.get(key).add(steelNo(task));
  });
  return months.map((item) => ({
    ...item,
    count: values.get(item.key)?.size || 0,
  }));
});
const dailyCapacityRows = computed(() => {
  if (apiMode) return productionStatistics.value?.dailyCapacity || [];
  const [from, to] = statsDateRange.value || [];
  if (!from || !to) return [];
  const values = new Map();
  for (let cursor = new Date(`${from}T12:00:00`), end = new Date(`${to}T12:00:00`); cursor <= end; cursor.setDate(cursor.getDate() + 1)) {
    const key = localDate(cursor);
    values.set(key, new Set());
  }
  completedStatsSource.value.forEach((task) => {
    const date = localDate(taskEndDate(task));
    const pieces = values.get(date), number = steelNo(task);
    if (pieces && number) pieces.add(number);
  });
  return [...values.entries()].map(([date, pieces]) => ({ date, label: date.slice(5), count: pieces.size }));
});
const statsTodayCapacity = computed(() => {
  if (apiMode) return Number(productionStatistics.value?.todayCapacity || 0);
  return completedPieces(completedStatsSource.value.filter(task => localDate(taskEndDate(task)) === localDate(new Date())));
});
const statsTodayCapacityPrevious = computed(() => {
  if (apiMode) {
    const value = productionStatistics.value?.todayCapacityPrevious;
    return value === null || value === undefined ? null : Number(value);
  }
  const yesterday = new Date();
  yesterday.setDate(yesterday.getDate() - 1);
  const date = localDate(yesterday);
  return completedPieces(completedStatsSource.value.filter(task => localDate(taskEndDate(task)) === date));
});
const statsMonthCapacity = computed(() => {
  if (apiMode) return Number(productionStatistics.value?.monthCapacity || 0);
  const prefix = localDate(new Date()).slice(0, 7);
  return completedPieces(completedStatsSource.value.filter(task => localDate(taskEndDate(task)).startsWith(prefix)));
});
const statsMonthCapacityPrevious = computed(() => {
  if (apiMode) {
    const value = productionStatistics.value?.monthCapacityPrevious;
    return value === null || value === undefined ? null : Number(value);
  }
  const now = new Date();
  const previousMonthEnd = new Date(now.getFullYear(), now.getMonth(), 0);
  previousMonthEnd.setDate(Math.min(now.getDate(), previousMonthEnd.getDate()));
  const range = [localDate(new Date(previousMonthEnd.getFullYear(), previousMonthEnd.getMonth(), 1)), localDate(previousMonthEnd)];
  return completedPieces(completedStatsSource.value.filter(task => dateInRange(taskEndDate(task), range)));
});
const capacityComparison = (current, previous) => {
  if (previous === null || previous === undefined) return { label: "无同期数据", tone: "neutral" };
  const difference = Number(current) - Number(previous);
  if (!difference) return { label: "持平", tone: "neutral" };
  if (Number(previous) === 0) return { label: `${difference > 0 ? "↑" : "↓"} ${Math.abs(difference)} 件`, tone: difference > 0 ? "up" : "down" };
  const percentage = difference / Number(previous) * 100;
  return { label: `${percentage > 0 ? "↑" : "↓"} ${Math.abs(percentage).toFixed(1)}%`, tone: percentage > 0 ? "up" : "down" };
};
const statsTodayCapacityComparison = computed(() => capacityComparison(statsTodayCapacity.value, statsTodayCapacityPrevious.value));
const statsMonthCapacityComparison = computed(() => capacityComparison(statsMonthCapacity.value, statsMonthCapacityPrevious.value));
const productionLinePositions = {
  1: { zoneLeft: "1.8%", y: "91%", cardY: "8%" }, 2: { zoneLeft: "12.5%", y: "91%", cardY: "0%" },
  3: { zoneLeft: "19.4%", y: "91%", cardY: "0%" }, 4: { zoneLeft: "27.8%", y: "91%", cardY: "9%" },
  5: { zoneLeft: "37.0%", y: "91%", cardY: "0%" }, 6: { zoneLeft: "44.1%", y: "91%", cardY: "0%" },
  7: { zoneLeft: "59.1%", y: "91%", cardY: "0%" }, 8: { zoneLeft: "67.9%", y: "91%", cardY: "10%" },
  9: { zoneLeft: "80.8%", y: "91%", cardY: "4%" }, 10: { zoneLeft: "90.0%", y: "91%", cardY: "10%" },
};
const hoveredStationId = ref(null);
function productionLineStyle(tile) {
  const position = productionLinePositions[tile.id] || productionLinePositions[10];
  return { "--station-zone-left": position.zoneLeft, "--station-y": position.y, "--card-y": position.cardY };
}
function handleStationEnter(stationId) {
  hoveredStationId.value = stationId;
}
function handleStationLeave(stationId) {
  if (hoveredStationId.value === stationId) hoveredStationId.value = null;
}
const stationHoverOverlay = computed(() => {
  const tile = liveStationTiles.value.find((item) => item.id === hoveredStationId.value);
  const maskByStation = {
    3: "bg_gw3.png",
    5: "bg_gw5.png",
    6: "bg_gw6.png",
    7: "bg_gw7.png",
  };
  const colorByStatus = {
    congested: "#CB0404",
    busy: "#FFCD24",
    normal: "#0077FA",
    idle: "#00FAED",
  };
  const mask = maskByStation[tile?.id];
  const color = colorByStatus[tile?.status];
  if (!tile?.configured || !mask || !color) {
    return {
      visible: false,
      style: { "--station-hover-mask": "none", "--station-hover-color": "transparent" },
    };
  }
  return {
    visible: true,
    style: {
      "--station-hover-mask": `url('/production-statistics/${mask}')`,
      "--station-hover-color": color,
    },
  };
});
function stationMarkerIcon(status) {
  return {
    idle: "/production-statistics/icon_empty.png",
    normal: "/production-statistics/icon_normal.png",
    busy: "/production-statistics/icon_busy.png",
  }[status] || "";
}
function copyStep() {
  const s = selectedStep.value;
  if (!s) return;
  selectedTemplateId.value =
    store.state.stepProjection[s.stepNo]?.templateId || "";
  const video = resolveVideoBinding(
      s.videoName,
      store.state.videos,
      store.state.deleted.videos,
    ).video,
    videoCategory = videoCategoryMap.value.get(video?.categoryId),
    videoParentId = videoCategory?.parentId || videoCategory?.id || "";
  stepForm.value = {
    title: s.title,
    content: String(s.content ?? ""),
    stationId: Number(s.stationId || 1),
    importance: s.importance,
    videoParentId,
    videoCategoryId: videoCategory?.id || "",
    videoName: s.videoName === "NONE" ? "" : s.videoName,
    parameters: s.parameters.map((p) => ({
      ...p,
      referenceValue: Number(p.type) === 2 ? normalizeRangeReference(p.referenceValue) || p.referenceValue : p.referenceValue,
      optionsText: (p.options?.length ? p.options : String(p.referenceValue || "").split(/\r?\n/)).join("\n"),
    })),
  };
}
watch([selectedStepNo, selectedStep], copyStep, { immediate: true });
function addParameter() {
  if (stepForm.value.parameters.length >= 3)
    return ElMessage.warning("每个步骤最多 3 组参数");
  stepForm.value.parameters.push({
    name: "",
    type: 0,
    referenceValue: "",
    optionsText: "",
  });
}
function selectStepVideoPrimary(id) {
  const parent = videoCategoryTree.value.find((item) => item.id === id);
  stepForm.value.videoParentId = id;
  stepForm.value.videoCategoryId = parent?.children?.[0]?.id || id || "";
  stepForm.value.videoName = "";
}
function selectStepVideoSecondary(id) {
  stepForm.value.videoCategoryId = id;
  stepForm.value.videoName = "";
}
async function saveStep() {
  const { videoParentId, videoCategoryId, ...f } = stepForm.value;
  if (!f.title?.trim()) return ElMessage.warning("请填写步骤标题");
  const parameters = f.parameters.map((parameter) => {
    const type = Number(parameter.type);
    const options = String(parameter.optionsText || "")
      .split(/\r?\n/)
      .map((item) => item.trim())
      .filter(Boolean);
    return {
      ...parameter,
      type,
      name: parameter.name?.trim(),
      referenceValue:
        type === 1 ? options.join("\n") : type === 2 ? normalizeRangeReference(parameter.referenceValue) || String(parameter.referenceValue || "").trim() : String(parameter.referenceValue || "").trim(),
      options: type === 1 ? options : [],
    };
  });
  for (const [index, parameter] of parameters.entries()) {
    if (!parameter.name) return ElMessage.warning(`参数 ${index + 1} 需填写名称`);
    if (parameter.type === 1 && new Set(parameter.options).size < 2)
      return ElMessage.warning(`参数 ${index + 1} 的单选至少需要两个不同选项`);
    if (parameter.type === 2) {
      const error = rangeReferenceError(parameter.referenceValue);
      if (error) return ElMessage.warning(`参数 ${index + 1}：${error}`);
    }
    if (parameter.type !== 1 && parameter.type !== 2 && !parameter.referenceValue)
      return ElMessage.warning(`参数 ${index + 1} 请填写参考值`);
  }
  const updatedStep = {
    ...f,
    content: String(f.content ?? ""),
    videoName: f.videoName || "NONE",
    parameters,
  };
  if (apiMode) {
    const process = store.state.processFiles.find(item => String(item.id) === String(store.state.processRecord.id));
    if (!process) return ElMessage.error("当前工艺不存在，请刷新后重试");
    try {
      store.replaceServerProcess(await api.updateProcessStep(process.id, selectedStepNo.value, { expectedVersion: process.rowVersion, step: updatedStep }));
    } catch (error) {
      return ElMessage.error(error instanceof ApiError ? error.message : "保存步骤失败，请稍后重试");
    }
  } else store.updateStep(selectedStepNo.value, updatedStep);
  ElMessage.success("步骤已保存；在制任务保持开始时快照");
}
async function applyTemplate() {
  if (!selectedTemplateId.value) return ElMessage.warning("请选择投影模板");
  if (apiMode) {
    const process = store.state.processFiles.find(item => String(item.id) === String(store.state.processRecord.id));
    if (!process) return ElMessage.error("当前工艺不存在，请刷新后重试");
    try {
      store.replaceServerProcess(await api.updateProcessProjection(process.id, selectedStepNo.value, {
        expectedVersion: process.rowVersion,
        templateId: selectedTemplateId.value,
        elements: [],
        inheritsTemplate: true,
      }));
    } catch (error) {
      return ElMessage.error(error instanceof ApiError ? error.message : "应用投影模板失败，请稍后重试");
    }
  } else store.applyTemplate(selectedStepNo.value, selectedTemplateId.value);
  ElMessage.success("已复制模板元素");
}
function goPreview() {
  window.open(
    location.origin +
      location.pathname +
      "#/admin/processes/" +
      store.state.processRecord.id +
      "/projector-preview/live?step=" +
      selectedStepNo.value,
    "_blank",
  );
}
function openProcess(id) {
  store.activateProcess(id);
  router.push("/admin/processes/" + id);
}
const importDialog = ref(false),
  importFile = ref(null),
  importInput = ref(null),
  autoFix = ref(false),
  prechecked = ref(false),
  importPreview = ref(null);
const importErrors = computed(() => importPreview.value?.errors || []);
function precheck(e) {
  importFile.value = e.target.files?.[0] || null;
  prechecked.value = false;
  importPreview.value = null;
}
const text = (value) => String(value ?? "").trim();
function parameterType(value) {
  const normalized = text(value).toLowerCase();
  if (["0", "输入", "手动输入"].includes(normalized)) return 0;
  if (["1", "选项", "单选"].includes(normalized)) return 1;
  if (["2", "范围", "范围值"].includes(normalized)) return 2;
  return null;
}
async function runPrecheck() {
  if (!importFile.value) return ElMessage.warning("请先选择 Excel 文件");
  const errors = [],
    steps = [],
    models = new Set(),
    stepNos = new Set();
  try {
    const buffer = await importFile.value.arrayBuffer(),
      workbook = XLSX.read(buffer, { type: "array" }),
      sheet = workbook.Sheets[workbook.SheetNames[0]],
      rows = XLSX.utils.sheet_to_json(sheet, { header: 1, defval: "" }),
      headerIndex = rows.findIndex(
        (row) =>
          text(row[0]) === "减振器型号" &&
          text(row[1]) === "标题" &&
          text(row[2]) === "步骤",
      );
    if (headerIndex < 0)
      throw new Error("未找到“减振器型号、标题、步骤”表头行");
    const header = rows[headerIndex].map(text),
      column = (labels) => header.findIndex((value) => labels.includes(value)),
      modelIndex = column(["减振器型号"]),
      titleIndex = column(["标题"]),
      stepIndex = column(["步骤"]),
      contentIndex = column(["步骤内容"]),
      stationIndex = column(["工位"]),
      importanceIndex = column(["重视等级", "重要等级"]),
      videoIndex = column(["视频名称"]),
      parameterColumns = [1, 2, 3].map((index) => ({
        type: column(
          index === 1 ? ["参数类型1", "参数类型"] : [`参数类型${index}`],
        ),
        name: column([`参数名称${index}`]),
        referenceValue: column([`参考参数值${index}`, `参数值${index}`]),
      }));
    if (
      [modelIndex, titleIndex, stepIndex, importanceIndex].some(
        (index) => index < 0,
      )
    )
      throw new Error("表头必须包含“减振器型号、标题、步骤、重视等级”");
    const workstationsByNo = new Map(
      store.state.workstations.map((station) => [
        Number(station.stationNo || station.id),
        station,
      ]),
    );
    const defaultStationId = workstationsByNo.get(1)?.id || 1;
    let fallbackStationCount = 0;
    for (let rowIndex = headerIndex + 1; rowIndex < rows.length; rowIndex++) {
      const row = rows[rowIndex],
        rowNo = rowIndex + 1,
        model = text(row[modelIndex]),
        title = text(row[titleIndex]),
        stepNo = Number(row[stepIndex]);
      if (!row.some((value) => text(value))) continue;
      if (!model || !title || !Number.isInteger(stepNo) || stepNo <= 0) {
        errors.push(`第 ${rowNo} 行：减振器型号、标题和正整数步骤为必填项`);
        continue;
      }
      const requestedStationNo =
          stationIndex < 0 ? 1 : Number(row[stationIndex]),
        workstation = workstationsByNo.get(requestedStationNo),
        stationId = workstation?.id || defaultStationId;
      if (stationIndex < 0 || !workstation)
        fallbackStationCount += 1;
      models.add(model);
      if (stepNos.has(stepNo)) {
        errors.push(`第 ${rowNo} 行：步骤 ${stepNo} 重复`);
        continue;
      }
      stepNos.add(stepNo);
      const importance = Number(row[importanceIndex]);
      if (![0, 1].includes(importance)) {
        errors.push(`第 ${rowNo} 行：重视等级只能为 0 或 1`);
        continue;
      }
      const videoBinding = resolveVideoBinding(
        videoIndex < 0 ? "" : text(row[videoIndex]),
        store.state.videos,
        store.state.deleted.videos,
      );
      if (videoBinding.status === "missing") {
        errors.push(
          `第 ${rowNo} 行：视频名称「${videoBinding.requestedName}」未在视频资源中找到，请先上传视频或修改名称`,
        );
        continue;
      }
      if (videoBinding.status === "ambiguous") {
        errors.push(
          `第 ${rowNo} 行：视频名称「${videoBinding.requestedName}」匹配到多个视频，请使用完整且唯一的视频名称`,
        );
        continue;
      }
      const parameters = [];
      parameterColumns.forEach((columns, index) => {
        const rawType = columns.type < 0 ? "" : text(row[columns.type]),
          name = columns.name < 0 ? "" : text(row[columns.name]),
          referenceValue =
            columns.referenceValue < 0 ? "" : text(row[columns.referenceValue]);
        if (!rawType && !name && !referenceValue) return;
        let type = parameterType(rawType);
        if (type === null && autoFix.value && name && referenceValue) type = 0;
        if (type === null || !name || !referenceValue) {
          errors.push(
            `第 ${rowNo} 行：参数 ${index + 1} 的类型、名称和参考值必须同时填写`,
          );
          return;
        }
        const options =
          type === 1
            ? referenceValue
                .split(/\r?\n/)
                .map((item) => item.trim())
                .filter(Boolean)
            : [];
        if (type === 1 && new Set(options).size < 2) {
          errors.push(
            `第 ${rowNo} 行：参数 ${index + 1} 为单选时至少需要两个不同选项`,
          );
          return;
        }
        const normalizedReference = type === 2 ? normalizeRangeReference(referenceValue) : referenceValue;
        if (type === 2 && !normalizedReference) {
          errors.push(`第 ${rowNo} 行：参数 ${index + 1} ${rangeReferenceError(referenceValue)}`);
          return;
        }
        parameters.push({
          index: index + 1,
          type,
          name,
          referenceValue: normalizedReference,
          options,
          sourceTypeMissing: false,
        });
      });
      steps.push({
        stepNo,
        title,
        content: contentIndex < 0 ? "" : String(row[contentIndex] ?? ""),
        stationId,
        importance,
        videoName: videoBinding.video?.name || "NONE",
        parameters,
        sourceRow: rowNo,
      });
    }
    if (models.size !== 1) errors.push("一个工艺文件只能包含一个减振器型号");
    if (!steps.length) errors.push("未读取到有效工艺步骤");
    const model = [...models][0] || "",
      sourceDocument = text(rows[0]?.[1]) || importFile.value.name;
    importPreview.value = {
      errors,
      payload: errors.length
        ? null
        : {
            model,
            title: `${model} 装配作业`,
            version: "v1",
            sourceWorkbook: importFile.value.name,
            sourceDocument,
            steps,
            fallbackStationCount,
          },
    };
    prechecked.value = !errors.length;
    if (errors.length) ElMessage.warning(`预检发现 ${errors.length} 项问题`);
    else
      ElMessage.success(
        `预检通过：识别 ${steps.length} 个工艺步骤${fallbackStationCount ? `，${fallbackStationCount} 步默认绑定工位 1` : ""}`,
      );
  } catch (error) {
    importPreview.value = {
      errors: [error.message || "Excel 解析失败"],
      payload: null,
    };
    prechecked.value = false;
    ElMessage.error(error.message || "Excel 解析失败");
  }
}
async function confirmImport() {
  const payload = importPreview.value?.payload;
  if (!prechecked.value || !payload) return;
  let process;
  try {
    if (apiMode) {
      process = await api.createProcess(payload);
      store.replaceServerProcess(process);
    } else process = store.importProcess(payload);
  } catch (error) {
    return ElMessage.error(error instanceof ApiError ? error.message : "工艺导入失败，请稍后重试");
  }
  importDialog.value = false;
  ElMessage.success(
    `工艺「${process.model}」已导入，共 ${process.steps.length} 步${payload.fallbackStationCount ? `，${payload.fallbackStationCount} 步默认绑定工位 1` : ""}，已绑定默认投影模板`,
  );
}
async function addWorkstation() {
  try {
    if (!apiMode) {
      const workstation = store.addWorkstation();
      ElMessage.success(`已新增 ${workstation.name}，可直接修改名称和识别模式`);
      return;
    }
    const workstation = workstationFromApi(
      await api.createWorkstation({ name: "", captureMode: 1 }),
    );
    store.state.workstations.push(workstation);
    store.state.workstations.sort((left, right) => left.stationNo - right.stationNo || left.id - right.id);
    ElMessage.success(`已新增 ${workstation.name}，可直接修改名称和识别模式`);
  } catch (error) {
    ElMessage.error(
      error instanceof ApiError ? error.message : "新增工位失败，请稍后重试",
    );
  }
}
async function workstationVersion(workstation) {
  const version = Number(workstation.rowVersion);
  if (Number.isInteger(version)) return version;
  const workstations = (await api.workstations()).map(workstationFromApi);
  store.state.workstations = workstations;
  const current = workstations.find(
    (item) => Number(item.id) === Number(workstation.id),
  );
  const refreshedVersion = Number(current?.rowVersion);
  if (!Number.isInteger(refreshedVersion)) {
    throw new ApiError("工位版本信息无效，请刷新页面后重试");
  }
  return refreshedVersion;
}
async function saveWorkstation(workstation, changes) {
  const next = {
    name: String(changes.name || workstation.name).trim() || workstation.name,
    captureMode: changes.captureMode === "manual" ? "manual" : "auto",
  };
  if (next.name === workstation.name && next.captureMode === workstation.captureMode)
    return;
  try {
    if (!apiMode) {
      store.updateWorkstation(workstation.id, next);
      return;
    }
    const expectedVersion = await workstationVersion(workstation);
    const updated = await api.updateWorkstation(workstation.id, {
      name: next.name,
      captureMode: next.captureMode === "manual" ? 0 : 1,
      expectedVersion,
    });
    const index = store.state.workstations.findIndex(
      (item) => Number(item.id) === Number(workstation.id),
    );
    if (index >= 0) store.state.workstations[index] = workstationFromApi(updated);
    ElMessage.success("工位配置已保存，操作端将自动更新");
  } catch (error) {
    ElMessage.error(
      error instanceof ApiError ? error.message : "保存工位配置失败，请稍后重试",
    );
  }
}
async function removeWorkstation(workstation) {
  try {
    await ElMessageBox.confirm(
      `删除「${workstation.name}」后，当前工艺中归属它的步骤会改绑到工位 1。确认删除？`,
      "删除工位",
      {
        confirmButtonText: "确认删除",
        cancelButtonText: "取消",
        type: "warning",
      },
    );
    if (!apiMode) {
      const result = store.deleteWorkstation(workstation.id);
      if (!result.ok) return ElMessage.warning(result.message);
    } else {
      const expectedVersion = await workstationVersion(workstation);
      await api.deleteWorkstation(workstation.id, expectedVersion);
      store.replaceServerProcesses(await api.processes());
      store.state.workstations = store.state.workstations.filter(
        (item) => Number(item.id) !== Number(workstation.id),
      );
    }
    ElMessage.success(`已删除 ${workstation.name}，相关工艺步骤已改绑到工位 1`);
  } catch (error) {
    if (error !== "cancel" && error !== "close") {
      ElMessage.error(
        error instanceof ApiError ? error.message : "删除工位失败，请稍后重试",
      );
    }
  }
}
function download() {
  const a = document.createElement("a");
  a.href = "/demo-process-template.xlsx";
  a.download = store.state.processRecord.sourceWorkbook;
  a.click();
}
const uploadDialog = ref(false),
  uploadCategory = ref([]),
  uploadFiles = ref([]),
  uploadInput = ref(null);
function chooseVideos(e) {
  uploadFiles.value = Array.from(e.target.files || []);
}
function openUploadDialog() {
  uploadCategory.value = [];
  uploadFiles.value = [];
  if (uploadInput.value) uploadInput.value.value = "";
  uploadDialog.value = true;
}
async function uploadVideos() {
  const categoryId = Array.isArray(uploadCategory.value)
    ? uploadCategory.value.at(-1)
    : uploadCategory.value;
  if (!categoryId) return ElMessage.warning("请选择视频分类");
  if (!uploadFiles.value.length) return ElMessage.warning("请选择视频文件");
  if (apiMode) {
    try {
      const files = await Promise.all(uploadFiles.value.map(file => api.uploadFile(file, 0)));
      const buildPayload = async replaceMode => {
        let existing = []
        if (replaceMode === 1) existing = (await api.videos({ page: 1, pageSize: 100 })).items || []
        return {
          categoryId: Number(categoryId),
          replaceMode,
          files: files.map((item, index) => {
            const found = existing.find(video => normalizeVideoName(video.name) === normalizeVideoName(uploadFiles.value[index].name))
            return { fileId: item.id, name: uploadFiles.value[index].name, expectedVersion: found?.rowVersion }
          }),
        }
      }
      let result
      try {
        result = await api.createVideos(await buildPayload(0))
      } catch (error) {
        if (!(error instanceof ApiError) || error.code !== 1013) throw error
        await ElMessageBox.confirm("资源库中存在同名视频。替换将保留既有视频 ID 和工艺绑定。", "检测到重名视频", {
          confirmButtonText: "替换上传", cancelButtonText: "取消上传", type: "warning",
        })
        result = await api.createVideos(await buildPayload(1))
      }
      const current = new Map(store.state.videos.map(item => [String(item.id), item]))
      result.items.forEach(item => {
        current.set(String(item.id), { ...current.get(String(item.id)), ...item, sessionPreview: false })
        store.state.deleted.videos = store.state.deleted.videos.filter(id => String(id) !== String(item.id))
      })
      store.state.videos = [...current.values()]
      uploadDialog.value = false
      ElMessage.success("已上传 " + result.items.length + " 个视频，媒体解析完成后可播放")
    } catch (error) {
      if (error !== "cancel") ElMessage.error(error instanceof ApiError ? error.message : "视频上传失败")
    }
    return
  }
  const selectedNames = new Set();
  const duplicateInSelection = uploadFiles.value.find((file) => {
    const key = normalizeVideoName(file.name);
    if (selectedNames.has(key)) return true;
    selectedNames.add(key);
    return false;
  });
  if (duplicateInSelection)
    return ElMessage.warning(
      `本次选择中包含重名视频「${duplicateInSelection.name}」，请只保留一个文件`,
    );
  const duplicates = uploadFiles.value
    .map((file) => ({
      file,
      video: resolveVideoBinding(
        file.name,
        store.state.videos,
        store.state.deleted.videos,
      ).video,
    }))
    .filter((item) => item.video);
  if (duplicates.length) {
    const names = duplicates.map((item) => `「${item.video.name}」`).join("、");
    try {
      await ElMessageBox.confirm(
        `视频资源中已存在 ${names}。替换后会更新视频文件和分类，原有工艺绑定保持有效。`,
        "检测到重名视频",
        {
          confirmButtonText: "替换上传",
          cancelButtonText: "取消上传",
          type: "warning",
        },
      );
    } catch {
      return;
    }
  }
  const duplicateByName = new Map(
    duplicates.map((item) => [normalizeVideoName(item.file.name), item.video]),
  );
  uploadFiles.value.forEach((file, i) => {
    const record = {
      name: file.name,
      categoryId,
      duration: "待解析",
      batch: "本次会话",
      sessionPreview: true,
      previewUrl: URL.createObjectURL(file),
    };
    const existing = duplicateByName.get(normalizeVideoName(file.name));
    if (existing) {
      if (existing.sessionPreview && existing.previewUrl)
        URL.revokeObjectURL(existing.previewUrl);
      Object.assign(existing, record);
      return;
    }
    store.state.videos.push({
      id: `V-${Date.now()}-${i}`,
      ...record,
    });
  });
  uploadDialog.value = false;
  ElMessage.success(
    duplicates.length
      ? `已上传 ${uploadFiles.value.length} 个视频，其中 ${duplicates.length} 个已替换`
      : "视频已上传，可在本次会话播放",
  );
}
const categoryDialog = ref(false),
  categoryForm = ref({ id: "", parentId: "", name: "" }),
  categoryMode = ref("create");
function openCategoryDialog(parentId = null) {
  categoryMode.value = "create";
  categoryForm.value = { id: "", parentId: parentId || "", name: "" };
  categoryDialog.value = true;
}
function openCategoryRename(item) {
  categoryMode.value = "rename";
  categoryForm.value = {
    id: item.id,
    parentId: item.parentId || "",
    name: item.name,
  };
  categoryDialog.value = true;
}
function saveVideoCategory() {
  const result =
    categoryMode.value === "rename"
      ? store.renameVideoCategory(
          categoryForm.value.id,
          categoryForm.value.name,
        )
      : store.createVideoCategory({
          name: categoryForm.value.name,
          parentId: categoryForm.value.parentId || null,
        });
  if (!result.ok) return ElMessage.warning(result.message);
  categoryDialog.value = false;
  ElMessage.success(
    categoryMode.value === "rename" ? "分类已重命名" : "分类已新增",
  );
}
async function removeVideoCategory(item) {
  try {
    await ElMessageBox.confirm(
      `确认删除分类「${item.name}」？`,
      "删除视频分类",
      {
        confirmButtonText: "确认删除",
        cancelButtonText: "取消",
        type: "warning",
      },
    );
    const result = store.deleteVideoCategory(item.id);
    if (!result.ok) return ElMessage.warning(result.message);
    if (category.value === item.id) category.value = "ALL";
    ElMessage.success("分类已删除");
  } catch {}
}
const previewVideo = ref(null),
  previewDialog = ref(false);
async function viewVideo(video) {
  previewVideo.value = apiMode
    ? { ...video, loading: true, previewUrl: "", playbackMessage: "" }
    : video;
  previewDialog.value = true;
  if (!apiMode) return;
  try {
    const playback = await api.playback(video.id);
    previewVideo.value = {
      ...video,
      loading: false,
      previewUrl: Number(playback.playable) === 1 ? playback.url : "",
      playbackMessage: Number(playback.playable) === 1
        ? ""
        : "视频正在解析或当前版本不可播放，请稍后刷新后重试。",
    };
  } catch (error) {
    previewVideo.value = {
      ...video,
      loading: false,
      previewUrl: "",
      playbackMessage: error instanceof ApiError ? error.message : "无法获取视频播放地址，请稍后重试。",
    };
  }
}
const iconDialog = ref(false),
  iconDialogMode = ref("create"),
  iconForm = ref({ name: "", originalName: "" }),
  iconFile = ref(null),
  iconInput = ref(null),
  iconPreview = ref(null),
  iconViewDialog = ref(false),
  savingIcon = ref(false);
function openIconDialog(item) {
  iconDialogMode.value = item ? "edit" : "create";
  iconForm.value = { name: item?.name || "", originalName: item?.name || "" };
  iconFile.value = null;
  if (iconInput.value) iconInput.value.value = "";
  iconDialog.value = true;
}
function chooseIcon(e) {
  const file = e.target.files?.[0];
  if (!file) return;
  if (file.type !== "image/png")
    return ElMessage.warning("请上传 PNG 格式的投影素材");
  iconFile.value = file;
}
async function saveIcon() {
  if (!iconForm.value.name?.trim()) return ElMessage.warning("请填写素材名称");
  if (iconDialogMode.value === "create" && !iconFile.value)
    return ElMessage.warning("请上传 PNG 文件");
  if (apiMode) {
    if (savingIcon.value) return;
    if (iconDialogMode.value === "edit") return ElMessage.warning("已上传图标暂不支持直接替换，请删除后重新上传");
    savingIcon.value = true;
    try {
      const file = await api.uploadFile(iconFile.value, 1);
      let icon;
      try {
        icon = await api.createIcon({ name: iconForm.value.name.trim(), fileId: file.id });
      } catch (error) {
        if (!(error instanceof ApiError) || error.code !== 1006) throw error;
        const existing = (await api.icons()).find(item => item.name === iconForm.value.name.trim());
        if (!existing || Number(existing.isBuiltIn) === 1) throw error;
        try {
          await ElMessageBox.confirm("已存在同名自定义投影素材，是否替换其图片？", "检测到重名素材", {
            confirmButtonText: "替换素材", cancelButtonText: "取消上传", type: "warning",
          });
        } catch {
          return;
        }
        icon = await api.replaceIcon(existing.id, { fileId: file.id, expectedVersion: existing.rowVersion });
      }
      const index = store.state.customIcons.findIndex(item => String(item.id) === String(icon.id));
      const record = { id: icon.id, name: icon.name, dataUrl: icon.url, rowVersion: icon.rowVersion };
      if (index >= 0) store.state.customIcons[index] = { ...store.state.customIcons[index], ...record };
      else store.state.customIcons.push(record);
      iconDialog.value = false;
      ElMessage.success("投影素材已保存");
    } catch (error) {
      if (error !== "cancel") ElMessage.error(error instanceof ApiError ? error.message : "投影素材上传失败");
    } finally {
      savingIcon.value = false;
    }
    return;
  }
  const persist = (dataUrl) => {
    const result = store.saveIconAsset({
      originalName: iconForm.value.originalName,
      name: iconForm.value.name,
      dataUrl,
    });
    if (!result.ok) return ElMessage.warning(result.message);
    iconDialog.value = false;
    ElMessage.success(
      iconDialogMode.value === "create" ? "投影素材已新增" : "投影素材已更新",
    );
  };
  if (!iconFile.value) return persist("");
  const reader = new FileReader();
  reader.onload = () => persist(String(reader.result || ""));
  reader.readAsDataURL(iconFile.value);
}
function viewIcon(item) {
  iconPreview.value = item;
  iconViewDialog.value = true;
}
const deleteDialog = ref(false),
  deleteTarget = ref(null);
function openDelete(type, id, name) {
  deleteTarget.value = { type, id, name };
  deleteDialog.value = true;
}
const references = computed(() => {
  const t = deleteTarget.value,
    task = store.state.task;
  if (!t) return { current: 0, running: 0, history: 0 };
  if (t.type === "processes")
    return {
      current: store.state.processFiles.some((file) => file.id === t.id)
        ? 1
        : 0,
      running: task?.status === "进行中" && task?.processId === t.id ? 1 : 0,
      history: task?.status === "已完成" && task?.processId === t.id ? 1 : 0,
    };
  if (t.type === "videos") {
    const uses = store.state.processFiles
      .flatMap((file) => file.steps || [])
      .filter((s) => s.videoName === t.name).length;
    const snapshots = (task?.processSnapshot || []).filter(
      (s) => s.videoName === t.name,
    ).length;
    return {
      current: uses,
      running: task?.status === "进行中" ? snapshots : 0,
      history: task?.status === "已完成" ? snapshots : 0,
    };
  }
  if (t.type === "icons") {
    const count = (elements) =>
      (elements || []).filter(
        (item) =>
          item.type === "icon" &&
          (item.iconName === t.id || item.icon === t.id),
      ).length;
    const current =
      store.state.templates.reduce(
        (sum, item) => sum + count(item.elements),
        0,
      ) +
      Object.values(store.state.stepProjection).reduce(
        (sum, item) => sum + count(item.elements),
        0,
      ) +
      store.state.processFiles.reduce(
        (sum, file) =>
          sum +
          Object.values(file.stepProjection || {}).reduce(
            (stepSum, projection) => stepSum + count(projection.elements),
            0,
          ),
        0,
      );
    const snapshot =
      (task?.processSnapshot || []).reduce(
        (sum, step) => sum + count(step.projection?.elements),
        0,
      ) +
      Object.values(task?.projection || {}).reduce(
        (sum, item) => sum + count(item?.elements),
        0,
      );
    return {
      current,
      running: task?.status === "进行中" ? snapshot : 0,
      history: task?.status === "已完成" ? snapshot : 0,
    };
  }
  return {
    current: 0,
    running: task?.status === "进行中" ? 1 : 0,
    history: task?.status === "已完成" ? 1 : 0,
  };
});
async function confirmDelete() {
  const t = deleteTarget.value;
  if (!t) return ElMessage.warning("未选择要删除的资源");
  if (apiMode && t.type === "people") {
    const person = store.state.people.find(item => String(item.id) === String(t.id));
    if (!person) return ElMessage.warning("人员不存在，请刷新后重试");
    try {
      await api.deleteUser(person.id, person.rowVersion);
      store.state.people = store.state.people.filter(item => String(item.id) !== String(person.id));
      deleteDialog.value = false;
      deleteTarget.value = null;
      return ElMessage.success("人员已删除，登录会话已失效");
    } catch (error) {
      return ElMessage.error(error instanceof ApiError ? error.message : "删除人员失败，请稍后重试");
    }
  }
  if (apiMode && t.type === "videos") {
    const video = store.state.videos.find(item => String(item.id) === String(t.id));
    if (!video) return ElMessage.warning("视频不存在，请刷新后重试");
    try {
      await api.deleteVideo(video.id, video.rowVersion);
      store.state.videos = store.state.videos.filter(item => String(item.id) !== String(video.id));
      deleteDialog.value = false;
      deleteTarget.value = null;
      return ElMessage.success("视频已删除，重新上传同名文件时可选择替换");
    } catch (error) {
      return ElMessage.error(error instanceof ApiError ? error.message : "删除视频失败，请稍后重试");
    }
  }
  if (apiMode && t.type === "icons") {
    const icon = store.state.customIcons.find(item => String(item.id) === String(t.id));
    if (!icon) return ElMessage.warning("投影素材不存在，请刷新后重试");
    try {
      await api.deleteIcon(icon.id, icon.rowVersion);
      store.state.customIcons = store.state.customIcons.filter(item => String(item.id) !== String(icon.id));
      deleteDialog.value = false;
      deleteTarget.value = null;
      return ElMessage.success("投影素材已删除，重新上传同名文件时可恢复");
    } catch (error) {
      return ElMessage.error(error instanceof ApiError ? error.message : "删除投影素材失败，请稍后重试");
    }
  }
  if (apiMode && t.type === "templates") {
    const template = store.state.templates.find(item => String(item.id) === String(t.id));
    if (!template) return ElMessage.warning("投影模板不存在，请刷新后重试");
    try {
      await api.deleteTemplate(template.id, template.rowVersion);
      store.state.templates = store.state.templates.filter(item => String(item.id) !== String(template.id));
      deleteDialog.value = false;
      deleteTarget.value = null;
      return ElMessage.success("投影模板已删除，历史任务快照不受影响");
    } catch (error) {
      return ElMessage.error(error instanceof ApiError ? error.message : "删除投影模板失败，请稍后重试");
    }
  }
  if (apiMode && t.type === "processes") {
    const process = store.state.processFiles.find(item => String(item.id) === String(t.id));
    if (!process) return ElMessage.warning("工艺文件不存在，请刷新后重试");
    const expectedVersion = Number(process.rowVersion);
    if (!Number.isInteger(expectedVersion)) return ElMessage.warning("工艺版本信息无效，请刷新页面后重试");
    try {
      await api.deleteProcess(process.id, expectedVersion);
      store.replaceServerProcesses(await api.processes());
      deleteDialog.value = false;
      deleteTarget.value = null;
      return ElMessage.success("工艺文件已删除，历史任务快照不受影响");
    } catch (error) {
      return ElMessage.error(error instanceof ApiError ? error.message : "删除工艺文件失败，请稍后重试");
    }
  }
  const result = store.deleteResource(t.type, t.id);
  if (!result.ok) {
    deleteDialog.value = false;
    return ElMessage.warning(result.message);
  }
  deleteDialog.value = false;
  deleteTarget.value = null;
  ElMessage.success("已删除，历史快照仍可读取");
}
const personDialog = ref(false),
  editing = ref(null),
  personForm = ref({});
function openPerson(p) {
  editing.value = p?.id || null;
  personForm.value = p
    ? { ...p }
    : {
        no: "",
        name: "",
        role: "操作员",
        dept: "总装车间",
        phone: "",
        status: "在岗",
        password: "000000",
      };
  personDialog.value = true;
}
async function savePerson() {
  const form = personForm.value,
    no = String(form.no || "").trim();
  if (!no || !form.name || !form.phone)
    return ElMessage.warning("请填写工号、姓名和手机号");
  const duplicate = store.state.people.some(
    (p) =>
      p.no === no &&
      p.id !== editing.value &&
      !store.state.deleted.people.includes(p.id),
  );
  if (duplicate) return ElMessage.error("工号已存在，不能重复");
  if (apiMode) {
    try {
      const payload = {
        name: String(form.name).trim(), roleCode: roleCodes[form.role],
        department: String(form.dept || "").trim(), phone: String(form.phone || "").trim(),
        statusCode: form.status === "在岗" ? 1 : 0,
      };
      let saved;
      if (editing.value) {
        saved = await api.updateUser(editing.value, { ...payload, expectedVersion: form.rowVersion });
        const index = store.state.people.findIndex(item => String(item.id) === String(editing.value));
        if (index >= 0) store.state.people[index] = userFromApi(saved);
      } else {
        saved = await api.createUser({ ...payload, no, password: String(form.password || "000000") });
        store.state.people.push(userFromApi(saved));
      }
      personDialog.value = false;
      return ElMessage.success("人员已保存，可使用工号和初始密码登录");
    } catch (error) {
      return ElMessage.error(error instanceof ApiError ? error.message : "保存人员失败，请稍后重试");
    }
  }
  if (editing.value) {
    const i = store.state.people.findIndex((p) => p.id === editing.value);
    store.state.people[i] = {
      ...store.state.people[i],
      ...form,
      no,
      password: store.state.people[i].password,
    };
  } else
    store.state.people.push({
      id: `U-${Date.now()}`,
      ...form,
      no,
      password: form.password || "000000",
    });
  personDialog.value = false;
  ElMessage.success("人员已保存");
}
async function resetPassword(p) {
  try {
    await ElMessageBox.confirm(`确认重置「${p.name}」的密码？`, "重置密码", {
      confirmButtonText: "确认重置",
      cancelButtonText: "取消",
    });
    const password = `MES@${String(p.no).replace(/\D/g, "").slice(-4).padStart(4, "0")}`;
    if (apiMode) {
      await api.resetUserPassword(p.id, { newPassword: password, expectedVersion: p.rowVersion });
      const current = store.state.people.find(item => String(item.id) === String(p.id));
      if (current) current.rowVersion += 1;
    } else store.resetPersonPassword(p.id);
    ElMessage.success(`密码已重置，新密码：${password}`);
  } catch {}
}
function active(path) {
  return (
    route.path === path ||
    (path === "/admin/processes" && route.path.startsWith(path)) ||
    (path === "/admin/projector/templates" && route.path.startsWith(path)) ||
    (path === "/admin/tasks" && route.path.startsWith(path))
  );
}
// 新建投影模板：先弹出名称输入框；留空则按「新建模板 + 生成时间 + 序号」自动命名
const newTemplateDialog = ref(false),
  newTemplateName = ref(""),
  creatingTemplate = ref(false);
function defaultNewTemplateName() {
  const d = new Date(),
    pad = (n) => String(n).padStart(2, "0");
  const ts = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
  const seq = String(store.state.templates.length + 1).padStart(2, "0");
  return `新建模板 ${ts} ${seq}`;
}
function openNewTemplate() {
  newTemplateName.value = "";
  newTemplateDialog.value = true;
}
async function confirmNewTemplate() {
  if (creatingTemplate.value) return;
  const name = newTemplateName.value.trim() || defaultNewTemplateName();
  creatingTemplate.value = true;
  try {
    let id;
    if (apiMode) {
      const created = templateFromApi(await api.createTemplate({ name, description: "", elements: [] }));
      const index = store.state.templates.findIndex(item => String(item.id) === String(created.id));
      if (index >= 0) store.state.templates[index] = created;
      else store.state.templates.push(created);
      id = created.id;
    } else id = store.addTemplate(name);
    newTemplateDialog.value = false;
    newTemplateName.value = "";
    await router.push(`/admin/projector/templates/${id}`);
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : "新建投影模板失败，请稍后重试");
  } finally {
    creatingTemplate.value = false;
  }
}
// 重命名既有投影模板：卡片操作区「编辑模板 / 重命名 / 删除」中间项
const renameDialog = ref(false),
  renameTarget = ref(null),
  renameName = ref("");
function openRename(t) {
  renameTarget.value = t;
  renameName.value = t.name;
  renameDialog.value = true;
}
async function confirmRename() {
  const name = renameName.value.trim();
  if (!name) return ElMessage.warning("请输入模板名称");
  const target = renameTarget.value;
  if (target && target.name !== name) {
    try {
      if (apiMode) {
        const saved = templateFromApi(await api.updateTemplate(target.id, {
          name,
          description: target.description || "",
          elements: target.elements || [],
          expectedVersion: target.rowVersion,
        }));
        const index = store.state.templates.findIndex(item => String(item.id) === String(saved.id));
        if (index >= 0) store.state.templates[index] = saved;
      } else {
        target.name = name;
        store.persist();
      }
    } catch (error) {
      return ElMessage.error(error instanceof ApiError ? error.message : "重命名投影模板失败，请稍后重试");
    }
  }
  renameDialog.value = false;
  renameTarget.value = null;
  ElMessage.success("模板名称已更新");
}
// 右上角登录人按钮：档案与改密在共享弹窗 UserProfileDialog 中展示
const profileDialog = ref(false);
const statsFocusMode = ref(false);
const currentPerson = computed(
  () =>
    store.state.people.find((p) => p.id === store.state.currentUserId) || null,
);
function toggleStatsFocusMode() {
  if (section.value === "stats") statsFocusMode.value = !statsFocusMode.value;
}
function exitStatsFocusMode() {
  statsFocusMode.value = false;
}
function handleStatsFocusKeydown(event) {
  if (event.key === "Escape" && statsFocusMode.value) exitStatsFocusMode();
}
watch(section, (value) => {
  if (value !== "stats") exitStatsFocusMode();
  if (value === "stats") refreshProductionStatistics();
  else stopProductionStatisticsPolling();
});
watch(statsDateRange, () => {
  if (section.value === "stats") refreshProductionStatistics();
}, { deep: true });
watch([section, () => route.params.id], ([nextSection, id]) => {
  if (!adminDataReady.value) return;
  if (nextSection === "audit") loadAuditTask(id);
  else auditTaskDetail.value = null;
});
onMounted(async () => {
  window.addEventListener("keydown", handleStatsFocusKeydown);
  if (!apiMode) {
    await loadTaskPage();
    adminDataReady.value = true;
    if (section.value === "audit") loadAuditTask(route.params.id);
    return;
  }
  try {
    // Pinia 会恢复上一次的界面状态，但登录凭证只保存在 sessionStorage。
    // 刷新后先校验会话，避免把 401 误报成“无法加载视频资源”。
    await api.me();
  } catch (error) {
    try { await endSession(); } catch {}
    store.logout();
    router.replace("/login");
    ElMessage.warning("登录已过期，请重新登录");
    return;
  }
  try {
    const [tree, videoPage, serverTaskPage, serverTaskModels, icons, serverProcesses, templates, workstations] = await Promise.all([api.categories(), api.videos({ page: 1, pageSize: 100 }), api.tasks({ page: 1, pageSize: taskPageSize.value }), api.taskModels(), api.icons(), api.processes(), api.templates(), api.workstations()]);
    // 后端只有一个根分类时，某些代理会将单元素数组解包为对象；这里统一按数组处理，
    // 避免管理端在首次加载资源时因 flatMap 不存在而中断。
    const flatten = nodes => (Array.isArray(nodes) ? nodes : []).flatMap(node => [{ ...node, id: String(node.id), parentId: node.parentId == null ? null : String(node.parentId) }, ...flatten(node.children || [])]);
    store.state.videoCategories = flatten(Array.isArray(tree) ? tree : tree ? [tree] : []);
    store.state.videos = (videoPage.items || []).map(item => ({ ...item, id: String(item.id), categoryId: String(item.categoryId), sessionPreview: false }));
    // The API is authoritative for server-managed projection assets. Clear stale mock-mode deletion markers so restored assets remain visible.
    store.state.customIcons = (icons || []).map(item => ({ ...item, dataUrl: item.url }));
    store.state.deleted.icons = [];
    store.state.templates = (templates || []).map(templateFromApi);
    store.state.workstations = (workstations || []).map(workstationFromApi);
    taskModelOptions.value = Array.isArray(serverTaskModels) ? serverTaskModels : [];
    setServerTaskPage(serverTaskPage);
    // 首次升级时，将此浏览器此前已导入、而服务端尚不存在的真实工艺逐项迁移；
    // 即使服务端已有其它工艺，也不能遗漏后来导入的工艺文件。
    const localImports = store.state.processFiles.filter(item => !item.isDemo);
    const processes = Array.isArray(serverProcesses) ? serverProcesses : serverProcesses ? [serverProcesses] : [];
    if (processes.length) store.replaceServerProcesses(processes);
    const existingModels = new Set(processes.map(item => item.model));
    for (const item of localImports) {
      if (!existingModels.has(item.model)) {
        const created = await api.createProcess({ model: item.model, title: item.title, version: item.version, sourceWorkbook: item.sourceWorkbook, sourceDocument: item.sourceDocument, steps: item.steps });
        store.replaceServerProcess(created);
        existingModels.add(item.model);
      }
    }
    if (isSuperAdmin.value) {
      const users = await api.users({ page: 1, pageSize: 100 });
      store.state.people = (users.items || []).map(userFromApi);
      store.state.deleted.people = [];
    }
    adminDataReady.value = true;
    if (section.value === "stats") {
      await loadProductionStatistics();
      scheduleProductionStatisticsPolling();
    }
    if (section.value === "audit") loadAuditTask(route.params.id);
  } catch (error) {
    ElMessage.error(error?.message || "管理端资源加载失败，请稍后重试")
  }
});
onBeforeUnmount(() => {
  window.removeEventListener("keydown", handleStatsFocusKeydown);
  stopProductionStatisticsPolling();
});
</script>
<template>
  <div class="admin-shell" :class="{ 'is-stats-focus': statsFocusMode }">
    <aside class="admin-sidebar">
      <div class="admin-brand">
        <img
          class="brand-logo"
          src="/syncdot-logo.svg"
          alt="SyncDot 骤点科技"
        />
        <div>
          <strong>油压减振器装配作业系统</strong><small>DISP-MES</small>
        </div>
      </div>
      <nav>
        <div v-for="group in menus" :key="group.group" class="nav-group">
          <span class="nav-heading">{{ group.group }}</span
          ><button
            v-for="item in group.items.filter(permitted)"
            :key="item[0]"
            class="nav-item"
            :class="{
              active: active(item[0]),
              disabled: menuDisabled(item[0]),
            }"
            @click="clickMenu(item[0])"
          >
            <component :is="item[2]" /><span>{{ item[1] }}</span
            ><ArrowRight />
          </button>
        </div>
      </nav>
    </aside>
    <main class="admin-main" :class="{ 'is-stats-page': section === 'stats' }">
      <header class="admin-topbar">
        <div class="admin-breadcrumb">管理端 <ArrowRight /> {{ title }}</div>
        <div class="admin-topbar-right">
          <button class="topbar-user" @click="profileDialog = true">
            <UserFilled /><span>{{
              currentPerson?.name || "管理员"
            }}</span></button
          ><button
            class="plain-icon"
            title="退出登录"
            aria-label="退出登录"
            @click="logout"
          >
            <SwitchButton />
          </button>
        </div>
      </header>
      <div class="admin-content">
        <div class="admin-page-head">
          <div>
            <div v-if="section === 'stats'" class="stats-page-title"><span><DataAnalysis /></span><h1>{{ title }}</h1></div>
            <h1 v-else>{{ title }}</h1>
            <p v-if="section === 'detail'">
              编辑标题、等级、视频和最多三组参数；保存不改写在制任务。
            </p>
          </div>
          <div class="admin-head-actions">
            <div v-if="section === 'stats'" class="stats-head-filters">
              <div class="stats-preset-buttons">
                <button :class="{ active: statsPreset === '7d' }" @click="setStatsRange('7d')">近7日</button>
                <button :class="{ active: statsPreset === '30d' }" @click="setStatsRange('30d')">近30日</button>
              </div>
              <el-date-picker
                v-model="statsDateRange"
                type="daterange"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                value-format="YYYY-MM-DD"
                :clearable="false"
                @change="changeStatsRange"
              />
            </div>
            <button
              v-if="section === 'stats'"
              class="ghost-button stats-focus-button"
              :aria-pressed="statsFocusMode"
              @click="toggleStatsFocusMode"
            >
              <ScaleToOriginal v-if="statsFocusMode" />
              <FullScreen v-else />
              {{ statsFocusMode ? "退出全屏" : "全屏查看" }}</button
            ><button
              v-if="section === 'processes'"
              class="ghost-button"
              @click="download"
            >
              <Download /> 下载模板</button
            ><button
              v-if="section === 'processes'"
              class="admin-primary"
              @click="importDialog = true"
            >
              <Upload /> 导入工艺 Excel</button
            ><button
              v-if="section === 'videos'"
              class="admin-primary"
              @click="openUploadDialog"
            >
              <Upload /> 批量上传视频</button
            ><button
              v-if="section === 'users' && isSuperAdmin"
              class="admin-primary"
              @click="openPerson()"
            >
              <Plus /> 新增人员</button
            ><button
              v-if="
                ['detail', 'preview', 'templateDetail', 'audit'].includes(
                  section,
                )
              "
              class="ghost-button"
              @click="router.back()"
            >
              <ArrowLeft /> 返回列表
            </button>
          </div>
        </div>
        <template v-if="section === 'processes'"
          ><div class="admin-metrics sd-swatch-grid">
            <div class="sd-swatch sd-swatch--blue">
              <div class="sd-swatch__title">有效型号</div>
              <div class="sd-swatch__val">{{ processRows.length }}</div>
              <div class="sd-swatch__desc">工艺文件</div>
            </div>
            <div class="sd-swatch sd-swatch--cyan">
              <div class="sd-swatch__title">重要工序</div>
              <div class="sd-swatch__val">
                {{ store.steps.filter((step) => step.importance).length }}
              </div>
              <div class="sd-swatch__desc">当前工艺</div>
            </div>
          </div>
          <section class="admin-card">
            <div class="table-toolbar">
              <strong>工艺列表</strong
              ><span>{{ processRows.length }} 份有效工艺</span>
            </div>
            <el-table :data="processRows" class="mes-table"
              ><el-table-column
                prop="model"
                label="减振器型号"
                min-width="180"
              /><el-table-column
                prop="title"
                label="工艺标题"
                min-width="220"
              /><el-table-column
                prop="count"
                label="步骤数"
                width="100"
              /><el-table-column
                prop="sourceWorkbook"
                label="来源文件"
                min-width="220"
              /><el-table-column label="状态" width="120"
                ><template #default="scope"
                  ><span
                    v-if="scope.row.id === store.state.processRecord.id"
                    class="selected-label"
                    >当前工艺</span
                  ><span v-else>可选</span></template
                ></el-table-column
              ><el-table-column label="操作" width="190"
                ><template #default="scope"
                  ><button
                    class="table-action"
                    @click="openProcess(scope.row.id)"
                  >
                    查看编辑</button
                  ><button
                    class="table-action danger"
                    @click="
                      openDelete('processes', scope.row.id, scope.row.title)
                    "
                  >
                    删除
                  </button></template
                ></el-table-column
              ></el-table
            >
            <div class="table-caption">
              导入工艺会显示在此列表并自动切换为当前工艺；开始任务时会冻结完整工艺快照。
            </div>
          </section></template
        >
        <template v-else-if="section === 'detail'"
          ><div class="process-detail-grid">
            <aside class="admin-card step-list-card">
              <div class="table-toolbar">
                <strong>工艺步骤</strong
                ><span>{{ store.steps.length }} 项</span>
              </div>
              <el-input v-model="search" placeholder="搜索步骤" clearable />
              <div class="step-list">
                <button
                  v-for="s in filteredSteps"
                  :key="s.stepNo"
                  :class="{ active: selectedStepNo === s.stepNo }"
                  @click="selectedStepNo = s.stepNo"
                >
                  <span>{{ String(s.stepNo).padStart(2, "0") }}</span
                  ><strong>{{ s.title }}</strong
                  ><StarFilled
                    v-if="s.importance"
                    class="step-importance-star"
                    aria-label="重要工序"
                  />
                  ><small>工位{{ stationNo(s.stationId) }}</small>
                </button>
              </div>
            </aside>
            <div class="process-detail-main">
              <section class="admin-card step-detail-card">
                <div class="table-toolbar">
                  <strong>第 {{ selectedStepNo }} 步</strong
                  ><button class="table-action" @click="goPreview">
                    投影预览
                  </button>
                </div>
                <div class="step-edit-form">
                  <label>步骤标题<input v-model="stepForm.title" /></label
                  ><label class="step-content-editor"
                    >步骤内容<textarea
                      v-model="stepForm.content"
                      rows="4"
                      placeholder="填写操作步骤详情描述，可分行填写"
                    /></label
                  ><label
                    >所属工位<el-select v-model="stepForm.stationId"
                      ><el-option
                        v-for="station in store.state.workstations"
                        :key="station.id"
                        :label="station.name"
                        :value="station.id" /></el-select></label
                  ><label
                    >重要等级<el-select v-model="stepForm.importance"
                      ><el-option label="普通工序" :value="0" /><el-option
                        label="重要工序"
                        :value="1" /></el-select></label
                  ><label
                    >视频一级分类<el-select
                      :model-value="stepForm.videoParentId"
                      clearable
                      placeholder="先选择一级分类"
                      @update:model-value="selectStepVideoPrimary"
                      ><el-option
                        v-for="parent in videoCategoryTree"
                        :key="parent.id"
                        :label="parent.name"
                        :value="parent.id" /></el-select></label
                  ><label
                    >视频二级分类<el-select
                      :model-value="stepForm.videoCategoryId"
                      :disabled="!stepForm.videoParentId"
                      placeholder="再选择二级分类"
                      @update:model-value="selectStepVideoSecondary"
                      ><el-option
                        v-for="item in stepVideoSecondaries"
                        :key="item.id"
                        :label="item.name"
                        :value="item.id" /></el-select></label
                  ><label
                    >绑定视频<el-select
                      v-model="stepForm.videoName"
                      :disabled="!stepForm.videoCategoryId"
                      clearable
                      placeholder="最后选择视频"
                      ><el-option
                        v-for="video in stepVideoOptions"
                        :key="video.id"
                        :label="video.name"
                        :value="video.name" /></el-select
                  ></label>
                </div>
                <div class="parameter-edit-head">
                  <strong>工艺参数</strong
                  ><span>{{ stepForm.parameters?.length || 0 }} / 3 组</span
                  ><button class="table-action" @click="addParameter">
                    添加参数
                  </button>
                </div>
                <div
                  v-for="(p, i) in stepForm.parameters"
                  :key="i"
                  class="parameter-edit-list"
                >
                  <article>
                    <div class="parameter-edit-title">
                      <strong>参数 {{ i + 1 }}</strong
                      ><button
                        class="table-action danger"
                        @click="stepForm.parameters.splice(i, 1)"
                      >
                        删除
                      </button>
                    </div>
                    <div class="parameter-edit-grid">
                      <label>参数名称<input v-model="p.name" /></label
                      ><label
                        >参数类型<el-select v-model="p.type"
                          ><el-option label="手动输入" :value="0" /><el-option
                            label="单选"
                            :value="1" /><el-option
                            label="范围值"
                            :value="2" /></el-select></label
                      ><label v-if="Number(p.type) !== 1">{{ Number(p.type) === 2 ? '范围值（四段逗号分隔）' : '参考值' }}<input v-if="Number(p.type) === 2" v-model="p.referenceValue" placeholder="例如：(>30),(=30),(=70),(<70)" /><input v-else v-model="p.referenceValue" /><small v-if="Number(p.type) === 2">依次填写下限排除、下限包含、上限包含、上限排除；无边界写 ()</small></label
                      ><label
                        v-if="Number(p.type) === 1"
                        class="parameter-options"
                        >单选项（每行一项，第一项为合格参考项，至少两项）<textarea
                          v-model="p.optionsText"
                          rows="3"
                        />
                      </label>
                    </div>
                  </article>
                </div>
                <div class="step-save-row">
                  <button class="admin-primary" @click="saveStep">
                    保存步骤编辑
                  </button>
                </div>
              </section>
              <section class="admin-card snapshot-card">
                <div class="table-toolbar"><strong>步骤投影配置</strong></div>
                <div class="template-picker">
                  <el-select
                    v-model="selectedTemplateId"
                    placeholder="选择投影模板"
                    ><el-option
                      v-for="t in activeTemplates"
                      :key="t.id"
                      :label="t.name"
                      :value="t.id" /></el-select
                  ><button class="ghost-button" @click="applyTemplate">
                    复制模板到步骤
                  </button>
                </div>
                <ProjectorEditor
                  :key="`step-${selectedStepNo}`"
                  mode="step"
                  :step-no="selectedStepNo"
                />
              </section>
            </div></div
        ></template>
        <template v-else-if="section === 'preview'"
          ><section class="admin-card full-preview">
            <ProjectionCanvas
              :elements="
                store.projectionElements(previewStep.stepNo)
              "
              :step="previewStep"
              :model="store.state.processRecord.model"
            /></section
        ></template>
        <template v-else-if="section === 'videos'"
          ><section class="admin-card">
            <div class="video-resource-layout">
              <aside class="video-category-pane">
                <div class="video-category-head">
                  <strong>视频分类</strong
                  ><button class="table-action" @click="openCategoryDialog()">
                    新增一级
                  </button>
                </div>
                <button
                  class="video-category-row all"
                  :class="{ active: category === 'ALL' }"
                  @click="category = 'ALL'"
                >
                  全部视频 <span>{{ activeVideos.length }}</span>
                </button>
                <div
                  v-for="parent in videoCategoryTree"
                  :key="parent.id"
                  class="video-category-branch"
                >
                  <div
                    class="video-category-row parent"
                    :class="{ active: category === parent.id }"
                  >
                    <button
                      v-if="parent.children.length"
                      class="video-category-toggle"
                      :aria-label="
                        collapsedVideoCategories[parent.id]
                          ? '展开二级分类'
                          : '收起二级分类'
                      "
                      @click.stop="toggleVideoCategory(parent.id)"
                    >
                      <ArrowRight
                        v-if="collapsedVideoCategories[parent.id]"
                      /><ArrowDown v-else /></button
                    ><button
                      class="video-category-name"
                      @click="category = parent.id"
                    >
                      {{ parent.name }}</button
                    ><span class="video-category-actions"
                      ><button
                        title="新增二级分类"
                        @click.stop="openCategoryDialog(parent.id)"
                      >
                        +</button
                      ><button
                        title="重命名"
                        @click.stop="openCategoryRename(parent)"
                      >
                        编辑</button
                      ><button
                        title="删除"
                        @click.stop="removeVideoCategory(parent)"
                      >
                        删除
                      </button></span
                    >
                  </div>
                  <div
                    v-if="!collapsedVideoCategories[parent.id]"
                    v-for="child in parent.children"
                    :key="child.id"
                    class="video-category-row child"
                    :class="{ active: category === child.id }"
                  >
                    <button @click="category = child.id">
                      {{ child.name }}</button
                    ><span class="video-category-actions"
                      ><button
                        title="重命名"
                        @click.stop="openCategoryRename(child)"
                      >
                        编辑</button
                      ><button
                        title="删除"
                        @click.stop="removeVideoCategory(child)"
                      >
                        删除
                      </button></span
                    >
                  </div>
                </div>
              </aside>
              <div class="video-list-panel">
                <div class="table-toolbar">
                  <strong>视频列表</strong
                  ><el-input
                    v-model="search"
                    class="video-search"
                    placeholder="搜索视频名称"
                    clearable
                  />
                </div>
                <el-table :data="pagedVideos" class="mes-table"
                  ><el-table-column
                    prop="name"
                    label="视频名称"
                    min-width="220"
                  /><el-table-column label="一级分类" width="150"
                    ><template #default="scope">{{
                      videoCategoryPath(scope.row.categoryId).primary
                    }}</template></el-table-column
                  ><el-table-column label="二级分类" width="170"
                    ><template #default="scope">{{
                      videoCategoryPath(scope.row.categoryId).secondary
                    }}</template></el-table-column
                  ><el-table-column
                    prop="duration"
                    label="时长"
                    width="120"
                  /><el-table-column
                    prop="batch"
                    label="上传批次"
                    min-width="150"
                  /><el-table-column label="操作" width="150"
                    ><template #default="scope"
                      ><button
                        class="table-action"
                        @click="viewVideo(scope.row)"
                      >
                        查看</button
                      ><button
                        class="table-action danger"
                        @click="
                          openDelete('videos', scope.row.id, scope.row.name)
                        "
                      >
                        删除
                      </button></template
                    ></el-table-column
                  ></el-table
                ><div class="video-pagination">
                  <el-pagination
                    v-model:current-page="videoPage"
                    v-model:page-size="videoPageSize"
                    background
                    :page-sizes="[10, 20, 50, 100]"
                    layout="total, sizes, prev, pager, next"
                    :total="filteredVideos.length"
                  />
                </div>
              </div>
            </div>
          </section></template
        >
        <template v-else-if="section === 'icons'"
          ><div class="asset-note">
            <InfoFilled />
            图标仅用于投影模板和步骤投影，不用于系统菜单和操作按钮。
          </div>
          <div class="asset-toolbar">
            <span>支持 PNG 素材的新增、查看、编辑和逻辑删除。</span
            ><button class="admin-primary" @click="openIconDialog()">
              <Plus /> 新增投影素材
            </button>
          </div>
          <div class="sd-product-grid icon-asset-grid">
            <article
              v-for="item in allIcons"
              :key="item.id || item.name"
              class="sd-product-card"
            >
              <div class="sd-product-card__media">
                <img :src="item.dataUrl" :alt="item.name" />
              </div>
              <div class="sd-product-card__eyebrow">{{ item.source }}</div>
              <div class="sd-product-card__title">{{ item.name }}</div>
              <div class="sd-product-card__price">PNG 投影素材</div>
              <div class="sd-product-card__actions">
                <button class="table-action" @click="viewIcon(item)">
                  查看</button
                ><button class="table-action" @click="openIconDialog(item)">
                  编辑</button
                ><button
                  v-if="!item.builtIn"
                  class="table-action danger"
                  @click="openDelete('icons', item.id, item.name)"
                >
                  删除
                </button>
              </div>
            </article>
          </div></template
        >
        <template v-else-if="section === 'templates'"
          ><div class="admin-head-actions template-head-actions">
            <button class="admin-primary" @click="openNewTemplate">
              <Plus /> 新建模板
            </button>
          </div>
          <div class="template-grid">
            <article
              v-for="t in activeTemplates"
              :key="t.id"
              class="admin-card template-card"
            >
              <div class="template-preview">
                <ProjectionCanvas
                  :elements="t.elements"
                  :step="store.steps[0]"
                  :model="store.state.processRecord.model"
                />
              </div>
              <div class="template-body">
                <span
                  class="selected-label"
                  :class="{ 'is-custom': !t.isDefault }"
                  >{{ t.isDefault ? "系统默认模板" : "自定义模板" }}</span
                >
                <h2>{{ t.name }}</h2>
                <div class="template-actions">
                  <button
                    class="table-action"
                    @click="router.push(`/admin/projector/templates/${t.id}`)"
                  >
                    编辑模板</button
                  ><button class="table-action" @click="openRename(t)">
                    重命名</button
                  ><button
                    v-if="!t.isDefault"
                    class="table-action danger"
                    @click="openDelete('templates', t.id, t.name)"
                  >
                    删除
                  </button>
                </div>
              </div>
            </article>
          </div></template
        >
        <template v-else-if="section === 'templateDetail'"
          ><ProjectorEditor
            v-if="currentTemplate"
            :key="currentTemplate.id"
            mode="template"
            :template-id="currentTemplate.id"
        /></template>
        <template v-else-if="section === 'tasks'"
          ><section class="admin-card">
            <div class="table-toolbar">
              <strong>任务记录</strong
              ><span>点击审计记录查看按工序展开的工位操作明细</span>
            </div>
            <div class="task-filter-bar">
              <div class="task-filter-controls">
                <el-date-picker
                  v-model="taskStartedRange"
                  type="daterange"
                  range-separator="至"
                  start-placeholder="开始日期"
                  end-placeholder="结束日期"
                  value-format="YYYY-MM-DD"
                  clearable
                />
                <el-select
                  v-model="taskModel"
                  class="task-model-filter"
                  clearable
                  filterable
                  placeholder="全部型号"
                >
                  <el-option
                    v-for="model in taskFilterModelOptions"
                    :key="model"
                    :label="model"
                    :value="model"
                  />
                </el-select>
                <button class="admin-primary" type="button" @click="applyTaskFilters">查询</button>
                <button class="ghost-button" type="button" @click="resetTaskFilters">重置</button>
              </div>
            </div>
            <el-table v-loading="taskLoading" :data="displayedTaskRows" class="mes-table"
              ><el-table-column
                prop="id"
                label="任务编号"
                min-width="180"
              /><el-table-column
                prop="productCode"
                label="产品编码"
                min-width="180"
              /><el-table-column
                prop="model"
                label="产品型号"
                min-width="160"
              /><el-table-column
                label="开始时间"
                min-width="180"
                ><template #default="scope">{{ formatTaskDateTime(scope.row.startedAt) }}</template
              ></el-table-column
              ><el-table-column
                prop="steelNo"
                label="钢印编号"
                min-width="150"
              /><el-table-column
                prop="status"
                label="状态"
                width="100"
              /><el-table-column label="操作" width="120" fixed="right"
                ><template #default="scope"
                  ><button
                    class="table-action"
                    @click="openTaskAudit(scope.row.id)"
                  >
                    审计记录
                  </button></template
              ></el-table-column
              ></el-table
            ><div class="task-pagination">
              <el-pagination
                :current-page="taskPage"
                :page-size="taskPageSize"
                :page-sizes="[10, 20, 50, 100]"
                :total="taskTotal"
                layout="total, sizes, prev, pager, next"
                @current-change="changeTaskPage"
                @size-change="changeTaskPageSize"
              />
            </div>
          </section></template
        >
        <template v-else-if="section === 'audit'"
          ><section class="admin-card">
            <div class="table-toolbar">
              <strong>工位操作明细</strong
              ><span
                >{{ auditTask?.id || "未找到任务" }} ·
                {{ taskStepDetails.length }} 个工序<template
                  v-if="steelNoAuditDetails.length"
                >
                  · {{ steelNoAuditDetails.length }} 次钢印号修改</template
                ></span
              >
            </div>
            <div class="asset-note">
              <InfoFilled />
              工序按任务开始时冻结的工艺快照展开；每个工序一行。钢印号修改会额外记录操作员、工位及修改前后的编号。
            </div>
            <el-table :data="taskOperationDetails" class="mes-table"
              ><el-table-column
                prop="stepNo"
                label="步骤号"
                width="100" /><el-table-column
                prop="title"
                label="步骤标题"
                min-width="220" /><el-table-column
                prop="stationName"
                label="所属工位"
                width="140" /><el-table-column
                prop="result"
                label="操作结果"
                width="150" /><el-table-column
                prop="operatorName"
                label="操作员"
                width="140" /><el-table-column
                label="参数"
                min-width="230"
                ><template #default="scope"
                  ><div class="task-parameter-list">
                    <div
                      v-for="(parameter, index) in scope.row.parameters"
                      :key="index"
                      class="task-parameter-line"
                    >
                      {{ parameter }}
                    </div>
                  </div></template
                ></el-table-column
              ><el-table-column
                prop="time"
                label="操作时间"
                min-width="190" /><el-table-column
                prop="reason"
                label="说明"
                min-width="160"
            /></el-table></section
        ></template>
        <template v-else-if="section === 'workstations'"
          ><section class="admin-card">
            <div class="table-toolbar">
              <strong>工位配置</strong
              ><span
                >自动获取时按产品编码读取最新绑定；手动输入时填写型号和钢印编号。</span
              ><button class="admin-primary" @click="addWorkstation">
                <Plus /> 新增工位
              </button>
            </div>
            <el-table :data="store.state.workstations" class="mes-table"
              ><el-table-column
                prop="stationNo"
                label="编号"
                width="100"
              /><el-table-column label="工位名称" min-width="220"
                ><template #default="scope"
                  ><input
                    class="table-inline-input"
                    :value="scope.row.name"
                    @change="
                      saveWorkstation(scope.row, {
                        ...scope.row,
                        name: $event.target.value,
                      })
                    " /></template></el-table-column
              ><el-table-column label="识别模式" min-width="220"
                ><template #default="scope"
                  ><el-select
                    :model-value="scope.row.captureMode"
                    @update:model-value="
                      (value) =>
                        saveWorkstation(scope.row, {
                          ...scope.row,
                          captureMode: value,
                        })
                    "
                    ><el-option
                      label="手动输入型号和钢印编号"
                      value="manual" /><el-option
                      label="自动获取最新绑定"
                      value="auto" /></el-select></template></el-table-column
              ><el-table-column label="说明" min-width="260"
                ><template #default="scope"
                  ><span>{{
                    scope.row.captureMode === "manual"
                      ? "扫码后选择产品型号并填写钢印编号"
                      : "扫码后读取产品编码的最新绑定记录"
                  }}</span></template
                ></el-table-column
              ><el-table-column label="操作" width="120" fixed="right"
                ><template #default="scope"
                  ><button
                    v-if="scope.row.stationNo !== 1"
                    class="table-action danger"
                    @click="removeWorkstation(scope.row)"
                  >
                    删除</button
                  ><span v-else class="table-caption">默认工位</span></template
                ></el-table-column
              ></el-table
            >
            <div class="table-caption">
              工位 1
              为默认归属，不能删除。删除其它工位会将现有工艺步骤改绑到工位
              1；若在制任务冻结快照引用该工位，则需完成任务后再删除。
            </div>
          </section></template
        >
        <template v-else-if="section === 'users'">
          <section v-if="isSuperAdmin" class="admin-card">
            <div class="table-toolbar">
              <strong>人员列表</strong
              ><span>{{ activePeople.length }} 名人员</span>
            </div>
            <el-table :data="activePeople" class="mes-table"
              ><el-table-column
                prop="no"
                label="工号"
                width="140"
              /><el-table-column
                prop="name"
                label="姓名"
                width="120"
              /><el-table-column
                prop="role"
                label="角色"
                width="150"
              /><el-table-column
                prop="dept"
                label="部门"
                min-width="150"
              /><el-table-column
                prop="phone"
                label="手机号"
                min-width="150"
              /><el-table-column
                prop="status"
                label="状态"
                width="100"
              /><el-table-column label="操作" width="220"
                ><template #default="scope"
                  ><button class="table-action" @click="openPerson(scope.row)">
                    编辑</button
                  ><button
                    class="table-action"
                    @click="resetPassword(scope.row)"
                  >
                    重置密码</button
                  ><button
                    class="table-action danger"
                    @click="openDelete('people', scope.row.id, scope.row.name)"
                  >
                    删除
                  </button></template
                ></el-table-column
              ></el-table
            >
          </section>
          <section v-else class="admin-card no-permission">
            <div class="empty-screen">
              <WarningFilled /><strong>暂无权限</strong
              ><span>人员管理仅超级管理员可访问</span>
            </div>
          </section>
        </template>
        <template v-else-if="section === 'stats'">
          <div v-loading="productionStatisticsLoading" class="production-stats-dashboard">
            <section class="production-line-card">
              <div class="production-line-scene" :class="{ 'has-station-hover-image': stationHoverOverlay.visible }" :style="stationHoverOverlay.style">
                <img class="production-line-connector" src="/production-statistics/production-line-connector.png" alt="" aria-hidden="true" />
                <div v-for="tile in liveStationTiles.filter(item => item.id <= 10)" :key="tile.id" class="production-line-zone" :class="{ 'is-status-alert': tile.status === 'busy' || tile.status === 'congested' }" :style="productionLineStyle(tile)" @mouseenter="handleStationEnter(tile.id)" @mouseleave="handleStationLeave(tile.id)">
                  <button type="button" class="production-line-hit" :aria-label="`${tile.name}，${tile.label}，${tile.configured ? `${tile.count} 件在制` : '未配置'}`">
                    <span class="production-line-station" :class="[tile.status, { disabled: !tile.configured }]">
                      <img v-if="stationMarkerIcon(tile.status)" class="production-line-station__icon" :src="stationMarkerIcon(tile.status)" alt="" />
                      <span v-else class="production-line-station__marker"></span>
                      <span class="production-line-station__title">
                        <strong>工序{{ tile.id }}</strong>
                        <span v-if="tile.operatorName" class="production-line-station__operator">
                          <img src="/production-statistics/icon_man.png" alt="" aria-hidden="true" />
                          <span class="production-line-station__operator-name">{{ tile.operatorName }}</span>
                        </span>
                      </span>
                      <em>{{ tile.configured ? `${tile.count} 件` : "未配置" }}</em>
                    </span>
                  </button>
                  <el-popover placement="top" :width="320" trigger="hover" :disabled="!tile.configured" popper-class="station-live-popover">
                    <template #reference>
                      <article class="production-line-status" :class="tile.status" tabindex="0">
                        <div class="production-line-status__facts"><span>工位</span><span>状态</span><strong>{{ String(tile.id).padStart(2, '0') }}</strong><strong>{{ tile.label }}</strong></div>
                        <p><i></i>{{ tile.label }}<b>{{ tile.configured ? `${tile.count} 件` : "未配置" }}</b></p>
                      </article>
                    </template>
                    <div class="station-live-popover__head"><span>{{ tile.name }} · {{ tile.label }}</span><strong>{{ tile.count }} 件在制</strong></div>
                    <div v-if="tile.tasks.length" class="station-live-popover__list"><div v-for="task in tile.tasks" :key="task.id" class="station-live-popover__row"><span>{{ task.model || "—" }}</span><strong>{{ task.steelNo || "—" }}</strong></div></div>
                    <div v-else class="station-live-popover__empty">该工位当前没有在制钢印号</div>
                  </el-popover>
                </div>
              </div>
            </section>
            <div class="stats-lower-layout">
              <div class="stats-lower-primary">
                <div class="stats-summary">
                  <article v-for="card in statsCards" :key="card.title" class="stats-overview-card sd-swatch" :class="`sd-swatch--${card.theme}`">
                    <span>{{ card.title }}</span><strong :title="String(card.value)">{{ card.value }}<small v-if="card.unit">{{ card.unit }}</small></strong>
                    <p :class="card.comparison?.tone">{{ card.detail || card.comparison?.label }}<template v-if="!card.detail && card.comparison?.label !== '无同期数据'"> 较上一周期</template></p>
                  </article>
                </div>
                <ProductionInsightsCharts
                  layout="main"
                  :deviations="parameterDeviationRows"
                  :step-durations="stepDurationRows"
                  :daily-capacity="dailyCapacityRows"
                  :today-capacity="statsTodayCapacity"
                  :today-capacity-comparison="statsTodayCapacityComparison"
                  :month-capacity="statsMonthCapacity"
                  :month-capacity-comparison="statsMonthCapacityComparison"
                  :model-options="analyticModelOptions"
                  :model="selectedAnalysisModel"
                  :deviation-model="selectedDeviationModel"
                  @update:model="selectedAnalysisModel = $event"
                  @update:deviation-model="selectedDeviationModel = $event" />
              </div>
              <ProductionInsightsCharts
                layout="duration"
                :deviations="parameterDeviationRows"
                :step-durations="stepDurationRows"
                :daily-capacity="dailyCapacityRows"
                :today-capacity="statsTodayCapacity"
                :today-capacity-comparison="statsTodayCapacityComparison"
                :month-capacity="statsMonthCapacity"
                :month-capacity-comparison="statsMonthCapacityComparison"
                :model-options="analyticModelOptions"
                :model="selectedAnalysisModel"
                :deviation-model="selectedDeviationModel"
                @update:model="selectedAnalysisModel = $event"
                @update:deviation-model="selectedDeviationModel = $event" />
            </div>
          </div>
        </template>
      </div>
    </main>
    <el-dialog
      v-model="importDialog"
      title="导入工艺 Excel"
      width="700px"
      class="mes-dialog"
      ><div class="file-add-row">
        <button type="button" class="file-add" @click="importInput?.click()">
          选择添加</button
        ><span v-if="importFile" class="file-add-name">{{
          importFile.name
        }}</span>
      </div>
      <input
        ref="importInput"
        type="file"
        accept=".xlsx,.xls"
        hidden
        @change="precheck"
      />
      <div class="import-options">
        <el-checkbox v-model="autoFix"
          >缺少参数类型时按“手动输入”修正</el-checkbox
        >
      </div>
      <div
        v-if="importPreview"
        class="precheck-result"
        :class="{ warning: importErrors.length }"
      >
        <strong>{{
          prechecked
            ? `预检通过：${importPreview.payload?.model}，共 ${importPreview.payload?.steps.length} 个步骤`
            : `预检发现 ${importErrors.length} 项问题`
        }}</strong
        ><span v-if="prechecked"
          >确认后将实际写入工艺列表，并为全部步骤复制系统默认投影模板。</span
        >
        <ul v-else>
          <li v-for="error in importErrors.slice(0, 5)" :key="error">
            {{ error }}
          </li>
        </ul>
      </div>
      <template #footer
        ><button class="ghost-button" @click="importDialog = false">取消</button
        ><button class="ghost-button" @click="runPrecheck">开始预检</button
        ><button
          class="admin-primary"
          :disabled="!prechecked"
          @click="confirmImport"
        >
          确认导入
        </button></template
      ></el-dialog
    >
    <el-dialog
      v-model="uploadDialog"
      title="批量上传视频"
      width="560px"
      class="mes-dialog"
      ><label class="dialog-label"
        >视频分类<el-cascader
          v-model="uploadCategory"
          :options="videoCategoryOptions"
          :props="{ checkStrictly: true }"
          clearable
          placeholder="可选择一级或二级分类" /></label
      ><label class="dialog-label"
        >视频文件
        <div class="file-add-row">
          <button type="button" class="file-add" @click="uploadInput?.click()">
            选择添加</button
          ><span v-if="uploadFiles.length" class="file-add-name"
            >已选择 {{ uploadFiles.length }} 个文件</span
          >
        </div>
        <input
          ref="uploadInput"
          type="file"
          accept="video/*"
          multiple
          hidden
          @change="chooseVideos" /></label
      ><template #footer
        ><button class="ghost-button" @click="uploadDialog = false">取消</button
        ><button class="admin-primary" @click="uploadVideos">
          加入资源库
        </button></template
      ></el-dialog
    >
    <el-dialog
      v-model="categoryDialog"
      :title="categoryMode === 'rename' ? '重命名视频分类' : '新增视频分类'"
      width="480px"
      class="mes-dialog"
      ><div class="dialog-fields">
        <label v-if="categoryMode === 'create'"
          >分类层级<input
            :value="categoryForm.parentId ? '二级分类' : '一级分类'"
            disabled /></label
        ><label v-if="categoryMode === 'create' && categoryForm.parentId"
          >所属一级分类<input
            :value="videoCategoryMap.get(categoryForm.parentId)?.name || ''"
            disabled /></label
        ><label
          >分类名称<input
            v-model="categoryForm.name"
            maxlength="64"
            placeholder="请输入分类名称"
            @keyup.enter="saveVideoCategory" /></label
        ><small class="dialog-hint"
          >分类最多两级；同一级分类名称不能重复。删除前需先清空二级分类及其关联视频。</small
        >
      </div>
      <template #footer
        ><button class="ghost-button" @click="categoryDialog = false">
          取消</button
        ><button class="admin-primary" @click="saveVideoCategory">
          保存分类
        </button></template
      ></el-dialog
    >
    <el-dialog
      v-model="previewDialog"
      title="查看视频"
      width="760px"
      class="mes-dialog"
      ><div v-if="previewVideo?.loading" class="video-preview-empty">
        <VideoCamera /><strong>正在获取视频播放地址</strong
        ><span>正在向文件服务申请短期播放地址。</span>
      </div>
      <div v-else-if="previewVideo?.previewUrl" class="video-preview-player">
        <video :src="previewVideo.previewUrl" controls autoplay playsinline />
      </div>
      <div v-else class="video-preview-empty">
        <VideoCamera /><strong>暂无可播放媒体</strong
        ><span>{{ previewVideo?.playbackMessage || "该记录未随附可播放媒体文件。" }}</span>
      </div></el-dialog
    >
    <el-dialog
      v-model="iconDialog"
      :title="iconDialogMode === 'create' ? '新增投影素材' : '编辑投影素材'"
      width="560px"
      class="mes-dialog"
      ><div class="dialog-fields">
        <label
          >素材名称<input
            v-model="iconForm.name"
            maxlength="128"
            placeholder="例如：定位箭头" /></label
        ><label
          >PNG 文件
          <div class="file-add-row">
            <button type="button" class="file-add" @click="iconInput?.click()">
              选择添加</button
            ><span v-if="iconFile" class="file-add-name">{{
              iconFile.name
            }}</span>
          </div>
          <input
            ref="iconInput"
            type="file"
            accept="image/png"
            hidden
            @change="chooseIcon"
          /><small>{{
            iconDialogMode === "create"
              ? "新增素材必须上传 PNG 文件。"
              : "如需替换图片，请选择新的 PNG 文件；未选择则保留原图。"
          }}</small></label
        >
      </div>
      <template #footer
        ><button class="ghost-button" @click="iconDialog = false">取消</button
        ><button class="admin-primary" @click="saveIcon">
          保存素材
        </button></template
      ></el-dialog
    >
    <el-dialog
      v-model="iconViewDialog"
      title="查看投影素材"
      width="560px"
      class="mes-dialog"
      ><div class="icon-view">
        <div v-if="iconPreview" class="icon-view__frame">
          <img :src="iconPreview.dataUrl" :alt="iconPreview.name" />
        </div>
        <strong>{{ iconPreview?.name }}</strong
        ><span>{{ iconPreview?.source }} · PNG</span>
      </div></el-dialog
    >
    <el-dialog
      v-model="deleteDialog"
      title="删除引用预检"
      width="610px"
      class="mes-dialog"
      @close="deleteTarget = null"
      ><p>资源：{{ deleteTarget?.name }}</p>
      <div class="reference-grid">
        <div>
          <span>当前配置引用</span><strong>{{ references.current }}</strong>
        </div>
        <div>
          <span>进行中任务引用</span><strong>{{ references.running }}</strong>
        </div>
        <div>
          <span>历史任务引用</span><strong>{{ references.history }}</strong>
        </div>
      </div>
      <p class="delete-dialog-help">
        确认后将执行逻辑删除，历史任务和审计记录仍可读取。
      </p>
      <template #footer
        ><button class="ghost-button" @click="deleteDialog = false">取消</button
        ><button class="danger-button" @click="confirmDelete">
          删除
        </button></template
      ></el-dialog
    >
    <el-dialog
      v-model="personDialog"
      :title="editing ? '编辑人员' : '新增人员'"
      width="560px"
      class="mes-dialog"
      ><div class="dialog-fields">
        <label
          >工号<input v-model="personForm.no" :disabled="!!editing" /><small
            v-if="editing"
            >工号为登录唯一标识，保存后不可修改。</small
          ></label
        ><label>姓名<input v-model="personForm.name" /></label
        ><label
          >角色<el-select v-model="personForm.role"
            ><el-option
              v-for="r in ['操作员', '管理员', '超级管理员']"
              :key="r"
              :label="r"
              :value="r" /></el-select></label
        ><label v-if="!editing"
          >密码<input
            v-model="personForm.password"
            placeholder="默认初始密码 000000"
          /><small>新增人员默认密码 000000，可手动修改。</small></label
        ><label>部门<input v-model="personForm.dept" /></label
        ><label>手机号<input v-model="personForm.phone" /></label
        ><label
          >状态<el-select v-model="personForm.status"
            ><el-option label="在岗" value="在岗" /><el-option
              label="停用"
              value="停用" /></el-select
        ></label>
      </div>
      <template #footer
        ><button class="ghost-button" @click="personDialog = false">取消</button
        ><button class="admin-primary" @click="savePerson">
          保存人员
        </button></template
      ></el-dialog
    >
    <el-dialog
      v-model="newTemplateDialog"
      title="新建投影模板"
      width="480px"
      class="mes-dialog"
      ><div class="dialog-fields">
        <label
          >模板名称<input
            v-model="newTemplateName"
            maxlength="64"
            placeholder="留空则按「新建模板 + 时间 + 序号」自动命名" /></label
        ><small class="dialog-hint"
          >未命名时系统自动生成，例如：<code
            >新建模板 2026-09-20 15:30:45 05</code
          ></small
        >
      </div>
      <template #footer
        ><button class="ghost-button" @click="newTemplateDialog = false">
          取消</button
        ><button class="admin-primary" :disabled="creatingTemplate" @click="confirmNewTemplate">
          {{ creatingTemplate ? "正在创建…" : "创建并进入" }}
        </button></template
      ></el-dialog
    >
    <el-dialog
      v-model="renameDialog"
      title="重命名投影模板"
      width="480px"
      class="mes-dialog"
      ><div class="dialog-fields">
        <label
          >模板名称<input
            v-model="renameName"
            maxlength="64"
            placeholder="请输入模板名称"
            @keyup.enter="confirmRename" /></label
        ><small class="dialog-hint"
          >名称将同步更新到模板卡片标题与步骤投影配置的模板选择列表。</small
        >
      </div>
      <template #footer
        ><button class="ghost-button" @click="renameDialog = false">取消</button
        ><button class="admin-primary" @click="confirmRename">
          保存名称
        </button></template
      ></el-dialog
    >
    <UserProfileDialog v-model="profileDialog" />
  </div>
</template>
