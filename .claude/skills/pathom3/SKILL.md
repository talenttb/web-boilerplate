---
name: pathom3
description: Pathom 3 是一個 Clojure/ClojureScript 函式庫，用於建立屬性關係模型。當使用者需要撰寫 Pathom 3 程式碼時使用此 skill，包括：(1) 建立 resolvers 來定義屬性之間的關係, (2) 使用 EQL 介面查詢資料, (3) 使用 Smart Maps 存取資料, (4) 建立 mutations 處理副作用, (5) 使用 built-in resolvers, (6) 處理 async/batch 操作, (7) 使用 plugins 擴展功能。觸發詞：pathom, resolver, defresolver, EQL, smart-map, pco, pci, pbir。末段含 web-boilerplate 專案慣例（hexagonal、attribute prefix、var 命名、新增 resolver 步驟）。
---

# Pathom 3 Skill

Pathom 是一個 Clojure/ClojureScript 函式庫，用於建模屬性之間的關係。它能夠抽象化函數呼叫鏈，讓你專注於資料本身而非如何獲取資料。

## 核心命名空間別名

```clojure
; 主要命名空間
[com.wsscode.pathom3.connect.indexes :as pci]
[com.wsscode.pathom3.connect.operation :as pco]
[com.wsscode.pathom3.interface.eql :as p.eql]

; 常用命名空間
[com.wsscode.pathom3.connect.built-in.resolvers :as pbir]
[com.wsscode.pathom3.connect.built-in.plugins :as pbip]
[com.wsscode.pathom3.interface.async.eql :as p.a.eql]
[com.wsscode.pathom3.interface.smart-map :as psm]
[com.wsscode.pathom3.plugin :as p.plugin]

; 較少使用
[com.wsscode.pathom3.cache :as p.cache]
[com.wsscode.pathom3.connect.foreign :as pcf]
[com.wsscode.pathom3.connect.planner :as pcp]
[com.wsscode.pathom3.connect.runner :as pcr]
[com.wsscode.pathom3.error :as p.error]
```

## Resolvers

Resolver 是 Pathom 的基本構建塊，用於表達屬性之間的關係。

### defresolver 語法

```clojure
; 完整形式
(pco/defresolver resolver-name [env {:keys [input-attr]}]
  {::pco/input  [:input-attr]
   ::pco/output [:output-attr]}
  {:output-attr (compute-value input-attr)})

; 簡化形式 - 自動推斷 input/output
(pco/defresolver resolver-name [{:keys [input-attr]}]
  {:output-attr (compute-value input-attr)})

; 無輸入的 resolver
(pco/defresolver constant-resolver []
  {:my-constant 42})
```

### Resolver 配置選項

| 選項 | 說明 |
|------|------|
| `::pco/input` | EQL 表達式，指定所需的輸入屬性 |
| `::pco/output` | EQL 表達式，指定提供的輸出屬性 |
| `::pco/batch?` | 設為 true 啟用批次處理 |
| `::pco/batch-chunk-size` | 批次處理的分塊大小 |
| `::pco/priority` | 優先級（整數，預設為 0） |
| `::pco/transform` | 轉換函數 |

### Optional Inputs

```clojure
(pco/defresolver user-display-name
  [{:user/keys [email name]}]
  {::pco/input [:user/email (pco/? :user/name)]}
  {:user/display-name (or name email)})

; 或使用 :or 語法
(pco/defresolver user-display-name
  [{:user/keys [email name] :or {name nil}}]
  {:user/display-name (or name email)})
```

### Nested Inputs

```clojure
(pco/defresolver top-players-avg
  [{:keys [game/top-players]}]
  {::pco/input [{:game/top-players [:player/score]}]}
  {:game/top-players-avg-score
   (let [sum (transduce (map :player/score) + 0 top-players)]
     (double (/ sum (count top-players))))})
```

### Batch Resolvers

```clojure
(pco/defresolver batch-fetch [items]
  {::pco/input  [:id]
   ::pco/output [:data]
   ::pco/batch? true
   ::pco/batch-chunk-size 10}  ; 可選
  (mapv #(hash-map :data (fetch (:id %))) items))
```

## Built-in Resolvers

詳細資訊請參閱 [references/built-in-resolvers.md](references/built-in-resolvers.md)

```clojure
; 常數
(pbir/constantly-resolver :math/PI 3.1415)

; 別名（單向）
(pbir/alias-resolver :source/id :target/id)

; 等價（雙向）
(pbir/equivalence-resolver :attr-a :attr-b)

; 單屬性轉換
(pbir/single-attr-resolver :ms :seconds #(/ % 1000))

; 靜態表
(pbir/static-table-resolver :user/id
  {1 {:user/name "Alice"}
   2 {:user/name "Bob"}})
```

## 索引與環境

```clojure
; 建立索引
(def indexes
  (pci/register [resolver-a resolver-b]))

; 合併多個來源
(def env
  (pci/register [resolver-a
                 [nested-resolvers]
                 other-indexes]))
```

## EQL 介面

```clojure
; 基本查詢
(p.eql/process indexes [::attr-a ::attr-b])

; 帶初始資料
(p.eql/process indexes
  {:user/id 1}
  [:user/name :user/email])

; 嵌套查詢
(p.eql/process indexes
  [{:users [:user/name :user/email]}])

; 帶參數
(p.eql/process indexes
  '[(::todos {::done? false})])
```

## Smart Maps

```clojure
(def sm (psm/smart-map indexes {:user/id 1}))

; 像普通 map 一樣存取
(:user/id sm)      ; => 1
(:user/name sm)    ; => 自動解析

; 預載入（帶參數）
(psm/sm-preload! sm '[(::todos {::done? false})])
```

## Mutations

```clojure
(pco/defmutation save-file [{::keys [path content]}]
  (spit path content)
  {::file-saved true})

; 執行 mutation
(p.eql/process env
  [`(save-file {::path "file.txt" ::content "hello"})])
```

## Async 處理

詳細資訊請參閱 [references/async.md](references/async.md)

```clojure
(pco/defresolver async-resolver [{:keys [id]}]
  (p/let [data (fetch-async id)]
    {:result data}))

; 使用 async EQL 介面
(p.a.eql/process env [:result])
```

## 參數處理

```clojure
(pco/defresolver todos-resolver [env _]
  {::pco/output [{::todos [::todo-message ::todo-done?]}]}
  {::todos
   (let [params (pco/params env)]
     (filter-by-params params mock-todos))})
```

## 常見錯誤

1. **返回值必須是 map** - Resolver 必須返回 map，不能只返回值
2. **Batch resolver 順序** - 批次結果必須與輸入順序一致，使用 `coll/restore-order` 修復
3. **Async 返回值** - Promise 必須包裝整個返回 map，不能只包裝單個值

## 相關資源

- [Built-in Resolvers 完整參考](references/built-in-resolvers.md)
- [Async 處理詳細說明](references/async.md)
- [官方文件](https://pathom3.wsscode.com/docs/)

## 本專案 Pathom 慣例（web-boilerplate）

專案採用 Pathom3 作為查詢與資料組合層，架構對齊 hexagonal（ports and adapters）：

```
EQL query (inbound port)
  → Pathom resolvers (adapter)
    → domain namespace (core logic)
      → 外部資源 via env (outbound adapter)
```

### 設計原則
- **Business logic 留在獨立的 domain namespace**（如 `demo.clj`），resolver 只做薄 adapter
- **Resolver 透過 env 取得依賴**（如 datasource），不直接 require 外部資源
- **Attribute 使用 domain prefix** 避免跨 domain 衝突

### 分層與 require 方向（requiring-resolve 教訓）

`pathom.clj` 是組裝點：它 require 所有 `resolvers/<domain>.clj`，resolvers 再 require domain ns。依賴箭頭只准往下：

```
inbound adapters（web handler／CLI／REPL helper）─ 靜態 require pathom，呼叫 process-eql
  ↓
pathom.clj（registry + env + process-eql ＝ inbound port，也是 EQL 路徑的 composition root：
            在這裡建構資源並放進 env，如 :db/ds (db/get-datasource)、:runner/req!）
  ↓ require
resolvers/<domain>.clj（薄 adapter：從 env 解構資源，以「參數」傳入 domain fn）
  ↓ require
<domain>.clj（core 純函數：資源必填首參（ds…），絕不自己抓資源）
  ↓ 用傳入的資源呼叫
outbound adapters（db.clj／外部服務 client：提供函式 API 與資源生命週期）
```

資源注入點＝`pathom.clj` 的 env（EQL 路徑的 composition root）。domain ns 對 `db.clj` 的靜態 require 只是使用它的函式 API（`write!`／`get-ref`…），**資源（datasource 等）永遠從參數來**——這就是依賴反轉的所在，反轉的是資源，不是 ns require。EQL 路徑之外的入口（`-main`、長駐引擎的 `start!`）各自當自己的 composition root 取得資源，再以參數餵給 domain fn。

鐵律：**被 pathom.clj 傳遞性 require 的 ns（resolvers、domain）永遠不呼叫 `process-eql`**。domain ns 一 require pathom 就成環，Clojure 載入器直接以 `Cyclic load dependency` 拒載。用 `requiring-resolve` 雖可破環，但它是 optional dependency／載入順序問題的 escape hatch，不是一般呼叫的慣用法——若發現需要靠它來呼叫 `process-eql`，代表那段 code 其實是 inbound adapter、放錯層了，正解是把它搬到 pathom 之上（如 handler.clj），不是留在原地破環。新增入口（CLI／排程／新頁面）＝在最上層加一個薄 adapter，domain 與 resolvers 零改動。

本樣板 demo 的 handler 即因此住在 handler.clj，demo.clj 完全不碰 pathom。

### Attribute prefix

每個 domain 的 attribute 加上自己的前綴，新增 domain 時照此慣例：

| Domain | Prefix | 範例 |
|--------|--------|------|
| Demo（旅費分帳） | `demo.split.` | `:demo.split/members`, `:demo.split/balances` |

### Var 命名

**Var 名也必須嵌 domain noun**，不可用 generic 動詞/名詞（會撞 `clojure.core/*`、跨 domain 重名、stack trace 看不出歸屬）：

| Pattern | 範例 |
|---------|------|
| Resolver: `<domain>-<descriptor>` 或 `<descriptor>-<domain>` | `split-bill-balances`, `split-bill-transfers`, `all-expenses`, `expense-by-id` |
| Mutation: `<verb>-<domain>` | `add-expense!`, `remove-expense!`, `add-member!` |

✗ `balances` / `total` / `transfers!` / `current`（generic，撞 core var 且跨 domain 衝突）
✓ `split-bill-balances` / `split-bill-total` / `add-expense!`

`::pco/op-name` 與 attribute 不受 var 名影響——var 是 Clojure internal，op-name / attribute 才是 EQL 對外 API。

### 新增 Resolver 的步驟
1. 在 domain namespace 實作 core logic（純函數，接收 `ds` 參數）
2. 建立 `resolvers/<domain>.clj`，用 `pco/defresolver` / `pco/defmutation` 包裝
3. Resolver 從 `env` 取得依賴（如 `{::keys [db-ds]}`）
4. 在 `pathom.clj` 的 `start-pathom!` 中注入依賴到 env，並將 resolvers 加入 registry
