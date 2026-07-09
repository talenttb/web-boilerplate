---
name: css
description: 本專案撰寫或 review 任何 CSS／樣式／版面前必載入。觸發詞：CSS、stylesheet、nesting selector、semantic tag、component、theme variable、data-attribute 狀態、font-size／間距 scale、reset、響應式、app.css/demo.css。新增 view／component、改色／改版面、加狀態樣式時都適用。
---

# web-boilerplate CSS 慣例

## 核心原則

- **Component 用單一 class 圈範圍**,所有樣式巢狀其下,不外洩、不 override 別的 component。
- **結構用 semantic tag**(`header/section/article/nav/figure/dl/table/time/meter/details`),不堆無語意 `<div>`。
- **狀態/變體用 `[data-*]` 值或 ARIA**,不開新 class。
- **色彩/字級/間距/圓角全走 CSS 變數**——`app.css` 的 `:root` 是單一來源,跟 `[data-theme]` 自動切換,禁止硬寫色碼或 px。
- **`#id` 只做定址**(JS `getElementById`、錨點、`aria-*`/`for`),不當樣式 handle。
- 全程**新版 nesting selector(`&`)**,不寫扁平後代;一個 component 一個檔/區段。

`app.css` 全頁共載,各 view 專屬 css(如 `demo.css`)疊其上;`:root` 變數與 reset 只在 `app.css` 定義一份。

## Reset 標準

只有一份 reset,放 `app.css` 頂部。目標引擎只有 **Blink(Mac Chrome/Edge)** 與 **WebKit(iPhone Safari/Chrome)**,故只留通用規則 + `-webkit-`/iOS 修正,不含 Firefox `-moz-` 與用不到的 date/time picker:

```css
*, ::before, ::after, ::backdrop {
  box-sizing: border-box; margin: 0; padding: 0; border: 0 solid;
}
html {
  line-height: 1.5; tab-size: 4;
  -webkit-text-size-adjust: 100%;
  -webkit-tap-highlight-color: transparent;
}
h1, h2, h3, h4, h5, h6 { font-size: inherit; font-weight: inherit; }
a { color: inherit; text-decoration: inherit; }
table { text-indent: 0; border-color: inherit; border-collapse: collapse; }
ol, ul, menu { list-style: none; }
img, svg, video, canvas, audio, iframe, embed, object { display: block; vertical-align: middle; }
img, video { max-width: 100%; height: auto; }
button, input, select, optgroup, textarea {
  font: inherit; color: inherit; letter-spacing: inherit;
  border-radius: 0; background-color: transparent;
}
button, input:where([type="button"], [type="reset"], [type="submit"]) { appearance: button; }
::placeholder { opacity: 1; }
textarea { resize: vertical; }
[hidden]:where(:not([hidden="until-found"])) { display: none !important; }
```

`-webkit-text-size-adjust`、`-webkit-tap-highlight-color`、`appearance: button` 是 iOS 必要修正。這份 reset 讓 form 元素自動繼承字體、heading/連結/清單/表格回到中性,component 內就不必再重抄這些 reset。

## Scale 標準

字級/間距/圓角**一律用 token**(宣告在 `app.css :root`,跟色彩同處),禁止硬寫 px。token 沿用既有收斂結果(非 16px base):

```css
/* font-size */
--size-2xs: 0.5625rem;  --size-xs: 0.625rem;   --size-sm: 0.6875rem;
--size-base:0.75rem;    --size-md: 0.8125rem;  --size-lg: 0.875rem;
--size-xl:  1rem;       --size-2xl:1.125rem;   --size-3xl:1.25rem;

/* line-height */
--lh-tight: 1.1;  --lh-snug: 1.25;  --lh-normal: 1.5;

/* spacing */
--space-0_5: 2px;  --space-1: 4px;   --space-1_5: 6px;  --space-2: 8px;
--space-2_5: 10px; --space-3: 12px;  --space-3_5: 14px; --space-4: 16px;
--space-5: 20px;   --space-6: 24px;

/* radius */
--radius-sm: 3px;  --radius: 4px;  --radius-md: 6px;  --radius-lg: 8px;  --radius-pill: 999px;
```

`--size-*` 對應 9/10/11/12/13/14/16/18/20px 九級。文字 line-height 預設繼承 reset 的 `1.5`,要更緊湊(數字、標題、hero)才配 `--lh-tight`/`--lh-snug`。用法:`font-size: var(--size-xs);`、`gap: var(--space-1_5);`、`border-radius: var(--radius-md);`。`.5px` 變體(9.5/10.5…)就近收斂到上面的 token。

**間距紀律**:間距由**外層 layout 用 `gap`(flex/grid)統一管**,component 不自帶 outer `margin`;要拉開兄弟元素就在父層加 `gap`,不在子層加 `margin-bottom`。

## Component

一個 component 由**單一 class** 圈住,整頁出現一次或多次都同一種寫法(如 `.balance-row`)。變體/狀態用 `[data-*]` 值或 ARIA,巢狀改子元素;互斥狀態用一個 `data-attr` 多值:

```css
.balance-row {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  padding: var(--space-2_5) var(--space-3);

  & [data-amount] {
    font-size: var(--size-xs);
    color: var(--text-secondary);
  }

  &[data-tone="positive"] { & [data-amount] { color: var(--accent-green); } }
  &[data-tone="negative"] { & [data-amount] { color: var(--accent-red); } }
}
```

對應 hiccup:`[:li.balance-row {:data-tone "positive"} [:span {:data-amount ""} "+$450"]]`——狀態切換只改 `data-tone`,CSS 自己 reflow。布林狀態用 `[data-active="true"]` / `[aria-pressed="true"]`。

**版面**:響應式優先 `grid` 自適應(`grid-template-columns: repeat(auto-fit, minmax(420px, 1fr))`)取代 `@media` 斷點,斷點只在真的需要時用。

**共用 utility**:跨 component 重複的東西抽成 `[data-*]` utility(如 `[data-num]` 右對齊 +`tabular-nums`),不要每個 component 各自重抄一份 button 樣式。無障礙隱藏文字用 `.sr-only`。

## 常見錯誤

| 症狀 | 修法 |
|---|---|
| 用 `#id` 當樣式 handle | 改 `.class`;`#id` 只留定址 |
| component 內層又開新 class | 用 nesting `&` + `[data-*]`,不堆 class |
| `font-size: 11px` / `padding: 10px` | 換 `var(--size-sm)` / `var(--space-2_5)` |
| 硬寫 `#fff` / `rgb(...)` | 換 `var(--...)`;沒有的色先進 `app.css :root` |
| 子元素加 `margin-bottom` 拉間距 | 改父層 `gap` |
| 在 `demo.css` 又寫一份 `:root` 色或 reset | 刪掉,只在 `app.css` 一份 |
| 樣式沒生效 | 查 CSS selector(如 `[data-tone="error"]`)是否與 hiccup 實際 attribute 一致——常見是誤用 `:class "error"` |
| 巢狀寫成 `.a .b`(扁平後代) | 改 `.a { & .b { } }`,維持 component 內聚 |
