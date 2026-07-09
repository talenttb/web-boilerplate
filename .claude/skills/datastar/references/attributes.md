# Datastar 前端屬性參考（v1.0）

## 狀態管理屬性

### data-signals

定義反應性信號。

```html
<div data-signals:count="0"></div>
<div data-signals:user="{name: 'Alice', age: 30}"></div>
<div data-signals="{foo: 1, bar: 2}"></div>
```

### data-computed

建立計算屬性（read-only），自動追蹤依賴。

```html
<div data-computed:total="$price * $quantity"></div>
<div data-computed:fullName="$firstName + ' ' + $lastName"></div>
```

### data-bind

雙向綁定表單元素與信號。

```html
<input data-bind:username />
<input type="checkbox" data-bind:agreed />
<select data-bind:country>...</select>
<textarea data-bind:content></textarea>
```

### data-ref

建立元素參照（實為 signal）。

```html
<div data-ref:container></div>
<input data-ref:inputEl />
```

### data-json-signals（1.0 新增）

把目前的 signals 樹以 reactive JSON 顯示在該元素內，除錯用。

```html
<pre data-json-signals></pre>
<pre data-json-signals="{include: /^form/}"></pre>
```

## DOM 綁定屬性

### data-text

綁定元素文字內容。

```html
<span data-text="$message"></span>
<p data-text="'Count: ' + $count"></p>
```

### data-class

條件式 CSS class。

```html
<div data-class:active="$isActive"></div>
<div data-class:hidden="!$visible"></div>
<button data-class:loading="$isFetching"></button>
```

### data-style

動態 inline style。

```html
<div data-style:opacity="$fade"></div>
<div data-style:display="$show ? 'block' : 'none'"></div>
<div data-style:background-color="$bgColor"></div>
```

### data-show

顯示/隱藏元素。

```html
<div data-show="$isLoggedIn">Welcome!</div>
<div data-show="$items.length > 0">...</div>
```

### data-attr

動態設定任意 HTML 屬性。

```html
<input data-attr:disabled="$isSubmitting" />
<a data-attr:href="'/user/' + $userId">Profile</a>
<img data-attr:src="$imageUrl" />
```

#### ⚠️ 雷：boolean expr 會被設成空字串，不是 `"true"`

`data-attr` 對求值結果的處理（v1.0 bundle source）：

```js
// expr 求值結果 = c
c === "" || c === true  ? el.setAttribute(name, "")      // ← boolean true → 空字串 ""
: c === false || c == null ? el.removeAttribute(name)    // ← false/null → 直接移除
: typeof c === "string" ? el.setAttribute(name, c)       // ← 字串 → 照設
```

所以 `data-attr:aria-pressed="$market === 'stock'"`（expr 回傳 **boolean**）會把 active 元素設成 `aria-pressed=""`、inactive 直接拿掉屬性。結果：

- CSS `[aria-pressed="true"]` **永遠不 match**（值是 `""` 不是 `"true"`）
- `aria-pressed=""` 對 enumerated ARIA 屬性也是**不合法值**（應為 `"true"/"false"/"mixed"`）

**正解：要 match 特定字串值（aria-pressed / aria-expanded / aria-checked，或任何 `[attr="x"]` CSS），expr 必須回傳字串：**

```clojure
;; ✗ boolean → aria-pressed=""，CSS [aria-pressed="true"] 不中
[:button {:data-attr:aria-pressed "$market === 'stock'"} "股票"]

;; ✓ 字串 → aria-pressed="true"/"false"，CSS 正常、ARIA 也合法
[:button {:data-attr:aria-pressed "$market === 'stock' ? 'true' : 'false'"} "股票"]
```

> 反過來說：純 boolean 屬性（`disabled` / `hidden`）用 boolean expr 是對的（`true`→存在、`false`→移除）；而「存在即生效」的 CSS（`[streaming]`）配 boolean 也沒事。**只有「要比對特定值」的場景才會中這個雷。**

## 事件屬性

### data-on

事件監聽器。

```html
<button data-on:click="$count++">+1</button>
<button data-on:click="@get('/api/refresh')">Refresh</button>
<input data-on:input="$search = this.value" />
<form data-on:submit__prevent="@post('/api/submit')">...</form>
```

**修飾符（v1.0 release）：用 `__` 分隔修飾符，`.` 分隔修飾符的內部值。**

- `__prevent` - preventDefault()
- `__stop` - stopPropagation()
- `__once` - 只觸發一次
- `__debounce.500ms` - 防抖
- `__throttle.500ms` - 節流

> 注意：1.0 RC 時期是用 `.prevent` / `.debounce.500ms`，1.0 release 改成 `__`。寫成舊語法時 Datastar 會把整段 `submit.prevent` 當成事件名 → 修飾符不會生效。

### data-on-intersect

元素進入視窗時觸發。

```html
<div data-on-intersect="@get('/lazy-content')">Loading...</div>
<img data-on-intersect__once="$loaded = true" />
```

### data-on-interval

定時觸發。

```html
<div data-on-interval__duration.1000ms="@get('/api/poll')"></div>
<div data-on-interval__duration.5s="$elapsed++"></div>
```

### data-on-signal-patch

任何 signal 變更時觸發。

```html
<div data-on-signal-patch="console.log('signals changed')"></div>
```

### data-on-signal-patch-filter（1.0 新增）

與 `data-on-signal-patch` 搭配，用 regex 過濾哪些 signal 才會觸發 callback。

```html
<div data-on-signal-patch="@post('/save')"
     data-on-signal-patch-filter="{include: /^form\\./}"></div>
```

## 工具屬性

### data-init

元素掛載到 DOM 時執行一次。

```html
<div data-init="$loaded = true"></div>
<div data-init="@get('/api/init')"></div>
```

### data-effect

副作用，依賴的 signal 變更時重新執行。

```html
<div data-effect="console.log($count)"></div>
<div data-effect="document.title = $pageTitle"></div>
```

### data-ignore

忽略元素及其子元素，Datastar 不處理。

```html
<div data-ignore>
  <!-- Datastar 不會處理這裡 -->
</div>
```

### data-ignore-morph

morph 時保留元素狀態（不被 patch 覆寫）。

```html
<video data-ignore-morph>...</video>
<div data-ignore-morph>preserve this</div>
```

### data-preserve-attr

morph 時保留特定屬性的現值（常用於 `<details open>`、`<input value>`）。

```html
<details open data-preserve-attr="open">...</details>
<input data-preserve-attr="value" />
```

### data-indicator

追蹤 fetch 請求狀態的 signal。

```html
<button data-indicator:loading data-on:click="@get('/api')">
  <span data-show="$loading">Loading...</span>
  Submit
</button>
```

## Actions 參考

### HTTP Actions

```html
@get(url, opts?)
@post(url, opts?)
@put(url, opts?)
@patch(url, opts?)
@delete(url, opts?)
```

送出 HTTP 請求，request body/query 自動帶上目前的 signals，回應以 SSE 串流方式驅動 `patch-elements` / `patch-signals`。

### HTTP opts

| 選項 | 型別 / 值 | 說明 |
|------|----------|------|
| `contentType` | `'json'`（預設）/ `'form'` | 請求 body 格式 |
| `headers` | map | 自訂 header |
| `filterSignals` | `{include: RegExp, exclude?: RegExp}` | 只送匹配的 signal |
| `selector` | CSS selector | 把 form 元素當作 payload 來源 |
| `payload` | any | 直接覆寫 fetch body |
| `openWhenHidden` | boolean | 分頁切到背景時仍保持連線 |
| `retry` | `'auto' \| 'error' \| 'always' \| 'never'` | 斷線重試策略 |
| `retryInterval` | ms | 起始重試間隔 |
| `retryScaler` | number | 每次重試間隔倍率 |
| `retryMaxWait` | ms | 單次重試上限 |
| `retryMaxCount` | number | 重試次數上限 |
| `requestCancellation` | `'auto' \| 'cleanup' \| 'disabled'` / `AbortController` | 取消策略 |

```html
<button data-on:click="@get('/api', {headers: {'X-Custom': 'v'}})">Go</button>
<form data-on:submit.prevent="@post('/submit', {contentType: 'form'})">...</form>
<div data-on-interval.5s="@get('/poll', {retry: 'always', retryMaxCount: 100})"></div>
```

### 工具 Actions

```html
@peek(() => $value)                 <!-- 讀取但不追蹤依賴 -->
@setAll(true, {include: /^is/})     <!-- 批次設定匹配的 signals -->
@toggleAll({include: /^show/})      <!-- 批次切換 boolean signals -->
```

## Pro 屬性（商業版）

以下屬性需 Datastar Pro，一般自架 CDN 版**沒有**。整合時若需要對應功能，請改用上方 core 屬性或自行處理。

| 屬性 | 用途 |
|------|------|
| `data-animate` | 屬性 / style tween 動畫 |
| `data-custom-validity` | 自訂 form 驗證訊息 |
| `data-match-media` | 把 media query 映射到 signal |
| `data-on-raf` | `requestAnimationFrame` 時觸發 |
| `data-on-resize` | 元素尺寸變更時觸發 |
| `data-persist` | signal 同步到 localStorage/sessionStorage |
| `data-query-string` | signal 同步到 URL query string |
| `data-replace-url` | 變更 URL（不 reload） |
| `data-scroll-into-view` | 將元素捲入視窗 |
| `data-view-transition` | View Transition API 整合 |

## Pro Actions（商業版）

| Action | 用途 |
|--------|------|
| `@clipboard(text, isBase64?)` | 複製到剪貼簿 |
| `@fit(v, oldMin, oldMax, newMin, newMax, clamp?, round?)` | 線性區間映射 |
| `@intl(type, value, opts?, locale?)` | i18n 格式化（日期/數字/list/relative time） |

## 1.0 變更重點

- **新增**：`data-json-signals`、`data-on-signal-patch-filter`
- **移除**：`data-scope`
- **擴充**：HTTP action 的 retry / cancellation / openWhenHidden 等選項
