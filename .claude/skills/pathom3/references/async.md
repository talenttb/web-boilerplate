# Async 處理詳細說明

Pathom 3 使用 Promesa 函式庫管理非同步處理。在 JavaScript 環境使用 native Promises，在 JVM 使用 CompletableFuture。

## 目錄

1. [基本 Async Resolver](#基本-async-resolver)
2. [使用 Async EQL 介面](#使用-async-eql-介面)
3. [Core.async 整合](#coreasync-整合)
4. [Batch 在 Parallel Processor](#batch-在-parallel-processor)
5. [常見錯誤](#常見錯誤)

## 基本 Async Resolver

Async resolver 與普通 resolver 結構相同，差別在於可以返回 future。

```clojure
(ns my-app
  (:require [com.wsscode.pathom3.connect.indexes :as pci]
            [com.wsscode.pathom3.connect.operation :as pco]
            [promesa.core :as p]))

(defn json-get [url]
  (p/let [resp (js/fetch url)
          json (.json resp)]
    (js->clj json :keywordize-keys true)))

(pco/defresolver age-from-name [{::keys [first-name]}]
  {::pco/output [::age]}
  (p/let [{:keys [age]} (json-get (str "https://api.agify.io/?name=" first-name))]
    {::age age}))
```

**重要**：Future 必須包裝整個返回 map，不能只包裝單個值。

```clojure
; ✅ 正確
(pco/defresolver age-from-name [{::keys [first-name]}]
  (p/let [{:keys [age]} (json-get url)]
    {::age age}))  ; future 包裝整個 map

; ❌ 錯誤
(pco/defresolver age-from-name [{::keys [first-name]}]
  {::age  ; map 中的值是 future，不會正常運作
   (p/let [{:keys [age]} (json-get url)]
     age)})
```

## 使用 Async EQL 介面

Async 只有 EQL 介面，沒有 Smart Maps 版本。

```clojure
(ns my-app
  (:require [com.wsscode.pathom3.interface.async.eql :as p.a.eql]))

(def env (pci/register [age-from-name]))

; 返回 Promise/CompletableFuture
(p.a.eql/process env {::first-name "Ada"} [::age])
```

## Core.async 整合

使用 `promesa-bridges` 函式庫擴展 Promesa 支援 core.async channels。

```clojure
(ns my-app
  (:require [clojure.core.async :as async :refer [go <!]]
            [com.wsscode.promesa.bridges.core-async]  ; 載入擴展
            [com.wsscode.pathom3.connect.indexes :as pci]
            [com.wsscode.pathom3.connect.operation :as pco]
            [com.wsscode.pathom3.interface.async.eql :as p.a.eql]
            [promesa.core :as p]))

(pco/defresolver slow-resolver []
  {::pco/output [::slow-response]}
  ; 返回 channel
  (go
    (<! (async/timeout 400))
    {::slow-response "done"}))

(def env (pci/register slow-resolver))

(comment
  (p/let [res (p.a.eql/process env [::slow-response])]
    (prn res)))
```

## Batch 在 Parallel Processor

在平行處理器中，批次處理的策略基於兩個配置：

| 配置 | 說明 | 預設值 |
|------|------|--------|
| `::pcrp/batch-hold-delay-ms` | 累積器等待新項目的毫秒數 | 10ms |
| `::pcrp/batch-hold-flush-threshold` | 達到此數量立即觸發批次 | nil |

```clojure
(def env
  (-> (pci/register batch-resolver)
      (assoc ::pcrp/batch-hold-delay-ms 20)
      (assoc ::pcrp/batch-hold-flush-threshold 100)))
```

## 常見錯誤

### 1. Future 沒有包裝整個返回值

```clojure
; ❌ 錯誤：值是 promise
{:result (p/let [x (fetch)] x)}

; ✅ 正確：整個 map 是 promise
(p/let [x (fetch)]
  {:result x})
```

### 2. 在同步介面使用 async resolver

Async resolver 只能搭配 `p.a.eql/process` 使用，不能用 `p.eql/process`。

### 3. Smart Maps 不支援 Async

如需 async 功能，請使用 `p.a.eql/process` EQL 介面。
