---
name: datastar
description: Use when building reactive web UIs with backend-driven updates via Datastar 1.0. Triggers: SSE streaming, real-time updates, data-* attributes, patch-elements, patch-signals, hypermedia, HTMX alternative, datastar-clojure SDK.
---

# Datastar（v1.0）

輕量級（約 11KB）超媒體框架，採用「後端驅動前端」模式。後端透過 SSE 推送 DOM 更新和信號變更，前端用 `data-*` 屬性定義反應性行為。本 skill 對齊 **Datastar 1.0** 與 Clojure SDK `1.0.0`。

## 核心概念

| 概念 | 說明 |
|------|------|
| **Signals** | 反應性狀態，用 `$` 前綴存取（如 `$count`） |
| **SSE Events** | 後端推送；1.0 只剩兩種：`datastar-patch-elements`（修改 DOM）、`datastar-patch-signals`（修改狀態） |
| **Expressions** | `data-*` 屬性中的 JS 表達式，自動追蹤依賴 |
| **執行 script / redirect** | 不再是獨立的 SSE event，改由 SDK sugar 透過 `patch-elements!` 注入 `<script>` 實作 |

## 前端載入

```html
<script type="module"
        src="https://cdn.jsdelivr.net/gh/starfederation/datastar@v1.0.0/bundles/datastar.js">
</script>
```

## Clojure SDK 快速參考

```clojure
;; deps.edn
;; {dev.data-star.clojure/sdk      {:mvn/version "1.0.0"}
;;  dev.data-star.clojure/http-kit {:mvn/version "1.0.0"}}

(require '[starfederation.datastar.clojure.api :as d*]
         '[starfederation.datastar.clojure.adapter.http-kit :as hk])

(defn handler [request]
  (hk/->sse-response request
    {hk/on-open
     (fn [sse]
       (d*/with-open-sse sse
         (d*/patch-elements! sse "<div id=\"target\">Hello</div>")))
     hk/on-close
     (fn [sse status]
       (println "closed" status))}))
```

### SDK 函數

| 函數 | 用途 |
|------|------|
| `d*/patch-elements!` | 修補 DOM 元素（需有 id 或用 `d*/selector`） |
| `d*/patch-elements-seq!` | 批次送多個元素字串 |
| `d*/patch-signals!` | 修補 signals（RFC 7386 JSON Merge Patch） |
| `d*/remove-element!` | 移除元素 |
| `d*/execute-script!` | 執行 JS（透過注入 `<script>` 實作） |
| `d*/redirect!` / `console-log!` / `console-error!` | `execute-script!` sugar |
| `d*/close-sse!` | 關閉連線 |
| `d*/with-open-sse` | macro，finally 自動 close |
| `d*/lock-sse!` | macro，加鎖避免多 thread 寫入交錯 |
| `d*/get-signals` | 從 request 取 signals（回傳 JSON 字串，需自行 parse） |
| `d*/datastar-request?` | 判斷是否為 Datastar 請求 |
| `d*/sse-get` / `sse-post` / `sse-put` / `sse-patch` / `sse-delete` | 產生 action 表達式字串 |

### patch-elements! 選項

**重要**：選項 map 的 key 是 `d*/` namespace 下的 **def**（namespaced keyword），**不是** `:selector` / `:mode`。

```clojure
(d*/patch-elements! sse html)
(d*/patch-elements! sse html
  {d*/selector   "#target"
   d*/patch-mode d*/pm-inner})
```

| Option | 值 | 說明 |
|--------|-----|------|
| `d*/selector` | CSS selector | 目標元素（沒填則用 HTML 根元素的 id） |
| `d*/patch-mode` | `d*/pm-outer` | 替換外層 HTML（預設，會 morph） |
| | `d*/pm-inner` | 替換內層 HTML |
| | `d*/pm-replace` | 直接整個替換，**不 morph**（1.0 新增） |
| | `d*/pm-prepend` | 前置到子項 |
| | `d*/pm-append` | 附加到子項 |
| | `d*/pm-before` | 插入為前兄弟 |
| | `d*/pm-after` | 插入為後兄弟 |
| | `d*/pm-remove` | 移除元素 |
| `d*/use-view-transition` | boolean | 啟用 View Transition API |
| `d*/id` / `d*/retry-duration` | — | SSE event id / reconnect 間隔 |

### patch-signals! 選項

```clojure
(d*/patch-signals! sse (json/write-value-as-string {:count 42}))
(d*/patch-signals! sse (json/write-value-as-string {:x 0})
  {d*/only-if-missing true})
```

`patch-signals!` 遵循 RFC 7386 JSON Merge Patch：**value 為 `null` 代表「刪除該 signal」**——這是功能，是移除 signal 的正規手段，刪掉後該 signal 的前端綁定即失效。要「清空值但保留 signal 與綁定」（如送出後清表單）用 `""`／`0`；注意 Clojure map 裡的 `nil` 經 JSON 序列化就是 `null`，等於下刪除指令。兩種都合法，依你要「刪」還是「清」自行選擇。

`patch-signals!` 需要 JSON 字串或可序列化成 JSON 物件的資料，SDK **不內建 JSON 序列化**，請自備 jsonista 等函式庫。

## 前端 data-* 屬性

### 狀態管理

```html
<div data-signals:count="0"></div>
<div data-computed:double="$count * 2"></div>
<input data-bind:name />
<pre data-json-signals></pre>    <!-- 1.0 新增：debug 顯示 signals -->
```

### Signal 名大小寫（server-rendered HTML 必讀）

HTML parser 會把屬性名小寫化：原始碼寫 `data-bind:memberName`，DOM 裡變成 `data-bind:membername`，signal 就成了 `$membername`，跟表達式裡的 `$memberName` 對不上。三種寫法實測（Datastar 1.0）：

| HTML 寫法 | 實際 signal |
|---|---|
| `data-bind:memberName` | `membername`（camelCase 遺失，勿用） |
| `data-bind:member-name` | `memberName`（kebab key 自動轉 camelCase） |
| `data-bind="memberName"` | `memberName`（值形式，屬性值保留大小寫） |

所有以 colon key 帶 signal 名的屬性（`data-bind:`／`data-signals:`／`data-computed:`／`data-ref:`／`data-indicator:`…）都適用同一規則：signal 名含大寫時，用 kebab-case key 或值形式。

本樣板 demo（旅費分帳）表單即用值形式。

### DOM 綁定

```html
<span data-text="$count"></span>
<div data-class:active="$isActive"></div>
<div data-show="$visible"></div>
<div data-style:opacity="$fade"></div>
```

### 事件處理

```html
<button data-on:click="$count++">+1</button>
<button data-on:click="@get('/api/data')">Fetch</button>
<div data-on-intersect="@get('/lazy-load')"></div>
<div data-on-interval__duration.1000ms="@get('/poll')"></div>
<div data-on-signal-patch="@post('/save')"
     data-on-signal-patch-filter="{include: /^form\\./}"></div>
```

### Actions（呼叫後端）

| Action | 用途 |
|--------|------|
| `@get(url, opts?)` | GET 請求 |
| `@post(url, opts?)` | POST 請求 |
| `@put(url, opts?)` / `@patch` / `@delete` | 對應方法 |
| `@peek(() => $x)` | 讀取但不追蹤依賴 |
| `@setAll(val, filter)` / `@toggleAll(filter)` | 批次設定/切換 signals |

HTTP action opts 新增：`retry`, `retryInterval`, `retryScaler`, `retryMaxWait`, `retryMaxCount`, `requestCancellation`, `openWhenHidden`, `selector`, `payload`。

詳細屬性與 action 參考見 @references/attributes.md 和 @references/clojure-sdk.md。

## Expression 語法（1.0）

`data-*` 屬性裡的 JS expression 可以用：

| 寫法 | 意義 |
|------|------|
| `$foo` | 讀 signal `foo`（仍用 `$` 前綴） |
| `$foo = 1` | 寫 signal |
| `el` | 當前元素（**不是** `$el`） |
| `evt` | 事件物件（**不是** `$event`） |
| `@action(...)` | 呼叫 action（`@get` / `@post` / `@peek` / `@setAll` 等） |

```html
<input data-on:input="$search = el.value" />
<button data-on:click="evt.preventDefault(); @post('/save')">Save</button>
<div data-text="el.offsetHeight"></div>
```

1.0 移除的「magic」：舊版 Alpine-style 的 `$el` / `$event` / `$refs` / `$watch` / `$$` 都拿掉了，改用上面這些明確變數。舊版 `data-scope="form"` + `$$name` 改以信號前綴慣例（直接用 `$form.name`）。

## Script / Redirect / Console（sugar over patch-elements!）

1.0 砍掉獨立的 `execute-script` / `redirect` SSE event，改由 `patch-elements!` 把 `<script>` append 到 `<body>`，瀏覽器 parse 時執行；預設 `auto-remove` 執行後移除。

```clojure
;; Inline JS
(d*/execute-script! sse "alert('hi')")
;; 實際送的是：patch-elements! "<script>alert('hi')</script>" {selector "body" mode :append}

;; 跳轉（內部用 setTimeout 避免同步執行時機問題）
(d*/redirect! sse "/new-page")

;; Console log/error（字串會被 quote 包住，別自己加引號）
(d*/console-log!   sse "saved")
(d*/console-error! sse "validation failed")

;; 保留 script 不移除（動態 import module）
(d*/execute-script! sse
  "import('/js/late.js').then(m => m.init())"
  {d*/auto-remove false
   d*/attributes  {"type" "module"}})
```

**踩雷點**：`console-log!` / `console-error!` 的實作是 `(str "console.log(\"" msg "\")")`，**字串內含雙引號會破掉**；要安全就自己 `d*/execute-script!` 傳已 escape 的 JS，或改走 `patch-signals!` 丟到 `data-effect` 裡 log。

## Query param + signal 整合（後端作為決策點）

**信號會自動隨 HTTP action 送到後端**：`@get` 放 query string、`@post/@put/@patch/@delete` 放 body。後端用 `d*/get-signals` 取得 JSON 字串，parse 後依信號值決定要 patch 什麼回去。這是 Datastar「後端作為 SoT」的主要機制 — **state 在信號、decision 在後端**，不要在前端自己算分支。

```html
<div data-signals="{status: 'all', q: '', page: 1}"></div>
<input data-bind:q data-on:input__debounce.300ms="@get('/tasks')" />
<select data-bind:status data-on:change="@get('/tasks')">
  <option value="all">全部</option>
  <option value="pending">待處理</option>
</select>
<button data-indicator:loading data-on:click="@get('/tasks')">
  <span data-show="$loading">...</span>Refresh
</button>
```

```clojure
(defn tasks-handler [request]
  (let [{:keys [status q page]}
        (some-> (d*/get-signals request)
          (json/read-value json/keyword-keys-object-mapper))]
    (hk/->sse-response request
      {hk/on-open
       (fn [sse]
         (d*/with-open-sse sse
           (d*/patch-elements! sse
             (render-tasks-list {:status status :q q :page (or page 1)}))))})))
```

URL 同步（free 版手寫、Pro 版用 `data-query-string` / `data-replace-url`）：

```html
<div data-effect="history.replaceState(null, '',
       '?status=' + encodeURIComponent($status))"></div>
```

## Streaming data 建議

`patch-signals!` 用 [RFC 7386 JSON Merge Patch](https://datatracker.ietf.org/doc/html/rfc7386)，**可且應該只送 delta**：

| 動作 | 送法 |
|------|------|
| 更新 `$market.AAPL.price` | `{:market {:AAPL {:price 101}}}`（其他 symbol 與 AAPL.volume 都不動） |
| 刪除 `$market.TSLA` | `{:market {:TSLA nil}}` |
| **Array 無法深合併** | 整個陣列會被覆蓋 → K/V map keyed by id/timestamp 更適合增量 |

### 適不適合用 Datastar 做 streaming？

| 場景 | 頻率 | 建議 |
|------|------|------|
| 持倉 / 帳戶 P&L | 1–5 Hz | ✅ Datastar 直送 |
| 盤中最後成交價（top-N symbol） | 5–30 Hz | ✅ Datastar + brotli + delta patch-signals |
| 完整 L2 order book / 每筆 tick / 大量 symbol | 30+ Hz | ⚠️ 臨界；brotli 後通常仍可接受。極端情境可「顯示層用 DS、計算層另開 WS」 |
| 二進位、雙向 low-latency | — | ❌ 用原生 WebSocket，不要塞 SSE |

### Candles 建模

即時推播若要增量，把 candles 存成 `{timestamp → bar}` 的 map 而不是 array，才吃得到 Merge Patch delta；歷史載入送 array 也行但每次都要全量送。

```clojure
;; ❌ array — 每次都要全量送
(d*/patch-signals! sse
  (json/write-value-as-string {:feed {:ohlc (all-candles sym)}}))

;; ✅ map keyed by timestamp — 只送最新一根
(d*/patch-signals! sse
  (json/write-value-as-string
    {:feed {:ohlc {(str last-ts) {:o 100 :h 101 :l 99 :c 100.5}}}}))
```

web-boilerplate 目前 `split-bill-handler` 一次送全部 array 適合歷史載入；即時更新建議另開 handler，走 map-keyed delta。

## 常見模式

### 短連線（單次更新後關閉）

```clojure
(defn click-handler [request]
  (hk/->sse-response request
    {hk/on-open
     (fn [sse]
       (d*/with-open-sse sse
         (d*/patch-elements! sse (render-result))))}))
```

### 長連線（Broadcast）

```clojure
(def !connections (atom #{}))

(defn subscribe [request]
  (hk/->sse-response request
    {hk/on-open  (fn [sse] (swap! !connections conj sse))
     hk/on-close (fn [sse _] (swap! !connections disj sse))}))

(defn broadcast! [html]
  (doseq [sse @!connections]
    (d*/lock-sse! sse
      (d*/patch-elements! sse html))))
```

### 與 Pathom3 整合

```clojure
(pco/defresolver live-data [{::keys [sse-connections]} _]
  {::pco/output [:app/live-subscribers]}
  {:app/live-subscribers (count @sse-connections)})
```

## 常見錯誤

| 錯誤 | 解法 |
|------|------|
| 元素沒更新 | 確保 HTML 根元素有 `id`，或傳 `d*/selector` |
| 傳 `{:selector ... :mode :inner}` 沒作用 | 1.0 options key 要用 `d*/selector` / `d*/patch-mode` / `d*/pm-inner` 這類 def |
| `patch-signals!` 丟 ClassCastException | 需傳 **JSON 字串**，不是 Clojure map；先 `json/write-value-as-string` |
| SSE 連線中斷 | 檢查 `on-close` 是否正確清理；短連線首選 `with-open-sse` |
| 信號值沒變 | 用 `$signal` 存取，不是 `signal` |
| 事件沒觸發 | `data-on:click` 不是 `data-onclick` |
| `data-scope` 找不到 | 1.0 已移除，改用 signal 前綴慣例（如 `$form.foo`） |
| `$el` / `$event` 未定義 | 1.0 已移除，改用 `el` / `evt` |
| 陣列 patch 後全變 | Merge Patch 不深合併陣列；用 `{id → item}` map 才吃得到 delta |
| `console-log!` 內嵌雙引號炸掉 | 實作是字串拼接，自己 `d*/execute-script!` 傳完整 JS |
| `data-attr:aria-pressed="$x"` 後 `[aria-pressed="true"]` 樣式不生效 | **expr 回傳 boolean 時，`true` 被設成空字串 `""`（不是 `"true"`）、`false` 直接 `removeAttribute`**。要讓 CSS match 特定值，expr 必須回傳**字串**：`"$x ? 'true' : 'false'"`。詳見 @references/attributes.md |

## Best Practices（The Tao of Datastar）

官方 `/guide/the_tao_of_datastar` 列出的原則，設計 handler / template 時照此思考：

### State
- **後端是 SoT**：前端只接收 patch，不要在前端自管「當前狀態」
- **Signals 越少越好**：只用在 UI 互動切換（`data-show`）、form 綁定（`data-bind`）、loading 指示（`data-indicator`）；不拿來當跨請求的資料快取
- **讀取時向後端拿最新的**，不要相信前端保留的舊資料
- **UI 狀態信號 → 隨請求帶給後端 → 後端決策**（見「Query param + signal 整合」）。Loading 也是相同原理：用 `data-indicator` 顯示，由後端送 patch 隱藏

### DOM 更新
- **Fat morph**：一次送大塊 HTML，讓 morph 做 diff；不要在後端自己管「哪些 id 要更新」。web-boilerplate 的 `split-bill-handler` 直接整個 `#split-bill-view` 重送就是這種用法
- 需要保留的內部狀態（video 播放位置、details open、input value）用 `data-ignore-morph` / `data-preserve-attr`
- 直接整塊 replace 且不想 morph 時才用 `d*/pm-replace`

### SSE pattern
- **短請求用短連線**（`with-open-sse` / on-open 結束即 `close-sse!`）
- **即時推播用長連線 + broadcast atom**（見上方常見模式）
- **CQRS**：一條長連線的讀（subscribe）+ 多條短連線的寫（mutation）；不要把 mutation 綁在同一條長連線上
- **開啟壓縮**（Brotli 對 SSE 約 200:1，http-kit adapter 已支援 `hk/gzip-profile` / `(brotli/->brotli-profile)`）

### Navigation & History
- 用標準 `<a>` 做頁面跳轉，別自造 history
- 後端要轉址時用 `d*/redirect!`
- 可搭配 `data-view-transition` / `d*/use-view-transition` 做平滑轉場（Pro 才有完整 `data-view-transition` 屬性）

### 反 pattern
- **別做 optimistic update**（前端假裝成功）：用 `data-indicator` 顯示 loading，由後端確認後送 patch
- 別用 signal 當資料層快取 → 後端查
- 別繞過 semantic HTML / a11y（Datastar 不強制 a11y，責任在開發者）

## 1.0 變更重點

**SDK 重命名（來自 2025-06 的 SDK ADR，已於 RC 階段落地）：**

| 舊 | 新 |
|---|---|
| `merge-fragment!` / `merge-fragments!` | `patch-elements!` / `patch-elements-seq!` |
| `remove-fragments!` | `remove-element!` |
| `merge-signals!` | `patch-signals!` |
| `remove-signals` | **已刪除**，改用 `patch-signals!` 傳 `nil` 刪除 |

**協定 / 前端：**

- SSE 協定只剩 `datastar-patch-elements` / `datastar-patch-signals` 兩個事件
- `execute-script!` / `redirect!` / `console-log!` / `console-error!` 改由 `patch-elements!` 注入 `<script>` 實作
- Options map 的 key 必須用 `d*/` 下的 def（namespaced keyword），不可用 `:selector` / `:mode`
- 新增 `pm-replace` patch mode（不 morph）
- 新屬性：`data-json-signals`、`data-on-signal-patch-filter`
- **修飾符語法（release vs RC 不一樣）**：1.0 release 用 `__` 分隔修飾符，`.` 分隔修飾符的內部值。例：`data-on:submit__prevent`、`data-on:input__debounce.300ms`、`data-on-interval__duration.5s`。1.0 RC 時期是 `.prevent` / `.debounce.300ms`，**寫舊語法不會報錯但整段被當成 event 名 → 修飾符不生效**（form 會做原生 submit）
- **已移除**：`data-scope`、魔術函式 `$el` / `$event` / `$refs` / `$watch` / `$$`（改用 `el` / `evt` / signal 前綴）
- HTTP action 新增 retry / cancellation / openWhenHidden / payload / selector 等選項
- Adapter package：`adapter-jetty` 已改名 `adapter-ring`（早於 1.0）
