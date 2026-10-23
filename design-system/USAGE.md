# SyncDot Design · Apply 使用说明

把任意 HTML 页面套用 **SyncDot Design v1.17** 视觉规范（品牌蓝 `#0077FA` 界面主色、点青 `#50D2EB` 辅助、`#509BEB`/`#50D2EB` 品牌识别色、工业冷灰中性色）。

## 方式一：让 AI 助手来套（推荐）
直接说：「把 xxx 页面套用设计系统」「按 SyncDot 规范改 UI」「统一视觉风格」。
助手会调用 `syncdot-design-apply` 技能：先注入样式库，再把原生元素逐个换成 `.sd-*` 组件类（保留 DOM 结构与可访问性）。

## 方式二：你自己用脚本注入
```bash
# 注入样式库：在页面旁生成 syncdot-design/ 并 <link> 进 <head>
python apply.py 你的页面.html --out 输出.html

# 单文件自包含（CSS 内联）
python apply.py 你的页面.html --out 输出.html --inline

# 启用 MiSans 品牌标题字体（需先把 4 个 MiSans-*.otf 放进本目录的 fonts/）
python apply.py 你的页面.html --fonts
```
注入后，按 SKILL.md 的「组件映射表」把组件改成 `.sd-*` 类即可。

## 引入到自己的工程
- 复制 `syncdot-design/` 文件夹进工程，`<link rel="stylesheet" href="syncdot-design/components.css">` 即可（`components.css` 已 `@import tokens.css`，链一个文件足够）。
- 然后按 `.sd-*` 类写结构；或把现有结构的 class 改过去。

**可交付成品包**：`F:\wb\SDT-Yu\syncdot-design-kit\`（v1.17，可直接发程序员）

```
syncdot-design-kit/
├── README.md               快速接入 + 类速查 + 品牌口径 + 可访问性数据
├── tokens.css              130 个设计变量
├── components.css          150 个 .sd-* 组件类（@import tokens.css）
├── fonts/                  MiSans 4 字重（约 25MB；缺失自动回退，不阻塞渲染）
├── assets/page-arrow.svg   分页箭头素材（供 <img> 引用；其余图形已内联为 data URI）
└── example.html            全组件参考页（133 个类，该文件即验收样本）
```

文档站（`syncdot-design.html`，18 章）是**设计源头**，本目录是它的**可消费产物**；由设计侧统一产出后同步，不要直接改成品包。

## 组件映射速查
| 通用写法 | 改为 |
|---|---|
| `<button>` / `.btn` | `.sd-btn`（主 `.sd-btn--primary` / 次 `.sd-btn--default` / 虚线 `.sd-btn--dashed` / 文字 / 链接 / 危险 / `--lg`/`--sm`/`--pill`） |
| `<input>` / `<select>` | `.sd-input` / `.sd-select` |
| 表单辅助 / 图标底 | `.sd-form-field` / `.sd-field-msg`（错误态 `.is-error`）/ `.sd-badge-box` —— v1.11 起统一 `sd-` 前缀 |
| checkbox / radio / switch | `.sd-checkbox` / `.sd-radio` / `.sd-switch`（保留 `<input>` 语义） |
| `<table>` | `.sd-table` |
| 标签 / 状态点 | `.sd-tag`（变体 `--cyan`/`--success`/`--warning`/`--danger`/`--gray`/`--line`）、`.sd-badge` |
| 提示条 | `.sd-alert`（--info/--success/--warning/--danger） |
| 反馈卡图标 | `.sd-alert` / `.sd-message` 内图标**无需额外类**，已自动与首行文字视觉居中 |
| Tabs / 分页 / 面包屑 / 进度 | `.sd-tabs` / `.sd-pagination` / `.sd-crumb` / `.sd-progress` |
| **页面骨架** | `.sd-layout` > `.sd-sidebar` + `.sd-main` > `.sd-content`（顶栏 `.sd-topbar`、导航 `.sd-nav-item` 选中加 `.is-active`） |
| **布局工具** | `.sd-row`（`--between`/`--center`/`--wrap`）/ `.sd-stack` / `.sd-grid` + `.sd-col-4·6·8·12·16·24` |
| **卡片 / 页头** | `.sd-card`（`--flat`）/ `.sd-card__title` / `.sd-page-head` |
| **动效** | `.sd-hover-lift`（悬停上浮）/ `.sd-fade-in`（入场）/ `.sd-stagger`（子项依次入场） |
| **品牌色卡** | `.sd-swatch-grid` > `.sd-swatch`（变体 `--blue`/`--cyan`/`--blue-1`/`--cyan-1`/`--gray-1`）+ `__title` / `__val` / `__desc` |
| **功能卡** | `.sd-feature-grid` > `.sd-feature-card`（变体 `--blue`/`--cyan`/`--deep`）+ `__title` / `__list` / `__cta` |
| **标题装饰** | 一级标题 `.sd-title-cut`；二级 / 卡片标题 `.sd-title-slashes`（`.sd-card__title` 默认已带） |
| **图表** | `.sd-chart`（SVG 根）+ `__grid` / `__axis` / `__ref` / `__dot` / `__tt*`；面板 `.sd-chart-card` + `__head` / `__title` / `__more`；图例 `.sd-chart-legend`；说明 `.sd-chart-note` |
| **曲线图交互** | `.sd-chart__pt`（每点一组）> `__hit`（命中区）+ `__focus`（默认隐藏的高亮组）|
| **环形图** | `.sd-donut` > `__fig` + `__list`（`__head` / `__row` / `__dot` / `__name` / `__val` / `__pct`，合计行加 `--total`） |
| **仪表盘** | `.sd-gauge`（变体 `--1`/`--2`/`--3`/`--4`/`--other`）+ `__fig` / `__track` / `__arc` / `__value` / `__unit` / `__label` / `__bound`；分段 `__seg`+`.sd-stroke-N`、指针 `--needle` |
| **横向柱状图** | `.sd-chart__hrow` + `__hhit` / `__hhband` / `__rowlabel` / `.sd-chart__barh` / `.sd-chart__barval` |
| **动效可见性** | 图表容器加 `.sd-anim-reveal`（+ `.js-ready` / `.is-inview`） |
| **图表出生动效** | `.sd-chart-anim`、`.sd-anim-seq`、`.sd-anim-line` / `-area` / `-bar-y` / `-bar-x` / `-arc` / `-needle` / `-fade` |
| **横向柱状图** | `.sd-chart__hrow` + `__hhit` / `__hhband` / `__rowlabel` / `.sd-chart__barh` / `.sd-chart__barval` |
| **图表出生动效** | `.sd-chart-anim`、`.sd-anim-seq`、`.sd-anim-line` / `-area` / `-bar-y` / `-bar-x` / `-arc` / `-needle` / `-fade` |
| **系列色 / 等分栅格** | `.sd-fill-1~4`、`.sd-fill-other`、`.sd-stroke-1~4`、`.sd-stroke-other`；`.sd-grid-2` / `.sd-grid-3` / `.sd-grid-4` |
| **商品卡** | `.sd-product-grid` > `.sd-product-card` + `__media`（`--wide`）/ `__eyebrow` / `__title` / `__price` / `__actions` |

## 商品卡（v1.17）

用于**产品陈列 / 选型对比**。与另两类卡片的区别：

| | 色卡 `.sd-swatch` | 功能卡 `.sd-feature-card` | 商品卡 `.sd-product-card` |
|---|---|---|---|
| 用途 | 数据看板 / 生产信息展示 | 能力入口 / 操作引导 | 产品陈列 / 选型对比 |
| 取向 | 信息优先 | 行动优先 | 选择优先 |
| 背景 | 纯色底 + Logo 纹理 | 渐变底 + 装饰插画 | 白底 + 浅灰图区 |

```html
<div class="sd-product-grid">
  <div class="sd-product-card">
    <div class="sd-product-card__media">        <!-- 可放 <img>，等比不变形 -->
      <img src="产品图.png" alt="">
    </div>
    <div class="sd-product-card__eyebrow">本季新品</div>
    <div class="sd-product-card__title">CR400AF 转向架数字教具</div>
    <div class="sd-product-card__price">¥ 12,800 起</div>
    <div class="sd-product-card__actions">
      <button class="sd-btn sd-btn--primary sd-btn--lg sd-btn--pill">查看全部型号</button>
    </div>
  </div>
</div>
```

**槽位与规范**

| 槽位 | 类名 | 规范 |
|---|---|---|
| 图区 | `__media` | 1:1 · 圆角 8px · 底 `--sd-gray-1` · 图片 `object-fit: contain` |
| 图区（横版） | `__media--wide` | 4:3 |
| 眉标 | `__eyebrow` | 12px / 500 · 字距 .08em · 大写 · `--muted`（白底 **4.74:1** AA） |
| 品名 | `__title` | 16px / 600 · 行高 1.4 · `--sd-gray-9` |
| 价格 / 副信息 | `__price` | 14px · `--muted` · `tabular-nums` |
| 操作区 | `__actions` | `margin-top: auto` 贴卡底 · 按钮用 `.sd-btn--pill` |

整卡：圆角 **12px**（`--radius-xl`）、内距 **16px**（`--space-4`）、边框 `--page-border` + `--shadow-1`，hover 边框转 `--sd-blue-3` 并上浮 2px。
网格：`auto-fit · minmax(260px, 1fr) · gap 16px`，始终填满容器。

**三点实现要点**

1. **等高对齐** —— 操作区 `margin-top: auto`，同行卡片品名长短不一时按钮仍对齐。
2. **图片不变形** —— 图区固定比例，图片一律 `object-fit: contain`，不裁切产品本体。
3. **可省略槽位** —— 眉标、价格、操作区都可整块去掉（纯展示型卡片）。

## 品牌色卡（v1.17）
把「颜色底图 + Logo 变体纹理」做成可复用的卡片背景模板：

```html
<div class="sd-swatch-grid">
  <div class="sd-swatch sd-swatch--blue">
    <div class="sd-swatch__title">品牌蓝 · SyncDot Blue</div>
    <div class="sd-swatch__val">#509BEB · HSL 213 / 79% / 62%</div>
    <div class="sd-swatch__desc">品牌识别色 —— 用于 Logo、品牌物料、市场传播</div>
  </div>
  <div class="sd-swatch sd-swatch--cyan">…</div>
</div>
```

- **变体顺序**：2 张 → `--blue` + `--cyan`（品牌双色优先）；3 张 → 补 `--blue-1`；4 张 → 补 `--cyan-1`；5 张 → 补 `--gray-1`。浅色档同样**保留 Logo 变体纹理**（转为淡蓝，12% 不透明度）。
- **宽度**：`.sd-swatch-grid` 用 `auto-fit` 网格，卡片组**始终填满容器宽度**（单列最小 260px）。
- **排版（火山引擎 Arco 标准）**：标题 16 / 500 / 1.5 →（距 4px）值 14 / 1.5715 →（距 4px）说明 12 / 1.6。
- **有 icon 版**：把标题包进 `.sd-swatch__head`，文字前放 16×16 图标 `.sd-swatch__icon`（距标题 8px）；无 icon 版直接用 `.sd-swatch__title`。
- 间距取自系统规范：卡片间 `--space-4`（16px）、卡内 `--space-5`（24px）、圆角 `--radius-lg`（8px）。
- 纹理已**内联为 data URI**，无需任何图片文件；深色卡白色 Logo 与浅色档淡蓝 Logo **均为 12% 不透明度**（保持白色不变，只调透明度）。


**数值行规格（v1.17 统一为 L1 层级，见「数值层级」章）**：`.sd-swatch__val` 由 14px/400 提升为 **28px / 600 / 行高 1.43** —— 与「字体排印」章字阶表的 **H1 · 28 / 40 · 页面主标题**同规格并加粗。色卡在数据看板 / 生产信息展示场景里，数值是最重要的一行，层级高于标题(16px)；数字走 `tabular-nums`。功能卡要展示数值用 `.sd-feature-card__value`（同规格）。**数值串建议 ≤ 8 字符**，更长的说明放 `.sd-swatch__desc`。
## 功能卡（v1.17）
用于**能力入口 / 操作引导**（大尺寸 · 渐变底 · 要点列表 + 胶囊按钮）。与色卡严格区分：**色卡 = 数据看板 / 生产信息展示**（信息优先），**功能卡 = 能力入口 / 操作引导**（行动优先）。

```html
<div class="sd-feature-grid">
  <div class="sd-feature-card sd-feature-card--blue">
    <div class="sd-feature-card__title">实时记录</div>
    <div class="sd-feature-card__list">
      <span>实时语音转文字</span>
      <span>区分发言人</span>
      <span>智能总结要点</span>
    </div>
    <button class="sd-feature-card__cta">
      <svg …></svg>开始录音
    </button>
  </div>
  <div class="sd-feature-card sd-feature-card--cyan">…</div>
  <div class="sd-feature-card sd-feature-card--deep">…</div>
</div>
```

- **变体**：`--blue`（点蓝浅渐变，深字）/ `--cyan`（点青浅渐变，深字）/ `--deep`（品牌蓝 → 界面主色 → 点青，白字，CTA 用品牌蓝字）。
- **结构**：标题 24 / 600 / 1.3 →（距 12px）要点列表 14 / 1.67（行间 4px）→ 操作按钮（44px 高胶囊，`margin-top:auto` 贴卡底，白底 + 16px 图标）。
- **尺寸**：最小高 260px、圆角 `--radius-xl`（12px）、内距 `--space-6`（32px）—— 比色卡（148 / 8 / 24）更大更高。
- **宽度**：`.sd-feature-grid` 为 auto-fit 网格（单列最小 300px），卡片组**填满容器**。
- **装饰插画**：卡片堆叠 / 文档 / 闪光三款，随变体内置为 data URI，**DOM 中无需额外元素**。
- **用途边界**：两者可同屏出现，但不要混用用途（数据用色卡、入口用功能卡）。

## 图表（v1.17）

数据可视化样式已从文档站抽入 Kit，产品端可直接复用，全部走 Design Token。

```html
<div class="sd-card sd-chart-card">
  <div class="sd-chart-card__head">
    <div class="sd-chart-card__title">曲线图<span>内容数据 · 近 7 日</span></div>
    <span class="sd-chart-card__more">查看更多 ›</span>
  </div>
  <svg class="sd-chart" viewBox="0 0 640 236" role="img" aria-label="曲线图">
    <line class="sd-chart__grid" x1="48" y1="12" x2="632" y2="12"/>
    <text class="sd-chart__axis" x="40" y="16" text-anchor="end">60k</text>
    <line class="sd-chart__ref" x1="316" y1="12" x2="316" y2="196"/>
    <circle class="sd-chart__dot-halo" cx="316" cy="105" r="7"/>
    <circle class="sd-chart__dot" cx="316" cy="105" r="3.5"/>
    <rect class="sd-chart__tt" x="332" y="48" width="152" height="66" rx="8"/>
    <text class="sd-chart__tt-title" x="344" y="68">2026-03-11</text>
    <text class="sd-chart__tt-val" x="472" y="94" text-anchor="end">39,068</text>
  </svg>
  <p class="sd-chart-note">网格线为浅灰实线；hover 参考线为灰色极细虚线。</p>
</div>
```

**规格红线**

| 项目 | 规定 |
|---|---|
| 网格线 | **浅灰实线** `--chart-grid`（rgba(42,54,73,.12)）1px，**禁止虚线** |
| hover 参考线 | `--sd-gray-4` **极细虚线** 1px，`dasharray: 4 4` |
| 折线 | 描边 2.5px / `linecap: round`；单曲线用 点青→点蓝 横向渐变；面积 主色 16%→0% 纵向渐变 |
| 柱子 | 宽 20px，顶角圆角 3px（用 `path` 画顶角，不用 `rect rx`） |
| 数据点 | 外圈光斑 `r 7` / 18% + 内点 `r 3.5` / 白描边 2px |
| Tooltip | 白底 `rx 8` + `drop-shadow(0 3px 5px rgba(16,22,35,.12))`；数值等宽右对齐 |
| 环形图 | `stroke-width 26` / `r 70`；**不用引导线百分比标注**，改左图右表（≤992px 转上图下表），明细降序 + 合计行 |
| 系列色 | 顺序 蓝 → 青 → 黄 → 绿 → 其他；对比关系优先 蓝/青；超过 4 类用 Top N + 其他聚合 |

**系列色快捷类**（避免硬编码 hex）：`.sd-fill-1~4` / `.sd-fill-other` 同时设 SVG 的 `fill` 与 HTML 的 `background`，所以图例圆点、明细列表点也能直接用；`.sd-stroke-1~4` / `.sd-stroke-other` 仅设描边。

**等分栅格**：`.sd-grid-2` / `.sd-grid-3` / `.sd-grid-4` —— ≤1280px 收为 2 列，≤768px 单列。

## 曲线图 hover 交互（v1.17）

**数据点 / 参考线 / Tooltip 默认不显示**，只在鼠标移入对应数据列时出现。

```html
<svg class="sd-chart" viewBox="0 0 640 236" role="img" aria-label="曲线图">
  <!-- 网格 / 轴 / 面积 / 曲线 照常 -->
  <line class="sd-chart__grid" x1="48" y1="12" x2="632" y2="12"/>
  <text class="sd-chart__axis" x="40" y="16" text-anchor="end">60k</text>
  <path d="M64,159.2 C…" fill="none" stroke="url(#line)" stroke-width="2.5"/>

  <!-- 每个数据点一组：命中区 + 焦点组 -->
  <g class="sd-chart__pt">
    <rect class="sd-chart__hit" x="300.65" y="8" width="94.7" height="192"/>
    <g class="sd-chart__focus">
      <line class="sd-chart__ref" x1="348" y1="12" x2="348" y2="196"/>
      <circle class="sd-chart__dot-halo" cx="348" cy="104" r="7"/>
      <circle class="sd-chart__dot" cx="348" cy="104" r="3.5"/>
      <rect class="sd-chart__tt" x="364" y="48" width="152" height="66" rx="8"/>
      <text class="sd-chart__tt-title" x="376" y="68">2026-03-12</text>
      <circle class="sd-fill-1" cx="380" cy="90" r="4"/>
      <text class="sd-chart__tt-label" x="390" y="94">总内容量</text>
      <text class="sd-chart__tt-val" x="504" y="94" text-anchor="end">36,540</text>
    </g>
  </g>
</svg>
```

**两种驱动方式**

| 方式 | 标记 | 适用 |
|---|---|---|
| 纯 CSS（推荐） | `.sd-chart__pt:hover .sd-chart__focus` | 每个数据点自带命中区，鼠标移入自动显示该点，**零 JS** |
| JS 驱动 | `.sd-chart__focus.is-active` | 大数据量、或一条共享 Tooltip 跟随最近点；也可由键盘 / 触摸触发 |

**关键约定**

| 要点 | 说明 |
|---|---|
| 默认隐藏 | `.sd-chart__focus { opacity: 0; pointer-events: none }`，带 `--duration` 淡入 |
| `pointer-events: none` | 保证 Tooltip 不会盖住命中区（否则会出现闪烁 / 无法切换） |
| 命中区 | `fill: transparent`，按**数据列宽度**铺满绘图区高度（本例 `y=8 h=192`），整列可命中，不用精确点到圆点 |
| 列宽怎么算 | 第 i 列 `left = (x[i-1]+x[i])/2`、`right = (x[i]+x[i+1])/2`；首列左边界取绘图区左边、末列右边界取绘图区右边 |
| Tooltip 贴边翻转 | 数据点靠右时改到左侧，判据 `x + 168 > 632` |
| 可访问性 | hover 才能看到的信息对键盘 / 触摸不可达。数据关键时请同时用 `.is-active` 响应聚焦，或像环形图那样配一张明细表 |

自检页：`demo-chart-hover.html`（① 可交互 / ② 强制 `.is-active` 对照 + 两种驱动方式说明）。

## 反馈卡图标与文字对齐（v1.17）

`.sd-alert`（反馈卡）与 `.sd-message`（轻提示）内的图标**不需要任何额外类**，样式已内置：

```html
<div class="sd-alert sd-alert--warning">
  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
    <path d="M12 3 2 20h20L12 3z"/><path d="M12 10v4M12 17.5h.01"/>
  </svg>
  <div><strong>存储空间不足</strong>历史回放数据已达配额 85%，建议清理 90 天前的冷数据。</div>
</div>
```

**对齐规则**：图标圆心与**首行文字**（标题或描述，二者皆可）的视觉中心齐平。

```css
.sd-alert svg, .sd-alert > img {
  flex: none;
  transform: translateY(calc(0.85em + var(--ink-nudge) - 50%));
}
@supports (block-size: 1lh) {   /* 支持 lh 时自动跟随行高 */
  .sd-alert svg, .sd-alert > img { transform: translateY(calc(0.5lh + var(--ink-nudge) - 50%)); }
}
```

**为什么这样写**

| 要点 | 说明 |
|---|---|
| 为什么不用 `align-items: center` | 那会把图标对齐到**整块**的垂直中心；描述换行时图标会跑到文字中间，而不是首行 |
| 为什么要 `0.85em` / `0.5lh` | 对齐目标是**首行行框的半高**（`line-height 1.7 ÷ 2`）才能落到首行；`lh` 单位可自动跟随行高 |
| 为什么还要 `var(--ink-nudge)` | 中文墨迹中心比行框中心低约 0.05em，补偿后才真正「视觉居中」 |
| 为什么用 `-50%` 而非 `margin-top` | `translateY` 的百分比取**元素自身高度**，换 16/18/20px 的图标都不用改代码 |
| 为什么废弃 `margin-top: 2px` | 固定像素、不随字号缩放、换图标尺寸即失准 |

**实测（Chrome · 13px 正文 / 15×15 图标 / 行高 1.7）**

| | 图标中心与首行墨迹中心的偏差 |
|---|---|
| 修正前（`margin-top: 2px`） | **+2.05px（偏高）** |
| 修正后 | **−0.15px**（3 / 4 用例）；最差 −0.65px |

自检页：`demo-alert-icon.html`（新旧并排 + 首行墨迹中心参考线 + 数值表）。

## 标题装饰（v1.17）
标题左右两侧的装饰元素，均取自 VI 品牌辅助图形：一级标题右侧用**切割元素 · 品牌蓝的标题加重版**（`chevron-blue-title.svg`，几何同 VI 原版、线宽加重）；VI 原版 `chevron-blue.svg` 仍供分页箭头与空状态插图使用，**请勿改其 stroke**；二级与卡片标题左侧用**蓝青双色斜线**（`slashes.svg`）。

| 层级 | 用法 | 装饰位置 |
|---|---|---|
| 一级标题 | `.sd-title-cut`（或 `.sd-page-head h1`，自动带） | **右侧**切割元素 · 品牌蓝 |
| 二级标题 | `.sd-title-slashes` | **左侧**双斜线 |
| 卡片标题 | `.sd-card__title`（默认已带） | **左侧**双斜线 |

- **等比缩放 / 不裁剪**：一律 `background: … no-repeat center / contain`，比例恒定不被裁切。
- **高度随字号**：一级标题 `block-size: 0.72em`（约等于中日韩字形高）、二级与卡片标题 `block-size: 1.06em`，随字号自动缩放。
- **宽度**：按素材比例给（切割 `0.3919em`、双斜线 `0.65em`）。
- **线宽怎么调**（重要）：切割元素是 `background-image`，**CSS 改不了它的 stroke**。线宽由素材自身的 `stroke-width` 决定：

  | 线宽（相对原版） | stroke-width | viewBox | inline-size |
  |---|---|---|---|
  | 1× （现仅用于分页/插图） | 13.27 | `-7 -7 94.44 164.46` | 0.574em |
  | **1.5×（当前采用）** | 30.61 | `-0.5 -15.8 99.11 182.07` | **0.3919em** |
  | 2× | 43.75 | `-0.5 -22.38 106.7 195.21` | 0.3935em |

  2× 版的 base64 已备在 `components.css` 末尾注释里，替换 `.sd-title-cut::after` / `.sd-page-head h1::after` 两处即可。
  自建素材时：改 `stroke-width` 后 **viewBox 必须同步放大**（半宽 = stroke-width/2 向外扩），否则粗线会被裁掉。
- **间距按规范**：一级标题 `--space-3`（12px）；二级与卡片标题 `--space-2`（8px）。
- **垂直居中（v1.9 引入，v1.12 更名为 `--ink-nudge`）**：装饰元素默认带
  `transform: translateY(var(--ink-nudge))`，token 默认值 `0.05em`。
  为什么需要：`align-items: center` 居中的是**行框**，而中日韩字形的**墨迹中心**比行框中心低约 0.05em，
  不加微调时装饰线会偏高 1~2px（字号越大越明显）。Chrome 实测残差：

  | 字号 | 修正前偏高 | 加 0.05em 后残差 |
  |---|---|---|
  | 40px | +2.01px | +0.01px |
  | 32px | +2.00px | +0.40px |
  | 24px | +1.51px | +0.31px |
  | 20px | +1.01px | +0.01px |
  | 16px | +1.51px | +0.71px |
  | 14px | +0.51px | −0.19px |

  若目标页字体不同（尤其含拉丁字母的标题），可整体微调这一个值：在 `:root` 覆盖
  `--ink-nudge: 0.06em;` 之类即可，全站装饰元素与反馈卡图标同步生效。
- **纯文字标题**：卡片标题不想带斜线时加 `.sd-card__title--plain`。

## 布局与响应式（v1.17）
设计基准：1440×900 设计稿 / 正文 14px。
- **内容区无宽度上限，随视口等宽铺满**：`--content-max: 100%` —— 侧栏 232px 与左右内边距 24px 之外的宽度全部给内容；长文阅读型页面用 `.sd-content--readable`（或正文段落 `max-width: 76ch`）控行宽。
- **侧栏展开宽统一 232px**，各断点一致。
- **大屏放大字号与间距**：≥1920px ×1.10；≥2560px（2K）×1.22；内容区继续等宽铺满。≤992px 侧栏收起 + 栅格塌到 12 列；≤768px 单列。
- 字号、间距、内容宽度均已 token 化（`--fs-*` / `--space-*` / `--content-max` / `--sidebar-w`），只需在媒体查询内重定义 `:root` 变量，全站引用处自动生效。
- 若在 2K/4K 屏仍觉偏小，多为 **Windows 系统缩放为 100%** 所致；可优先把系统缩放调到 125%/150%（对所有应用统一生效）。

## 字体规则（v1.17）
- **标题 / 品牌字** → `--font-display`（MiSans）
- **正文 / 界面文字 / 一切装饰性文字**（编号、图注、标签、说明、元数据）→ `--font-sans`（系统无衬线栈）
- **代码 / 需纵向对齐的数值** → `--font-mono`（受限例外：行内代码、代码块、表格数字列、图表数值、色值 HEX）
- 禁止：装饰性文字使用等宽字体（mono）或任何第三字族。

## 数值层级（v1.17）

看板与数据卡里**数值是最重要的一行**，层级高于同卡标题。三层口径已统一：

| 层级 | 类 | 字号 / 行高 / 字重 | 用途 |
|---|---|---|---|
| **L1 主数值** | `.sd-swatch__val` · `.sd-card__value` · `.sd-feature-card__value` | **28 / 1.43 / 600** | 色卡、数据卡、功能卡主数值 —— 与「字体排印」**H1 页面主标题**同规格 |
| **L2 统计值** | `.sd-stat__value` | **24 / 1.3 / 600** | 统计卡与指标组的中等数值，默认取界面主色 |
| **L3 表格与图表数字** | `.sd-table .num` · `.sd-chart__barval` | 13 / 11 · 400 | 表格数字列、图表轴与数值标签（`--font-mono` 授权范围） |

```html
<div class="sd-card">
  <div class="sd-card__title">本月工单</div>
  <div class="sd-card__value">1,284</div>        <!-- L1：28 / 600 -->
  <div class="sd-stat__value">86.4%</div>        <!-- L2：24 / 600，取界面主色 -->
</div>
```

**要点**
- 三个层级全部走 `--font-num`（= `--font-sans`）+ `font-variant-numeric: tabular-nums`，保证多行数字字宽一致、右对齐不参差。
- L1 用 **600** 而非 H1 的 500 —— 规范中 Semibold 600 明确授权用于英文与数字加粗。
- **数值串建议 ≤ 8 字符**；更长的说明（单位构成、口径注释）放说明行或 `.sd-swatch__desc`，不要塞进数值本身。
- 两个数值类叠用时后者覆盖前者，别把 `.sd-card__value` 与 `.sd-stat__value` 放在同一元素上。

## 仪表盘 Gauge（v1.17）

单值 KPI 的标准形态，结构参考 Arco / VChart Gauge。颜色**一律按图表系列色顺序**取用，禁止硬编码 hex。

### 结构

```html
<div class="sd-gauge sd-gauge--1">
  <div class="sd-gauge__fig">
    <svg class="sd-chart" viewBox="0 0 200 200" role="img" aria-label="设备综合效率 78%">
      <circle class="sd-gauge__track" cx="100" cy="100" r="74"
              transform="rotate(135 100 100)" stroke-dasharray="348.72 464.96"/>
      <circle class="sd-gauge__arc" cx="100" cy="100" r="74"
              transform="rotate(135 100 100)" stroke-dasharray="271.99 464.96"/>
      <text class="sd-gauge__value" x="100" y="104" text-anchor="middle">78<tspan class="sd-gauge__unit" dx="2">%</tspan></text>
      <text class="sd-gauge__label" x="100" y="126" text-anchor="middle">设备综合效率</text>
    </svg>
  </div>
</div>
```

### 几何常量（照抄即可）

| 项目 | 270° 环（默认） | 180° 半环 |
|---|---|---|
| `transform` | `rotate(135 100 100)` | `rotate(180 100 100)` |
| 整弧弧长 | **348.72** | **232.48** |
| 圆周长（dasharray 第二值） | 464.96 | 464.96 |
| 半径 / 环厚 | 74 / 16 | 74 / 16 |
| 起点 → 终点 | 135° → 405°（开口向下） | 180° → 360°（开口向下） |

进度弧写法：`stroke-dasharray="整弧弧长 × 值% 464.96"`。**0% 时给 `__arc` 加 `opacity="0"`** —— 端帽是圆头，不加会在起点留一个残点。

### 颜色变体（按系列色顺序）

| 变体 | 取色 |
|---|---|
| `--1` | `--chart-series-1` 蓝 |
| `--2` | `--chart-series-2` 青 |
| `--3` | `--chart-series-3` 黄 |
| `--4` | `--chart-series-4` 绿 |
| `--other` | `--chart-other` 冷灰（灰度 / 无效态） |

变体类挂在**容器**上（`.sd-gauge.sd-gauge--2`），不是挂在 `<circle>` 上。

### 分段仪表 `--multi`

各段一条 `<path>`，按数据比例分配弧长，段间留 **2°** 缺口，颜色从 `--1` 起按序取用。底轨自动压到 `--sd-gray-1`，缺口自然显形。

```html
<div class="sd-gauge sd-gauge--multi">
  <div class="sd-gauge__fig">
    <svg class="sd-chart" viewBox="0 0 200 200">
      <circle class="sd-gauge__track" cx="100" cy="100" r="74"
              transform="rotate(135 100 100)" stroke-dasharray="348.72 464.96"/>
      <path class="sd-gauge__seg sd-stroke-1" d="M47.67 152.33 A74.0 74.0 0 0 1 79.35 28.94"/>
      <path class="sd-gauge__seg sd-stroke-2" d="M81.85 28.26 A74.0 74.0 0 0 1 158.15 54.24"/>
      <path class="sd-gauge__seg sd-stroke-3" d="M159.72 56.3 A74.0 74.0 0 0 1 172.56 114.53"/>
      <path class="sd-gauge__seg sd-stroke-4" d="M172.01 117.05 A74.0 74.0 0 0 1 152.33 152.33"/>
      <text class="sd-gauge__value" x="100" y="104" text-anchor="middle">45<tspan class="sd-gauge__unit" dx="2">%</tspan></text>
      <text class="sd-gauge__label" x="100" y="126" text-anchor="middle">运行占比</text>
    </svg>
  </div>
</div>
```

各段角度的算法：可用角度 = `270 − 2 × (段数−1)`，第 i 段弧长 = `可用角度 × 该段值 / 总和`，依次从 135° 顺时针排布。

### 指针仪表 `--needle`

180° 半环 + 外圈刻度 + 指针 + 轴心，适合需要「读刻度」的工况量（转速 / 压力 / 负载）。

- 刻度：每 **30°** 主刻度（`__tick--major`，r 86→93）+ 每 **10°** 次刻度（r 87→91），共 7 主 + 12 次
- 指针：`__needle`，从轴心指向 `180° + 180° × 值%`，长度 56
- 轴心：`__pivot`（r 6，`--sd-gray-8`）+ `__pivot-core`（r 2.5，白）
- 上下限文字 `__bound` 置于弧两端（0 / 100）

**`--needle` 会把进度弧端帽转成直角** —— 圆头会沿弧多伸 8px（≈6°），与指针读数对不上。

### 规格

| 项目 | 取值 |
|---|---|
| 环厚 / 半径 | 16px / 74（viewBox 200） |
| 底轨 | `--sd-gray-2`（分段形态用 `--sd-gray-1`） |
| 进度弧端帽 | 圆头（指针形态直角） |
| 中心数值 | `--fs-28` / 600 / **等宽数字** |
| 单位 | `--fs-12` / 500 / `--sd-gray-8` |
| 标题 | `--fs-12` / `--muted` |
| 上下限刻度 | `--fs-11` / `--muted` |
| 画布 | `__fig` 最大宽 220px（≤768px 收至 180px） |

### 可访问性

SVG 根节点带 `role="img"` + `aria-label="<指标> <值><单位>"`。仪表盘只呈现单一数值，**若该数值对业务关键，请在旁边补文字读数或明细表**。

## 横向柱状图（v1.17）

用于**排名 / TOP N** 场景（操作员完成量、设备产量、区域销量等）。来源：中车油压减振器作业系统「操作员完成量」排行图，颜色已按图表系列色重定。

```html
<svg class="sd-chart sd-chart-anim" viewBox="0 0 640 236" role="img" aria-label="操作员完成量排行">
  <defs>
    <linearGradient id="bh-grad" x1="0" y1="0" x2="1" y2="0">
      <stop offset="0" class="sd-grad-1"/><stop offset="1" class="sd-grad-2"/>
    </linearGradient>
  </defs>
  <line class="sd-chart__grid sd-anim-fade" x1="64" y1="8" x2="64" y2="206"/>
  …
  <g class="sd-anim-seq">
    <g class="sd-chart__hrow">
      <rect class="sd-chart__hhit"   x="8" y="14" width="624" height="28" rx="6"/>
      <rect class="sd-chart__hhband" x="8" y="14" width="624" height="28" rx="6"/>
      <text class="sd-chart__rowlabel sd-anim-fade" x="56" y="32" text-anchor="end">朱军</text>
      <path class="sd-chart__barh sd-anim-bar-x" style="fill:url(#bh-grad)"
            d="M64,18 H543.2 A3,3 0 0 1 546.2,21 V32 A3,3 0 0 1 543.2,35 H64 Z"/>
      <text class="sd-chart__barval sd-anim-fade" x="554.2" y="32" text-anchor="start">18</text>
    </g>
    … 其余行
  </g>
</svg>
```

### 规格

| 项目 | 取值 |
|---|---|
| 行距 / 柱厚 | 32px / **20px**（与纵向柱一致） |
| 值端圆角 | **3px**（与纵向柱顶角一致），**只圆右端** —— 用 `<path>` 画，`<rect rx>` 会两端都圆 |
| 行名 | `--fs-12` · `--text-secondary` · `text-anchor:end` |
| 柱端数值 | `--fs-11` · `--muted` · **等宽数字** · `text-anchor:start` · 贴柱端右侧 8px |
| 柱体渐变 | `.sd-grad-1` → `.sd-grad-2`（系列色 1→2），横向（`x1=0 x2=1`）逐柱铺展 |
| 行 hover | `--sd-gray-1` 底块（28px 高、圆角 6px）+ 行名/数值转 `--sd-gray-9`，纯 CSS |
| 网格线 | 复用 `.sd-chart__grid` —— 值轴在横轴，故网格线为**竖线** |

### ⚠️ 渐变必须用内联 style

```html
<!-- ✗ 静默失效：呈现属性会被 CSS 类的 fill 覆盖 -->
<path class="sd-chart__barh" fill="url(#bh-grad)" d="…"/>

<!-- ✓ 正确 -->
<path class="sd-chart__barh" style="fill:url(#bh-grad)" d="…"/>
```

`.sd-chart__barh` 在 CSS 里声明了默认 `fill: var(--chart-series-1)`；**CSS 规则的优先级高于 SVG 呈现属性**，所以写在属性上的 `fill="url(#id)"` 会被默认系列色覆盖，图表看起来"没有渐变"却不报错。同类问题在任何「既给了类又给了 `fill` 属性」的 SVG 元素上都可能出现。

## 图表出生动效（v1.17）

全部图表统带的入场动效，**纯 CSS、零 JS**。

### 用法

| 类 | 挂在哪 | 效果 | 必需变量 |
|---|---|---|---|
| `.sd-chart-anim` | `<svg class="sd-chart">` | 整体淡入 + 上移 8px | — |
| `.sd-anim-fade` | 网格线 / 轴标签 / 图例 / 文字 | 淡入 | — |
| `.sd-anim-line` | 折线 `<path>` | 从左端描线生长 | `--len` = 路径长度 |
| `.sd-anim-area` | 面积 `<path>` | 淡入 + 轻微上移 | — |
| `.sd-anim-bar-y` | 纵向柱（或「一整列」的 `<g>`） | 从底部生长 | — |
| `.sd-anim-bar-x` | 横向柱 | 从左侧生长 | — |
| `.sd-anim-arc` | 环形 / 仪表弧 | 从起点扫出 | `--len`；定位偏移用 `--off` |
| `.sd-anim-needle` | 仪表指针 | 从 0% 扫到读数 | `--rot` = 相对 12 点的偏角 |

### 时长与错峰

- 生长类 `--duration-slow`（600ms）；描线 / 扫弧 / 淡入类 `--duration-draw`（900ms）
- 错峰：把 `.sd-anim-seq` 挂到**包住一组图形的 `<g>`** 上，子元素按 DOM 顺序每级递增 40ms（1–12 级 = 0/40/…/440ms，第 13 个起固定 480ms）
- 单个元素精确控制：内联 `style="--sd-delay:180ms"`

### 取 `--len` 的两种办法

1. 折线：在浏览器里 `document.querySelector('path').getTotalLength()`，取整后填 `--len`
2. 弧：圆取 `2πr × 角度/360`；环形图分段直接用它自己的 `stroke-dasharray` 首值

### 三条实现要点

1. **纵向柱把整列包进 `<g>`**：`.sd-anim-bar-y` 挂在 `<g>` 上时整列作为一个整体从柱底生长；挂在单个柱段上则每段各自原地"展开"，堆叠柱看起来会不自然。
2. **弧的分段定位用 `--off`**：环形图各段靠 `stroke-dashoffset` 排在圆周不同位置；把该值填进 `--off`，动画终点正好落在元素的自然状态，不会打乱分段顺序。
3. **`prefers-reduced-motion` 自动关闭**：全局规则把 `animation` 置为 `none`，元素回到自然状态（图形完整显示），无需额外处理。

## 让动效可见（v1.17）

**问题**：长页面里图表的入场动效在页面加载时就播完了，用户滚到跟前已经看不到。
**做法**：给图表容器加 `.sd-anim-reveal`。

### 用法一：配合一小段脚本（推荐，全部浏览器可用）

```html
<div class="sd-chart-card sd-anim-reveal"> …图表… </div>

<script>
(function () {
  var SEL = '.sd-anim-reveal';
  var boxes = [].slice.call(document.querySelectorAll(SEL));
  if (!boxes.length) return;
  boxes.forEach(function (b) { b.classList.add('js-ready'); });   // 先暂停在起点
  if (!('IntersectionObserver' in window)) {
    boxes.forEach(function (b) { b.classList.add('is-inview'); });
    return;
  }
  var io = new IntersectionObserver(function (es) {
    es.forEach(function (e) {
      if (e.isIntersecting) { e.target.classList.add('is-inview'); io.unobserve(e.target); }
    });
  }, { threshold: 0.12 });
  boxes.forEach(function (b) { io.observe(b); });
  // 兜底：只在观察器失效时放行（用必定可见的 body 探测），
  // 不要无条件定时放行 —— 否则下方图表会在滚到之前就播完动效
  var ioOK = false;
  var probe = new IntersectionObserver(function () { ioOK = true; probe.disconnect(); }, { threshold: 0 });
  probe.observe(document.body);
  setTimeout(function () { if (!ioOK) boxes.forEach(function (b) { b.classList.add('is-inview'); }); }, 1500);
})();
</script>
```

**关键设计**：`.js-ready` 由脚本添加 —— 所以脚本失效时选择器不匹配，动效退回「加载即播」，
**不会出现动画被暂停、图表永远不可见的死状态**。

### 用法二：零脚本

什么都不用加。支持滚动驱动动画的浏览器（Chrome / Edge 115+）会自动走 `@supports (animation-timeline: view())` 分支：
容器建立 view 时间线，图表滚动到视口即播。老浏览器退回「加载即播」。

### 用法三：不做处理

不加 `.sd-anim-reveal`，保持页面加载即播（短页面 / 首屏图表适合这样）。

### 注意

- 系统开启「减少动态效果」时，动效会被 `prefers-reduced-motion` 全局关闭 —— 这是**正确行为**，不要为了展示去覆盖它。
  设计稿预览/演示页里如果需要强制查看，应在页面上单独做一次性开关，且明确标注「仅演示」。
- 一个容器只观察一次（`unobserve`），避免上下滚动反复重播。

## 口径与边界
- 组件对比度均满足 WCAG AA（主按钮白字 4.18:1、Tag/Alert 文字底色 ≥4.5:1、正文 12.19:1）。
- 基础 reset 会重置 `body`/标题/盒模型；套用后请目视回归。
- 已落地：组件（Button / Tabs / Input / Checkbox / Radio / Switch / Table / Tag / Badge / Alert / Message / Pagination / Breadcrumb / Progress）+ **布局骨架 / 栅格 / 卡片 / 动效工具类**。**Modal / Drawer / Tooltip / Tree / Skeleton / Upload / DatePicker 尚未纳入**，遇到按现有 token 手写或暂留原样。
- 表格：**每行（含最后一行）底部均有 1px 描边**；表头底色 `--sd-gray-1`、行分隔 `--sd-gray-2`，统一走 `border-bottom`（不用顶边实现）。
- 未放 MiSans 字体时，标题回退系统无衬线（PingFang SC / Microsoft YaHei），功能正常。
