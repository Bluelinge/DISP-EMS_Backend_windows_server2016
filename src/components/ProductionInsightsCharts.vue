<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import * as echarts from "echarts";

const props = defineProps({
  deviations: { type: Array, default: () => [] },
  stepDurations: { type: Array, default: () => [] },
  dailyCapacity: { type: Array, default: () => [] },
  todayCapacity: { type: Number, default: 0 },
  todayCapacityComparison: { type: Object, default: null },
  monthCapacity: { type: Number, default: 0 },
  monthCapacityComparison: { type: Object, default: null },
  modelOptions: { type: Array, default: () => [] },
  model: { type: String, default: "" },
  deviationModel: { type: String, default: "" },
  layout: { type: String, default: "all" },
});
const emit = defineEmits(["update:model", "update:deviation-model"]);
const deviationElement = ref(null), durationElement = ref(null), capacityElement = ref(null), charts = [];
const selectedModel = computed({ get: () => props.model, set: value => emit("update:model", value) });
const selectedDeviationModel = computed({ get: () => props.deviationModel, set: value => emit("update:deviation-model", value) });

const durationRows = computed(() => {
  const rows = props.stepDurations.filter(item => Number(item.value) > 0).map(item => ({ ...item, value: Number(item.value) })).sort((a, b) => b.value - a.value);
  if (rows.length <= 5) return rows;
  const other = rows.slice(5).reduce((total, item) => total + item.value, 0);
  return [...rows.slice(0, 5), { name: "其他工序", value: other, isOther: true }];
});
const durationTotal = computed(() => durationRows.value.reduce((total, item) => total + item.value, 0));

function token(name, fallback) { return typeof window === "undefined" ? fallback : getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback; }
function number(name, fallback) { const value = parseFloat(token(name, "")); return Number.isFinite(value) ? value : fallback; }
function alpha(hex, opacity) {
  const normalized = String(hex || "").replace("#", "");
  if (normalized.length !== 6) return `rgba(0,119,250,${opacity})`;
  return `rgba(${parseInt(normalized.slice(0, 2), 16)},${parseInt(normalized.slice(2, 4), 16)},${parseInt(normalized.slice(4, 6), 16)},${opacity})`;
}
function theme() {
  return {
    blue: token("--chart-series-1", "#0077FA"), cyan: token("--chart-series-2", "#2FC6E2"), yellow: token("--chart-series-3", "#FFC53D"),
    green: token("--chart-series-4", "#16B364"), other: token("--chart-other", "#C4CEDC"), grid: token("--chart-grid", "rgba(42,54,73,.12)"),
    axis: token("--chart-axis", "#66748A"), label: token("--chart-label", "#66748A"), strong: token("--sd-gray-9", "#1A2332"),
    surface: token("--page-bg", "#FFFFFF"), tooltip: token("--chart-tooltip-bg", "#FFFFFF"), font: token("--font-sans", "sans-serif"),
    fontNum: token("--font-num", "sans-serif"), fs11: number("--fs-11", 11), fs12: number("--fs-12", 12), fs16: number("--fs-16", 16), duration: number("--duration-slow", 600),
  };
}
function base(t) {
  return {
    animationDuration: t.duration, animationEasing: "cubicOut", textStyle: { fontFamily: t.font, color: t.axis, fontSize: t.fs11 },
    tooltip: { backgroundColor: t.tooltip, borderWidth: 0, padding: [10, 12], extraCssText: "border-radius:6px;box-shadow:0 3px 10px rgba(16,22,35,.12);", textStyle: { color: t.strong, fontFamily: t.font, fontSize: t.fs12 } },
  };
}
function valueAxis(t) { return { type: "value", minInterval: 1, axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: t.axis, fontSize: t.fs11 }, splitLine: { lineStyle: { color: t.grid } } }; }
function categoryAxis(t) { return { type: "category", axisLine: { lineStyle: { color: t.grid } }, axisTick: { show: false }, axisLabel: { color: t.axis, fontSize: t.fs11, fontFamily: t.font } }; }
function deviationOption(t) {
  const option = base(t);
  option.grid = { left: 8, right: 8, top: 22, bottom: 24, containLabel: true };
  option.tooltip = { ...option.tooltip, trigger: "axis", axisPointer: { type: "shadow" }, formatter: params => `${params[0]?.axisValue || ""}<br/><b>${params[0]?.value || 0}</b> 次` };
  option.xAxis = { ...categoryAxis(t), data: props.deviations.map(item => item.name), axisLabel: { color: t.axis, fontSize: t.fs11, interval: 0 } };
  option.yAxis = valueAxis(t);
  option.series = [{ name: "偏离次数", type: "bar", data: props.deviations.map(item => item.value), barMaxWidth: 22, itemStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: t.cyan }, { offset: 1, color: t.blue }]), borderRadius: [3, 3, 0, 0] } }];
  return option;
}
function durationOption(t) {
  const total = durationTotal.value, option = base(t);
  option.color = [t.blue, t.cyan, t.yellow, t.green, token("--chart-accent-2", "#E15B64"), t.other];
  option.tooltip = { ...option.tooltip, trigger: "item", formatter: params => `${params.name}<br/><b>${params.percent}%</b> · ${formatMinutes(params.value)}` };
  option.graphic = [
    { type: "text", left: "center", top: "39%", style: { text: formatMinutes(total), fill: t.strong, fontFamily: t.fontNum, fontSize: t.fs16, fontWeight: 600, textAlign: "center" } },
    { type: "text", left: "center", top: "57%", style: { text: "总耗时", fill: t.axis, fontFamily: t.font, fontSize: t.fs11, textAlign: "center" } },
  ];
  option.series = [{ name: "工序耗时", type: "pie", radius: ["58%", "76%"], center: ["50%", "50%"], avoidLabelOverlap: false, itemStyle: { borderColor: t.surface, borderWidth: 3 }, label: { show: false }, labelLine: { show: false }, emphasis: { scale: true, scaleSize: 4 }, data: durationRows.value.map(item => ({ name: item.name, value: item.value })) }];
  return option;
}
function capacityOption(t) {
  const option = base(t);
  option.grid = { left: 18, right: 16, top: 22, bottom: 22, containLabel: true };
  option.tooltip = { ...option.tooltip, trigger: "axis", formatter: params => `${params[0]?.axisValue || ""}<br/><b>${params[0]?.value || 0}</b> 件` };
  option.xAxis = { ...categoryAxis(t), boundaryGap: false, data: props.dailyCapacity.map(item => item.label), axisLabel: { color: t.axis, fontSize: t.fs11, interval: props.dailyCapacity.length > 12 ? Math.ceil(props.dailyCapacity.length / 8) - 1 : 0 } };
  option.yAxis = valueAxis(t);
  option.series = [{ name: "完成件数", type: "line", smooth: false, showSymbol: false, data: props.dailyCapacity.map(item => item.count), lineStyle: { color: t.blue, width: 2.5 }, areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: alpha(t.cyan, .34) }, { offset: 1, color: alpha(t.cyan, 0) }]) }, emphasis: { focus: "series", scale: false } }];
  return option;
}
function formatMinutes(seconds) { return `${Math.max(0, Math.round(Number(seconds || 0) / 60))} 分钟`; }
function durationPercent(value) { return durationTotal.value ? `${Math.round(value / durationTotal.value * 100)}%` : "0%"; }
function durationColor(index) { const t = theme(); return [t.blue, t.cyan, t.yellow, t.green, token("--chart-accent-2", "#E15B64"), t.other][index] || t.other; }
function dispose() { charts.splice(0).forEach(chart => chart.dispose()); }
function create(element, option) { if (!element) return; const chart = echarts.init(element); chart.setOption(option); charts.push(chart); }
function draw() {
  dispose(); const t = theme();
  if (props.dailyCapacity.length) create(capacityElement.value, capacityOption(t));
  if (props.deviations.length) create(deviationElement.value, deviationOption(t));
  if (durationRows.value.length) create(durationElement.value, durationOption(t));
}
function resize() { charts.forEach(chart => chart.resize()); }
let observer;
onMounted(async () => { await nextTick(); draw(); observer = new ResizeObserver(resize); [deviationElement.value, durationElement.value, capacityElement.value].filter(Boolean).forEach(element => observer.observe(element)); });
watch(() => [props.deviations, props.stepDurations, props.dailyCapacity], async () => { await nextTick(); draw(); }, { deep: true });
onBeforeUnmount(() => { observer?.disconnect(); dispose(); });
</script>

<template>
  <div class="stats-insight-layout" :class="`is-${layout}`">
    <section v-if="layout !== 'duration'" class="stats-chart-card stats-capacity-card">
      <div class="stats-chart-title"><div><img class="stats-capacity-title-icon" src="/production-statistics/icon_cn.png" alt="" /><h2>产能趋势</h2></div><span>按日完成件数</span></div>
      <div class="stats-capacity-body">
        <div class="stats-capacity-kpis">
          <div class="stats-capacity-kpi"><img src="/production-statistics/icon_week.png" alt="" /><span>今日产能</span><strong>{{ todayCapacity }}<small>件</small></strong><p v-if="todayCapacityComparison" :class="todayCapacityComparison.tone">{{ todayCapacityComparison.label }}<template v-if="todayCapacityComparison.label !== '无同期数据'"> 较上一周期</template></p></div>
          <div class="stats-capacity-kpi month"><img src="/production-statistics/icon_month.png" alt="" /><span>本月产能</span><strong>{{ monthCapacity }}<small>件</small></strong><p v-if="monthCapacityComparison" :class="monthCapacityComparison.tone">{{ monthCapacityComparison.label }}<template v-if="monthCapacityComparison.label !== '无同期数据'"> 较上一周期</template></p></div>
        </div>
        <div v-if="dailyCapacity.length" ref="capacityElement" class="stats-capacity-canvas"></div>
        <div v-else class="stats-chart-empty">当前日期范围暂无完成件数</div>
      </div>
    </section>
    <section v-if="layout !== 'duration'" class="stats-chart-card stats-insight-card">
      <div class="stats-chart-title"><div><Histogram /><h2>参数偏离排行</h2></div><div class="stats-insight-model-wrap"><el-select v-model="selectedDeviationModel" size="small" :placeholder="deviationModel || '选择型号'"><el-option v-for="item in modelOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></div></div>
      <div v-if="deviations.length" ref="deviationElement" class="stats-insight-canvas"></div>
      <div v-else class="stats-chart-empty">当前型号暂无参数偏离记录</div>
    </section>
    <section v-if="layout !== 'main'" class="stats-chart-card stats-insight-card stats-duration-card">
      <div class="stats-chart-title"><div><PieChart /><h2>工序耗时占比</h2></div><div class="stats-insight-model-wrap"><el-select v-model="selectedModel" size="small" :placeholder="model || '选择型号'"><el-option v-for="item in modelOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></div></div>
      <div v-if="durationRows.length" class="stats-duration-body">
        <div ref="durationElement" class="stats-duration-canvas"></div>
        <div class="stats-duration-list"><div v-for="(item, index) in durationRows" :key="item.name" class="stats-duration-row"><i :style="{ backgroundColor: durationColor(index) }"></i><span>{{ item.name }}</span><strong>{{ formatMinutes(item.value) }}</strong><b>{{ durationPercent(item.value) }}</b></div></div>
      </div>
      <div v-else class="stats-chart-empty">该型号暂无有效工序耗时</div>
    </section>
  </div>
</template>
