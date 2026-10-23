---
name: syncdot-design-apply
description: 将程序员已做好的功能页面/组件套用 SyncDot Design 设计系统。自动注入 tokens.css + components.css（.sd-* 组件类），并把通用 class / 原生元素 / 内联样式替换为设计系统类，使任意页面一键进入 SyncDot 视觉模式。当用户说"套用设计系统""按 SyncDot 规范改 UI""接入 .sd 组件""统一视觉风格"时使用。
---

# SyncDot Design · Apply（设计系统套用）

把一份"程序员写好的、但还没走设计系统"的 HTML 页面，转换成符合 **SyncDot Design v1.17** 视觉规范的版本。
本技能自带可消费样式库（`tokens.css` + `components.css`），不依赖那份 `syncdot-design.html` 文档站。

## 何时用
- 拿到一个功能完整但样式朴素的页面（原生 `<button>`/`<input>`/`<table>`、Bootstrap/Tailwind 或纯内联样式），需要统一成 SyncDot 风格。
- 想把已有产品的 UI 批量切换为 SyncDot 设计语言（品牌蓝 `#0077FA` 界面主色、点青 `#50D2EB` 辅助、冷灰中性色、`#509BEB`/`#50D2EB` 品牌识别色）。
- 新建页面想直接基于 `.sd-*` 组件类开发。

## 设计系统速览（权威口径）
- **品牌识别色**：品牌蓝 `#509BEB` / 点青 `#50D2EB`（VI 色彩基因，用于 Logo/品牌区）。
- **界面主色**：`#0077FA`（白字对比度约 4.2:1，满足组件与大字号 WCAG AA）；加深档 `#005FD4`（5.9:1）用于白底小号强调文字。
- **中性色**：`--sd-gray-1..10`（工业冷灰，文本主色 `--sd-gray-8 #2A3649`）。
- **功能色**：success `#15A35C` / warning `#E8930C` / danger `#D9434E`（Tag/Alert 文字已加深到 AA 档）。
- **字体**：标题/品牌字 MiSans（`font-display: swap`，需 `./fonts/MiSans-*.otf`，缺则回退系统无衬线）；正文走系统字体栈。

## 套用工作流（四步）

### 1. 注入样式库（机械步骤，用 apply.py）
```bash
python "{SKILL_DIR}/apply.py" <目标页面.html> [--out 输出.html] [--inline] [--fonts]
```
- 默认：把 `tokens.css` + `components.css` 复制到目标目录的 `syncdot-design/`，并在 `<head>` 注入 `<link rel="stylesheet" href="syncdot-design/components.css">`（`components.css` 已 `@import` tokens，链一个即可）。
- `--inline`：把两份 CSS 内联进 `<style>`，产出单文件自包含页面（适合交付/预览）。
- `--fonts`：若技能目录有 `./fonts/`（MiSans OTF），一并复制，启用品牌标题字体。
- 没有 MiSans 字体时，标题回退系统无衬线，功能完全正常，只是不是 MiSans。

### 2. 语义替换（核心，由助手按表编辑）
读取目标 HTML，把通用结构替换为 `.sd-*` 组件类（见下方映射表）。这一步需要判断，**不要用脚本盲替换**——按组件语义逐个改。原则：
- 优先用设计系统的语义类；保留原 DOM 结构与可访问性属性（`role`/`aria`/`type`/`for`/`id`）。
- 删除/弱化与系统冲突的内联样式（如 `style="color:#f00;background:#09f"`），交给 `.sd-` 类接管。
- 表单控件保留 `<input>`/`<label>` 语义，只加类。

### 3. 补全可访问性（如原页面缺失）
- 交互元素用 `<button>` 而非 `<div>`；Tabs 加 `role="tablist"`/`tab`/`tabpanel` + `aria-selected`；表格 `<th scope="col/row">`；聚焦态用 `:focus-visible`（设计系统已定义 `.sd-btn:focus-visible` 等）。
- 颜色对比不足时，用加深档文字色（同 Alert/Tag 做法）。

### 4. 校验
- 双击/浏览器打开（或跑 `html-demo-verify` 技能）确认无破损、`.sd-` 类生效、布局正常。
- 抽查对比度：主按钮白字 `#FFFFFF`/`#0077FA` ≥ 4.2:1；正文 `--sd-gray-8`/`#FFFFFF` ≥ 4.5:1；Tag/Alert 文字/底色 ≥ 4.5:1。

## 组件映射表（通用 → .sd-*）

| 原始写法 | 改为 | 变体/说明 |
|---|---|---|
| `<button>` / `.btn` / `.button` | `<button class="sd-btn">` | 主按钮 `.sd-btn--primary`；次按钮 `.sd-btn--default`；虚线 `.sd-btn--dashed`；文字 `.sd-btn--text`；链接 `.sd-btn--link`；危险 `.sd-btn--danger`；大 `.sd-btn--lg`；小 `.sd-btn--sm`；胶囊 `.sd-btn--pill`（圆角 999px）；禁用 `disabled` 或 `.is-disabled` |
| `<input type="text">` / `.input` | `<input class="sd-input">` | 错误态 `.is-error`；禁用 `disabled`；占位符用 `placeholder` |
| `<select>` | `<select class="sd-select">` | — |
| checkbox | `<label class="sd-checkbox"><input type="checkbox"><span class="box"><svg.../></span>文字</label>` | `.box` 内放对勾 SVG（16×16，currentColor） |
| radio | `<label class="sd-radio"><input type="radio"><span class="box"></span>文字</label>` | — |
| switch 开关 | `<label class="sd-switch"><input type="checkbox"><span class="track"></span></label>` | — |
| `<table>` | `<table class="sd-table">` | 表头 `<th>` 自动样式；数字列加 `.num`；悬停高亮已内置；**每行（含最后一行）底部均有 1px 描边** |
| 表单辅助 / 图标底 | `.sd-form-field`（字段纵向容器）、`.sd-field-msg`（字段说明，错误态加 `.is-error`）、`.sd-badge-box`（32px 图标底） | v1.11 起统一加 `sd-` 前缀（原 `form-field` / `field-msg` / `badge-box` 无前缀，有命名冲突风险，已废弃旧名）；`.is-error` 与 `.sd-input.is-error` 同约定 |
| 标签 / 徽标 / 状态点 | `<span class="sd-tag">` | 描边 `.sd-tag--line`；点青 `--cyan`；成功 `--success`；警告 `--warning`；危险 `--danger`；灰 `--gray` |
| 角标数字 | `.sd-badge` 包裹 + `<span class="dot">` | 红点角标 |
| 提示条 / 通知 | `<div class="sd-alert sd-alert--info">` | `--success` / `--warning` / `--danger`；内置图标位 `svg`（左侧 `flex:none`） |
| **反馈卡图标对齐** | `.sd-alert` / `.sd-message` 内的 `<svg>` / `<img>` **无需额外类**，样式已内置 | 图标与**首行文字（标题或描述）视觉居中**：`translateY(calc(0.85em + var(--ink-nudge) - 50%))`，支持 `lh` 时改用 `0.5lh` 自动跟随行高。用 `-50%` 取元素自身高度，**换任何图标尺寸都不用改代码**；`.sd-alert` 已显式声明 `line-height: 1.7` 作为对齐依据。v1.12 起废弃硬编码 `margin-top: 2px` |
| 轻提示 toast | `.sd-message` | 配合 `.sd-message-wrap` 容器固定顶部 |
| Tabs | `.sd-tabs` > `.sd-tabs__nav` > `.sd-tabs__tab`（`.on`） + `.sd-tabs__pane`（`.on`） | 卡片式加 `.sd-tabs--card` |
| 分页 | `.sd-pagination` > `.sd-page`（`.on`/`.on` 当前、`:disabled` 禁用、`--arrow` 翻页箭头） | — |
| 面包屑 | `.sd-crumb`（`a` + `.sep` 分隔 + `.cur` 当前） | — |
| 进度条 | `<div class="sd-progress"><i style="width:60%"></i></div>` | 渐变蓝→青已内置 |
| **页面骨架** | `<div class="sd-layout"><aside class="sd-sidebar">…</aside><main class="sd-main"><div class="sd-content">…</div></main></div>` | 顶部栏 `.sd-topbar`；侧栏导航 `.sd-nav-group__title` / `.sd-nav-item`（`.is-active`）；内容区等宽铺满，长文页加 `.sd-content--readable` |
| **布局工具** | `.sd-row`（`--between` / `--center` / `--wrap`）、`.sd-stack`、`.sd-grid` + `.sd-col-4/6/8/12/16/24` | 24 列栅格；≤992px 自动塌到 12 列，≤768px 单列 |
| **卡片 / 页头** | `.sd-card`（`--flat`）、`.sd-card__title`、`.sd-page-head` | 卡片自带 1px 边框 + shadow-1 |
| **动效** | `.sd-hover-lift`（悬停上浮）、`.sd-fade-in`（入场）、`.sd-stagger`（子项依次入场，前 6 项错峰 60ms） | 走 `--duration` / `--ease`；`prefers-reduced-motion` 下自动禁用 |
| **品牌色卡** | `<div class="sd-swatch-grid"><div class="sd-swatch sd-swatch--blue"><div class="sd-swatch__title">…</div><div class="sd-swatch__val">…</div><div class="sd-swatch__desc">…</div></div>…</div>` | 变体：`--blue` / `--cyan`（品牌双色，深底白字）、`--blue-1` / `--cyan-1` / `--gray-1`（浅色档，**保留 Logo 变体纹理**）。**2 张用品牌双色，3 张及以上依次补浅色档**；卡片组始终填满宽度；有 icon 版加 `.sd-swatch__head` + `.sd-swatch__icon`（16px，距标题 8px）。**数值行 `.sd-swatch__val` 规格 = 28px / 600 / 行高 1.43** —— 与「字体排印」章字阶表的 **H1 · 28/40 · 页面主标题**同规格并加粗，层级高于标题(16px)；功能卡要展示数值用 `.sd-feature-card__value`（同规格）。数值串控制在 8 字符内，超出改放 `.sd-swatch__desc` |
| **功能卡** | `<div class="sd-feature-grid"><div class="sd-feature-card sd-feature-card--blue"><div class="sd-feature-card__title">…</div><div class="sd-feature-card__list"><span>…</span></div><button class="sd-feature-card__cta"><svg…/>按钮文字</button></div>…</div>` | 变体：`--blue` / `--cyan`（浅渐变深字）、`--deep`（品牌深渐变白字）。用于**能力入口 / 操作引导**，与色卡的「数据看板 / 生产信息展示」**用途区分**；卡片组填满宽度；装饰插画随变体内置，CTA 贴卡底（`margin-top:auto`） |
| **标题装饰** | 一级标题加 `.sd-title-cut`（或直接用 `.sd-page-head h1`）；二级标题 / 卡片标题加 `.sd-title-slashes`（`.sd-card__title` **已默认带**，纯文字标题加 `.sd-card__title--plain`） | 一级标题**右侧蓝色切割元素**（素材 `chevron-blue-title.svg` —— VI「切割元素 · 品牌蓝」的**标题加重版**；VI 原版 `chevron-blue.svg` 仍用于分页箭头与空状态插图，勿改其 stroke）；二级与卡片标题**左侧双斜线**（`slashes.svg`）。均等比缩放、不裁剪：一级标题盒高 **0.72em** / 宽 **0.3919em**，二级与卡片标题高 1.06em / 宽 0.65em；间距走 `--space-2`（8px）/ `--space-3`（12px）。**垂直居中**：装饰元素统一加 `transform: translateY(var(--ink-nudge))`（默认 `0.05em`；v1.12 由 `--title-mark-nudge` 更名，语义已扩展到图标对齐）——中日韩字形的墨迹中心比行框中心低约 0.05em，不加微调会偏高 1~2px |
| **图表** | `<div class="sd-card sd-chart-card"><div class="sd-chart-card__head"><div class="sd-chart-card__title">标题<span>副标题</span></div><span class="sd-chart-card__more">查看更多 ›</span></div><svg class="sd-chart">…</svg></div>` | SVG 内：网格线 `.sd-chart__grid`（**浅灰实线 1px**，禁虚线）、轴标签 `.sd-chart__axis`、hover 参考线 `.sd-chart__ref`（灰色极细虚线 4 4）、数据点 `.sd-chart__dot-halo`+`.sd-chart__dot`、Tooltip `.sd-chart__tt` + `__tt-title/__tt-label/__tt-val`。图下说明 `.sd-chart-note`，图例 `.sd-chart-legend` |
| **曲线图 hover 交互** | 每个数据点一组 `<g class="sd-chart__pt">`，内含命中区 `<rect class="sd-chart__hit" fill=transparent>` + `<g class="sd-chart__focus">`（参考线 / 光点 / Tooltip 都放里面） | **数据点 / 参考线 / Tooltip 默认不显示**，鼠标移入数据列才出现。两种驱动：① 纯 CSS `.sd-chart__pt:hover .sd-chart__focus`（零 JS，静态图表用）；② JS 给最近一组加 `.sd-chart__focus.is-active`（大数据量 / 共享 Tooltip）。`.sd-chart__focus` 已带 `opacity:0` + `pointer-events:none` + `--duration` 淡入；命中区按数据列宽铺满绘图区高度（整列可命中，无需精确点到圆点）；Tooltip 贴边翻转判据 `x + 168 > 632` |
| **环形图** | `<div class="sd-donut"><div class="sd-donut__fig"><svg class="sd-chart">…</svg></div><div class="sd-donut__list"><div class="sd-donut__head">…</div><div class="sd-donut__row">…</div>…</div></div>` | 左图右表；末行合计加 `.sd-donut__row--total`；≤992px 自动转上图下表。不用引导线百分比标注 |
| **仪表盘** | `<div class="sd-gauge sd-gauge--1"><div class="sd-gauge__fig"><svg class="sd-chart" viewBox="0 0 200 200">…</svg></div></div>` | 默认 **270° 环**（开口向下）：底轨 `__track` + 进度弧 `__arc`（环厚 16 / r 74），中心 `__value`+`__unit`+`__label`，两端可选 `__bound`。**颜色按图表系列色顺序**：`--1` 蓝 → `--2` 青 → `--3` 黄 → `--4` 绿 → `--other` 灰（禁硬编码 hex）。分段用 `__seg` + `.sd-stroke-N`（`--multi`，段间留 2° 缺口）；指针用 `--needle`（弧端帽转直角）。进度写 `stroke-dasharray="整弧×值% 464.96"`，270° 整弧 348.72 / 180° 232.48 |
| **横向柱状图** | `<div class="sd-chart__hrow"><rect class="sd-chart__hhit"/><rect class="sd-chart__hhband"/><text class="sd-chart__rowlabel">行名</text><path class="sd-chart__barh sd-anim-bar-x" style="fill:url(#grad)" d="…"/><text class="sd-chart__barval">值</text></div>` | 排名 / TOP N 场景。柱厚 20px、**值端（右端）圆角 3px**（用 path 画，不用 rect）；行名 12px `--text-secondary`、数值 11px `--muted` 等宽；hover 整行浅灰底块（纯 CSS）。**渐变务必写成内联 `style="fill:url(#id)"`** —— 呈现属性 `fill="url(#id)"` 优先级低于 CSS 类的 `fill`，会被默认系列色静默覆盖。外层加 `.sd-anim-seq` 让各行错峰生长 |
| **图表出生动效** | 容器 `<svg class="sd-chart sd-chart-anim">`；图形加 `.sd-anim-line`（折线，需 `--len`）/ `.sd-anim-area` / `.sd-anim-bar-y` / `.sd-anim-bar-x` / `.sd-anim-arc`（弧，需 `--len`，分段定位补 `--off`）/ `.sd-anim-needle`（指针，需 `--rot`）/ `.sd-anim-fade`；组容器加 `.sd-anim-seq` | 纯 CSS 零 JS。生长走 `--duration-slow`(600ms)、描线/扫弧/淡入走 `--duration-draw`(900ms)；`.sd-anim-seq` 按 DOM 顺序每级递增 40ms（12 级）；单元素可内联 `--sd-delay`。**纵向柱建议把「一整列」包进 `<g class="sd-anim-bar-y">`**，整列作为一个整体从底部生长，比逐段生长自然。`prefers-reduced-motion` 下由全局规则自动关闭 |
| **横向柱状图** | `<div class="sd-chart__hrow"><rect class="sd-chart__hhit"/><rect class="sd-chart__hhband"/><text class="sd-chart__rowlabel">行名</text><path class="sd-chart__barh sd-anim-bar-x" style="fill:url(#grad)" d="…"/><text class="sd-chart__barval">值</text></div>` | 排名 / TOP N 场景。柱厚 20px、**值端（右端）圆角 3px**（用 path 画，不用 rect）；行名 12px `--text-secondary`、数值 11px `--muted` 等宽；hover 整行浅灰底块（纯 CSS）。**渐变务必写成内联 `style="fill:url(#id)"`** —— 呈现属性 `fill="url(#id)"` 优先级低于 CSS 类的 `fill`，会被默认系列色静默覆盖。外层加 `.sd-anim-seq` 让各行错峰生长 |
| **图表出生动效** | 容器 `<svg class="sd-chart sd-chart-anim">`；图形加 `.sd-anim-line`（折线，需 `--len`）/ `.sd-anim-area` / `.sd-anim-bar-y` / `.sd-anim-bar-x` / `.sd-anim-arc`（弧，需 `--len`，分段定位补 `--off`）/ `.sd-anim-needle`（指针，需 `--rot`）/ `.sd-anim-fade`；组容器加 `.sd-anim-seq` | 纯 CSS 零 JS。生长走 `--duration-slow`(600ms)、描线/扫弧/淡入走 `--duration-draw`(900ms)；`.sd-anim-seq` 按 DOM 顺序每级递增 40ms（12 级）；单元素可内联 `--sd-delay`。**纵向柱建议把「一整列」包进 `<g class="sd-anim-bar-y">`**，整列作为一个整体从底部生长，比逐段生长自然。`prefers-reduced-motion` 下由全局规则自动关闭 |
| **动效可见性** | 图表容器加 `.sd-anim-reveal` | 长页面里图表容易「加载即播完」，滚到跟前看不到。加这个类后动效改为**进入视口时播放**：脚本初始化加 `.js-ready`（暂停在起点）、进入视口加 `.is-inview`（播放）；无 JS 时支持滚动驱动动画的浏览器自动用 `animation-timeline: view()` 播。`.js-ready` 由脚本添加，脚本失效即退回「加载即播」，不会出现动画永远暂停的不可见状态 |
| **系列色 / 等分栅格** | `.sd-fill-1~4` / `.sd-fill-other`、`.sd-stroke-1~4` / `.sd-stroke-other`、`.sd-grid-2` / `.sd-grid-3` / `.sd-grid-4` | 系列色类**免硬编码 hex**：`sd-fill-*` 同时设 SVG `fill` 与 HTML `background`（图例点 / 明细点），`sd-stroke-*` 仅 SVG 描边；等分栅格 ≤1280px 收 2 列、≤768px 单列 |
| **商品卡** | `<div class="sd-product-grid"><div class="sd-product-card"><div class="sd-product-card__media"></div><div class="sd-product-card__eyebrow">本季新品</div><div class="sd-product-card__title">品名</div><div class="sd-product-card__price">¥ 12,800 起</div><div class="sd-product-card__actions"><button class="sd-btn sd-btn--primary sd-btn--lg sd-btn--pill">查看全部型号</button></div></div>…</div>` | 用于**产品陈列 / 选型对比**（区别于色卡=展示数据、功能卡=引导操作）。图区 `__media` 默认 1:1（`--media--wide` 为 4:3）、底 `--sd-gray-1`、图片 `object-fit: contain`；眉标大写 12px/500 字距 .08em（白底 4.74:1 AA）；品名 16px/600；价格 14px `--muted`；卡圆角 12px / 内距 16px；操作区 `margin-top:auto` 贴卡底保证同行等高对齐 |

## 字体说明（v1.17：含装饰性文字约束）
- 标题/品牌字走 MiSans：把 `h1–h4` 与品牌标题元素交给设计系统（已内置 `h1,h2,h3,h4 { font-family: var(--font-display) }`）。
- **正文、界面文字以及一切装饰性文字**（章节编号、图注、标签、说明、元数据）统一走 `--font-sans`；**禁止装饰性文字使用等宽（mono）或任何第三字族**。
- `--font-mono` 为受限例外，仅用于：行内代码、代码块、表格数字列、图表数值、色值 HEX。
- 未放 MiSans 字体时自动回退系统无衬线（PingFang SC / Microsoft YaHei），不影响功能。
- 想启用 MiSans：把 4 个 `MiSans-*.otf`（Regular/Medium/Semibold/Bold）放进技能目录的 `fonts/`，套用时加 `--fonts`。

## 数字与字体规范（v1.17）
- **三个字体族**：`--font-sans`（正文/UI，默认）、`--font-display`（标题/品牌 MiSans）、`--font-mono`（**仅** code / `.sd-code` / `.sd-table .num`）。
- **数据数字走 `--font-num`（= `--font-sans`）+ `font-variant-numeric: tabular-nums`**：卡片指标值、统计数字、编码/文号、进度百分比等。既保证列对齐，又与正文同字形。
- **数值层级（v1.17 统一口径）**：
  | 层级 | 类 | 字号 / 行高 / 字重 | 用途 |
  |---|---|---|---|
  | L1 主数值 | `.sd-swatch__val` / `.sd-card__value` / `.sd-feature-card__value` | **28 / 1.43 / 600** | 色卡、数据卡、功能卡主数值 —— 与「字体排印」H1 页面主标题同规格 |
  | L2 统计值 | `.sd-stat__value` | **24 / 1.3 / 600** | 统计卡与指标组的中等数值，默认取界面主色 |
  | L3 表格与图表数字 | `.sd-table .num` / `.sd-chart__barval` | 13 / 11 · 400 | 表格数字列、图表轴与数值标签（`--font-mono` 授权范围） |
- L1 之所以是 600 而非 H1 的 500：规范中 Semibold 600 明确授权用于**英文与数字加粗**。数值串建议 ≤ 8 字符，更长的说明放说明行。
- ⚠️ **`--font-mono` 不得用于指标/装饰数字**：其栈首项 `SFMono-Regular` 在 Windows 不存在，会回退 **Consolas**，大号数字观感违和。
- 数字容器（表格、卡片指标区）建议在父级统一挂 `tabular-nums`，一次覆盖全部数字。

## 布局与响应式（v1.17）
- **内容区无宽度上限，随视口等宽铺满**：`--content-max: 100%` —— 侧栏 232px 与左右内边距 24px 之外的宽度全部给内容；长文阅读型页面用 `.sd-content--readable`（或正文段落 `max-width: 76ch`）控行宽。
- **侧栏展开宽统一 232px**（`--sidebar-w`），各断点一致。
- **大屏放大字号与间距**：`@media (min-width:1920px)` ×1.10；`@media (min-width:2560px)` ×1.22；内容区继续等宽铺满。`max-width:992px` 侧栏收起 + 栅格塌到 12 列；`max-width:768px` 单列。
- 字号/间距/容器宽度全部 token 化（`--fs-*` / `--space-*` / `--content-max` / `--sidebar-w`），媒体查询内重定义 `:root` 即全站生效。
- 套用后在大屏仍偏小，多是 Windows 系统缩放 100% 所致，建议把系统缩放抬至 125%/150%。

## 同步审计（每轮必跑）

设计系统有四处落点，任何一处改完都必须同步其余三处，否则会出现「文档写了、Kit 没有」或「Kit 有、文档没写」的静默 drift。**宣布「Kit 已同步」之前，必须先跑审计：**

```bash
python ~/.workbuddy/skills/syncdot-design-apply/audit.py
```

路径不同时显式指定：`--doc <文档站.html> --kit <技能目录> --release <成品包> --demo <演示副本>`。
退出码 0 = 无 drift；1 = 有问题，报告末尾会列出。

**审计的 11 个维度**（按发现 bug 的实战价值排序）：

| # | 维度 | 为什么需要 |
|---|---|---|
| 0 | **品牌自检** | 扫 `synodot`/`syndot`/`数智科技`/`#5194D0`/`#59C2DA`/`rgba(36,112,220` —— 错拼与旧色出现即 FAIL |
| 1 | 版本一致性 | 七处版本号（文档站 / tokens / components / SKILL / USAGE / example / README）必须完全相同 |
| 2 | 副本同源 | 成品包与演示副本的 CSS 必须与技能 Kit **md5 一致** |
| 3 | HTML↔CSS 自洽 | 页面用到的类，自己的 CSS 是否定义 —— 曾抓出「柱状图 39 根柱子全渲染成黑色」 |
| 4 | 文档站→Kit | 文档站有、Kit 缺（真 drift，必须补） |
| 5 | Kit→文档站 | 分类：外壳等价（可忽略） vs Kit 专有（需确认） |
| 6 | **属性级比对** | 同名选择器的声明逐条比对 —— **类名齐 ≠ 样式一致**，本维度抓过 3 个真 bug（旧蓝残留、缺 SVG 宽度规则、缺 tabular-nums） |
| 7 | 未定义变量 | `var()` 引用了但从未声明 → 静默失效（如某轮 `--font-num` 只在一侧定义） |
| 8 | 宣称 vs 定义 | 文档「组件类名」表宣称的类是否真的存在 |
| 9 | 重复选择器 | 同一选择器声明多次（后者覆盖前者），注入脚本不幂等时会出现 |
| 10 | 结构 + 依赖 | 花括号 / svg / g / div / section 平衡；Kit 外部图片依赖应为 0 |

**审计自身的两个坑（已在 audit.py 内修掉，改动时别退回去）：**
- 解析 CSS 必须跳过**无花括号的 at-statement**（`@import` / `@charset`）—— 否则下一条规则的选择器会被吞进这条语句，产生**假差异**。
- 同名选择器被声明多次时要**合并**而不是取第一条 —— 否则会把「第一条」当全部，误报缺属性。

**结果处置约定：**
- 维度 6 报出的差异要先判断真假：`url(` 差异（data URI ↔ 本地 assets）与值等价的 token 写法（`12px` vs `var(--space-3)`）属**有意为之**，audit.py 已自动过滤；确认等效但写法不同的，登记进 `KNOWN_EQUIV` 并写明原因。
- 维度 3/5 报出的「未定义类」不一定是 bug —— 文档站外壳类（`.card`/`.sidebar`/`.block-title`）与 Kit 的 `.sd-*` 是两套实现；但要逐项确认，别整批放过（`page` / `report-intro--plain` 就是确认过的死类）。

## 注意事项
- 基础 reset 会重置 `body`/标题/盒模型/默认边距。若目标页依赖原有全局基底，套用后需目视回归，必要时把冲突的全局规则改为局部类。
- 设计系统只含当前 v1.17 已落地内容：组件（Button/Tabs/Input/Checkbox/Radio/Switch/Table/Tag/Badge/Alert/Message/Pagination/Breadcrumb/Progress）+ **布局骨架 / 栅格 / 卡片 / 动效工具类 / 品牌色卡 / 功能卡 / 商品卡 / 图表与环形图 / 仪表盘 / 横向柱状图 / 图表出生动效 / 标题装饰**（见映射表末十六行）。Modal/Drawer/Tooltip/Tree/Skeleton/Upload/DatePicker 等尚未纳入，遇到时按现有 token 手写或暂留原样并备注。
- **图表规范红线**：网格线必须是**浅灰实线**（`--chart-grid`），不要写成虚线；hover 参考线才是虚线且用 `--sd-gray-4` 极细 1px。
- 注入不产生破坏性改动；建议 `--out` 另存，保留原文件对比。
