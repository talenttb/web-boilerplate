# Datastar Clojure SDK 參考（v1.0）

> 對齊 Datastar 1.0（SDK coord `dev.data-star.clojure/*` 1.0.0）。CDN 對應 `@v1.0.0` 或具體 tag（如 `@1.0.0-RC.7`）。

## 依賴

```clojure
;; deps.edn
{:deps {dev.data-star.clojure/sdk      {:mvn/version "1.0.0"}
        dev.data-star.clojure/http-kit {:mvn/version "1.0.0"}}}
```

可用套件：

| Coord | 用途 |
|-------|------|
| `dev.data-star.clojure/sdk` | 核心 API（必備） |
| `dev.data-star.clojure/http-kit` | http-kit adapter |
| `dev.data-star.clojure/ring` | Ring adapter |
| `dev.data-star.clojure/brotli` | Brotli 壓縮 profile |
| `dev.data-star.clojure/malli-schemas` | core malli schemas |
| `dev.data-star.clojure/http-kit-malli-schemas` | http-kit schemas |
| `dev.data-star.clojure/ring-malli-schemas` | Ring schemas |

## 命名空間

```clojure
(require '[starfederation.datastar.clojure.api :as d*]
         '[starfederation.datastar.clojure.adapter.http-kit :as hk])
;; Ring adapter：
;; (require '[starfederation.datastar.clojure.adapter.ring :as ring-gen])
```

## ->sse-response

```clojure
(hk/->sse-response request callbacks-and-options)
```

### Callbacks

| Key | 簽名 | 說明 |
|-----|------|------|
| `hk/on-open` | `(fn [sse-gen])` | SSE 連線建立時呼叫 |
| `hk/on-close` | `(fn [sse-gen status])` | 連線關閉時呼叫 |
| `hk/on-exception` | `(fn [sse-gen exception])` | 發生例外時呼叫 |

### Options

| Key | 說明 |
|-----|------|
| `hk/write-profile` | 寫入設定（如 `hk/gzip-profile`、`(brotli/->brotli-profile)`） |

### 完整範例

```clojure
(defn sse-handler [request]
  (hk/->sse-response request
    {hk/on-open
     (fn [sse]
       (d*/with-open-sse sse
         (d*/patch-elements! sse "<div id=\"result\">Done</div>")))

     hk/on-close
     (fn [sse status]
       (println "Connection closed:" status))

     hk/on-exception
     (fn [sse ex]
       (println "Error:" (.getMessage ex))
       (d*/close-sse! sse))

     hk/write-profile hk/gzip-profile}))
```

## Event 選項

**重要**：options map 的 key 是 `d*/` 命名空間下的 `def`（實為 namespaced keyword），**不是** `:selector` / `:mode`。

### 通用 SSE 選項（所有 event 函數皆可用）

| Key | 型別 | 用途 |
|-----|------|------|
| `d*/id` | string | SSE event id（讓瀏覽器能 replay） |
| `d*/retry-duration` | number(ms) | 連線斷開後重試間隔（預設 1000） |

### patch-elements! / patch-elements-seq! 選項

| Key | 型別 / 值 | 說明 |
|-----|----------|------|
| `d*/selector` | string | 目標 CSS selector（沒填則用元素的 id） |
| `d*/patch-mode` | 見下方 pm-* | 合併模式（預設 morph，即 `pm-outer`） |
| `d*/use-view-transition` | boolean | 啟用 View Transition API（預設 false） |

### Patch modes

| Const | 行為 |
|-------|------|
| `d*/pm-outer` | 替換外層 HTML（預設，會 morph） |
| `d*/pm-inner` | 替換內層 HTML |
| `d*/pm-replace` | 直接整個替換，不 morph，重置相關狀態 |
| `d*/pm-prepend` | 前置到子項 |
| `d*/pm-append` | 附加到子項 |
| `d*/pm-before` | 插入為前兄弟 |
| `d*/pm-after` | 插入為後兄弟 |
| `d*/pm-remove` | 移除元素 |

### patch-signals! 選項

| Key | 型別 | 用途 |
|-----|------|------|
| `d*/only-if-missing` | boolean | 只在 signal 不存在時才寫入（預設 false） |

### execute-script! 選項

| Key | 型別 | 用途 |
|-----|------|------|
| `d*/auto-remove` | boolean | 執行後移除 `<script>`（預設 true） |
| `d*/attributes` | map | 要加到 `<script>` 的屬性 |

## API 函數

### patch-elements!

修補 DOM 元素（根元素需有 id，或用 `d*/selector` 指定）。

```clojure
(d*/patch-elements! sse elements-html)
(d*/patch-elements! sse elements-html opts)
```

```clojure
(d*/patch-elements! sse "<div id=\"msg\">Hello</div>")

(d*/patch-elements! sse "<li>New item</li>"
  {d*/selector   "#list"
   d*/patch-mode d*/pm-append})

(d*/patch-elements! sse "<div>...</div>"
  {d*/use-view-transition true})
```

### patch-elements-seq!

批次修補多個元素字串。

```clojure
(d*/patch-elements-seq! sse elements-coll)
(d*/patch-elements-seq! sse elements-coll opts)
```

### remove-element!

`patch-elements!` + `pm-remove` 的 sugar。

```clojure
(d*/remove-element! sse "#notification")
(d*/remove-element! sse "#old" {d*/use-view-transition true})
```

### patch-signals!

依 [RFC 7386 JSON Merge Patch](https://datatracker.ietf.org/doc/html/rfc7386) 語意修補 signals。

```clojure
(d*/patch-signals! sse signals-content)
(d*/patch-signals! sse signals-content opts)
```

- `signals-content` 可為 **JSON 字串** 或能被序列化成 JSON 物件的資料（SDK 不內建 JSON 序列化，請自備 jsonista/cheshire 轉字串）
- `nil` 值代表刪除該 signal

```clojure
(d*/patch-signals! sse
  (json/write-value-as-string {:count 42 :name "Alice"}))

(d*/patch-signals! sse
  (json/write-value-as-string {:foo nil}))           ;; 刪除 :foo

(d*/patch-signals! sse
  (json/write-value-as-string {:default-val 0})
  {d*/only-if-missing true})
```

### execute-script!

在 browser 執行 JavaScript。實作上是用 `patch-elements!` + `pm-append` + `selector "body"` 插入 `<script>`，預設執行完自動移除。

```clojure
(d*/execute-script! sse "alert('Hello!')")
(d*/execute-script! sse "window.scrollTo(0, 0)"
  {d*/auto-remove false
   d*/attributes  {"type" "module"}})
```

### Script sugar

都是 `execute-script!` 的 wrapper。

```clojure
(d*/console-log!   sse "Debug info")
(d*/console-error! sse "Something went wrong")
(d*/redirect!      sse "/new-page")   ;; 內部用 setTimeout(() => window.location.href = ...)
```

### get-signals

從 ring request 取出 signals 原始 JSON 字串（**需自行 parse**）。

- GET：從 `:query-params` 取
- 其他方法：從 body 取

```clojure
(def signals-str (d*/get-signals request))
;; => "{\"count\":5,\"name\":\"Bob\"}"
(json/read-value signals-str json/keyword-keys-object-mapper)
;; => {:count 5 :name "Bob"}
```

### datastar-request?

檢查是否為 Datastar 觸發的請求。

```clojure
(when (d*/datastar-request? request)
  ...)
```

### close-sse!

關閉 SSE 連線。回傳 `true`（剛關閉）/ `false`（已關閉）。

### with-open-sse（macro）

類似 `clojure.core/with-open`，finally 會呼叫 `close-sse!`。短連線首選。

```clojure
(hk/on-open
  (fn [sse]
    (d*/with-open-sse sse
      (d*/patch-elements! sse frag1)
      (d*/patch-signals!  sse signals))))
```

### lock-sse!（macro）

對 SSE generator 內部的 `ReentrantLock` 加鎖，避免多 thread 同時寫入同一條連線時事件交錯。

```clojure
(d*/lock-sse! sse
  (d*/patch-elements! sse frags)
  (d*/patch-signals!  sse signals))
```

### Action string helpers

在 server 端產出 `data-on:click="@get('/api/foo')"` 這類表達式字串用。

```clojure
(d*/sse-get    "/a/b")                        ;; => "@get('/a/b')"
(d*/sse-post   "/api")                        ;; => "@post('/api')"
(d*/sse-put    "/a/b" "{includeLocal: true}") ;; => "@put('/a/b', {includeLocal: true})"
(d*/sse-patch  "/a")
(d*/sse-delete "/a")
```

## SSE 生命週期

```
Client connects
       │
       ▼
  on-open called
       │
       ├── patch-elements! ──┐
       ├── patch-signals!  ──┼── 可多次呼叫
       ├── execute-script! ──┘
       │
       ▼
  close-sse! or with-open-sse / client disconnects
       │
       ▼
  on-close called
```

## 壓縮

### gzip（http-kit adapter 內建）

```clojure
(hk/->sse-response request
  {hk/on-open (fn [sse] ...)
   hk/write-profile hk/gzip-profile})
```

### Brotli（需額外依賴 `dev.data-star.clojure/brotli` + `brotli4j`）

```clojure
(require '[starfederation.datastar.clojure.brotli :as brotli])

{hk/write-profile (brotli/->brotli-profile)}
```

## 與 Ring 整合

```clojure
(require '[starfederation.datastar.clojure.adapter.ring :as ring-gen])

(defn handler [request]
  (ring-gen/->sse-response request
    {ring-gen/on-open  (fn [sse] ...)
     ring-gen/on-close (fn [sse status] ...)}))
```

Ring adapter 的 key 命名鏡射 http-kit adapter，只是命名空間不同。

## 1.0 變更重點（相對於 0.21 / 早期 RC）

- **事件名定版**：SSE 協定只剩兩個事件 `datastar-patch-elements`、`datastar-patch-signals`（取代舊的 `merge-fragments` / `merge-signals`）
- **Options map 用 namespaced def**：必須寫 `{d*/selector "..." d*/patch-mode d*/pm-inner}`，不能用 `{:selector ... :mode :inner}`
- **新增 `pm-replace`**：不 morph、直接換掉整個元素的模式
- **`execute-script!` / `redirect!` / `console-log!` / `console-error!`** 全部改以 `patch-elements!` 注入 `<script>` 實作（1.0 砍掉了獨立的 execute-script / redirect SSE event）
- **`data-scope` 已移除**
