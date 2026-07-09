# web-boilerplate

Clojure server-driven web 樣板：Pathom3（資料查詢） + Datastar（前端互動） + append-only DB（`commits`/`refs`）這套組合，clone 改名即可開新專案。

## Quickstart

```bash
# 1. Clone 專案
git clone <repository-url> my-app
cd my-app

# 2. 改名：全域替換兩個 pattern
#    web-boilerplate → 新專案名稱（deps.edn 的 lib 名、build.clj、ns 字串等）
#    web_boilerplate  → 新目錄／namespace 路徑（底線版，對應到目錄與檔名）
#    也可以直接請 AI agent 代勞（叫它「把這個專案改名成 my-app」即可）
grep -rl 'web-boilerplate' . --exclude-dir={.git,target,.cpcache} | xargs sed -i '' 's/web-boilerplate/my-app/g'
grep -rl 'web_boilerplate' . --exclude-dir={.git,target,.cpcache} | xargs sed -i '' 's/web_boilerplate/my_app/g'
mv src/web_boilerplate src/my_app
mv test/web_boilerplate test/my_app

# 3. 啟動
clj -M:run
```

應用程式預設會在 `http://localhost:8080` 啟動（見 `resources/config.edn`），開 `http://localhost:8080/demo` 看旅費分帳範例。

> macOS 的 `sed -i ''` 與 GNU sed 的 `sed -i` 語法不同，Linux 上請拿掉 `''`。

## 環境需求

- **Java 21+**（Clojure 執行環境；`.tool-versions` 已釘 Temurin 25，用 asdf 可自動安裝對應版本）
- **Clojure CLI**
- **babashka（bb）**（跑 `bb.edn` 裡的開發 task，如 `worktree-up`）
- **PostgreSQL**（僅在要用 append-only DB 層時才需要——樣板開箱啟動不碰 DB，見下方「Append-only DB」段）

## 專案結構

```
web-boilerplate/
├── deps.edn                  # 依賴管理檔案
├── build.clj                 # 建構腳本（uberjar）
├── bb.edn                    # babashka task（clj-repl／worktree-up／wait-nrepl／trim-log）
├── resources/
│   ├── config.edn            # 配置檔案（cprop 底層）
│   └── public/css/
│       ├── app.css           # 全站 token（light/dark）+ reset + 基礎排版
│       └── demo.css          # demo 專用樣式
├── dev/
│   └── user.clj              # 開發 REPL：start/stop/reset/restart/portal
├── src/web_boilerplate/
│   ├── core.clj              # -main（composition root：注入 pathom 資源＋啟動 server）
│   ├── config.clj            # 配置管理（cprop + malli 驗證）
│   ├── logging.clj           # mulog 設定
│   ├── server.clj            # http-kit server 生命週期
│   ├── nrepl.clj             # nREPL server
│   ├── portal.clj            # Portal 啟動
│   ├── util.clj              # gen-nanoid／uuid7／eid
│   ├── db.clj                # append-only DB core API（樣板未接，備而不用）
│   ├── pathom.clj            # Pathom3 registry + process-eql（唯一查詢入口）
│   ├── pathom/plugins.clj    # Pathom3 plugins
│   ├── handler.clj           # reitit routes + middleware + demo handler
│   ├── demo.clj              # demo：旅費分帳 domain + view
│   └── resolvers/
│       └── demo.clj          # demo 的 Pathom resolvers/mutations
├── test/web_boilerplate/
│   └── demo_test.clj         # demo 純函數測試
├── scripts/
│   ├── install-git-hooks.sh  # 安裝 git hooks
│   └── git-hooks/pre-commit  # 擋 #p debug 巨集殘留
├── workspace/                # 本地檔案（log／secret.edn），已 .gitignore
└── .claude/                  # skills/agents/commands（brepl、pathom3、db、malli、css、
                               # datastar 六個 skill；clj-convention-reviewer 等三個 agent；
                               # isolated-verify command/workflow）
```

## 技術架構

### Pathom3 Hexagonal 架構

各種入口都收斂到 `process-eql` 這一個呼叫點，資源則由入口注入 `pathom.clj` 的 env：

```
┌────────────────────┐  ┌──────┐  ┌─────────────┐
│ web（handler.clj） │  │ REPL │  │ CLI（未來） │
└──────────┬─────────┘  └───┬──┘  └──────┬──────┘
           │                │            │
           └────────────────┼────────────┘
                             │ process-eql
                             ▼
┌──────────────────────────────────────────────────────┐
│ pathom.clj                                           │
│ process-eql＝唯一呼叫點；env＝資源容器（由入口注入） │
└──────────────────────────┬───────────────────────────┘
                            │ require
                            ▼
┌──────────────────────────────────────┐
│ resolvers/<domain>.clj（薄 adapter） │
│ 從 env 解構資源、以參數呼叫 biz      │
└──────────────────┬───────────────────┘
                    │ require
                    ▼
┌───────────────────────────────────────────┐
│ demo.clj（biz logic）                     │
│ 資源必填首參，不認識外圍（Pathom／HTTP…） │
└──────────────────────┬────────────────────┘
                        │ 用傳入的資源呼叫
                        ▼
┌─────────────────────────────────────────────────┐
│ db.clj（DB 與外部服務層；PostgreSQL，樣板未接） │
└─────────────────────────────────────────────────┘
```

- **入口在哪？** web／REPL／CLI 全部呼叫 `process-eql`；加入口＝加一條線，其餘層零改動。
- **誰注入資源？** 入口：`core/-main` 與 `user/start` 呼叫 `(pathom/start-pathom! core/pathom-resources)`；`pathom.clj` 只是收 map 的 port，不自取資源。
- **誰是 biz logic？** `demo.clj`。
- **誰處理 DB 與外部服務？** `db.clj`，經 resources map → env → 參數 一路傳遞抵達。

## Append-only DB（commits + refs）

`web-boilerplate.db` 保留 stock-dash 同款 append-only 讀寫核心，但**樣板預設不接 DB**：`core/-main` 建構的 `pathom-resources` 不碰 `db/get-datasource`，開箱即用不需要 PostgreSQL。

### Core API（`web-boilerplate.db`）

| 函數 | 用途 |
|---|---|
| `(write! ds eid f)` | 讀當前 data → `f` → 寫新 commit + 更新 ref |
| `(merge! ds eid patch)` | `write!` + `merge` 的 shortcut |
| `(delete! ds eid)` | Soft delete，assoc `:deleted_at` |
| `(get-ref ds eid)` | 取最新 snapshot |
| `(get-ref-by-kind ds kind)` | 取某 kind 所有 active snapshot |
| `(get-commits ds eid)` | 取某 eid 全部版本 |
| `(get-commit-at ds eid t)` | 時間旅行 |
| `(ensure-index! ds spec)` | idempotent 建 index，支援 map / raw SQL |
| `(exec-ddl! ds sql)` | 執行任意 DDL |

### 接上第一個 DB domain

1. `resources/config.edn` 的 `:database` 填實際連線資訊（host/port/name/user/password）
2. `src/web_boilerplate/core.clj` 的 `pathom-resources` 比照 `:demo/state demo/state` 的模式加一行 `:db/ds (db/get-datasource)`，並在 `core.clj` 的 ns require 加 `[web-boilerplate.db :as db]`
3. 新 domain 照 `demo.clj` 的模式建立 domain ns 與 `resolvers/<domain>.clj`，`pathom.clj` 補上該 domain 的 require 與 `pci/register` 一行；新 domain 的 resolver 從 env 的 `:db/ds` 取 datasource，呼叫上表的 core API

現成範例在 `resolvers/demo.clj` 底部的 `db-example-resolvers`（`trip-archive-list`／`trip-snapshot`／`trip-commit-history` 三顆，分別示範 `get-ref-by-kind`、`get-ref` 搭 `:select` 投影、`get-commits` 取 audit log，預設未註冊）：接上 `:db/ds` 後把 `(pci/register demo-resolvers/all-resolvers)` 改成 `(pci/register (into demo-resolvers/all-resolvers demo-resolvers/db-example-resolvers))` 即可查。

## Demo：旅費分帳

`/demo` 是一個完整的 Datastar + Pathom3 範例（表單粗略，重點示範資料流不是 UI 完成度）：

- **卡 1 成員**：chips 顯示現有成員 + 輸入姓名加入
- **卡 2 支出**：表格列出每筆支出（誰付／項目／金額／刪除），表尾 inline form 新增
- **卡 3 結算**：總支出、每人均攤、每人淨額（`data-tone` 正綠負紅）、`settle-transfers`（greedy 演算法：最大債權配最大債務）算出的轉帳建議清單

資料流：`demo.clj` 的 `defonce state` atom 存 members/expenses → `resolvers/demo.clj` 的 resolver 鏈（`:demo.split/members`→`:demo.split/expenses`→`:demo.split/total`→`:demo.split/balances`→`:demo.split/transfers`）逐層推導 → view resolver `:demo/<split-bill-view>` 組成整頁 → `split-bill-handler`：GET 走 `process-eql` render 整頁，POST 讀 Datastar signals、依 `$action` 呼叫對應 mutation（`demo.expense/add!`／`demo.expense/remove!`／`demo.member/add!`），再用 `patch-elements!`／`patch-signals!` 回推更新。

### 整包刪除 demo（clone 後不需要 demo 範例時）

**刪除四個檔案：**

- `src/web_boilerplate/demo.clj`
- `src/web_boilerplate/resolvers/demo.clj`
- `resources/public/css/demo.css`
- `test/web_boilerplate/demo_test.clj`

**`src/web_boilerplate/handler.clj` 要刪四處：**

1. `ns` 的 require 三行：`[web-boilerplate.demo :as demo]`、`[starfederation.datastar.clojure.adapter.http-kit :as d*-hk]`、`[starfederation.datastar.clojure.api :as d*]`（後兩個 datastar require 只有 demo 在用，一併刪）
2. `home-handler` 首頁連結那一行：`[:li [:a {:href "/demo"} "旅費分帳 demo"]]`
3. `routes` 裡的路由：`["/demo" {:handler #'split-bill-handler}]`
4. `render-split-bill-page` 與 `split-bill-handler` 兩個 fn 的連續區塊（緊接在 `wrap-request-logging` 之後、`routes` 之前）

**`src/web_boilerplate/pathom.clj` 要刪兩處：**

1. `ns` 的 require：`[web-boilerplate.resolvers.demo :as demo-resolvers]`
2. `start-pathom!` 裡 threading 巨集的一段：`(pci/register demo-resolvers/all-resolvers)`

**`src/web_boilerplate/core.clj` 要刪兩處：**

1. `ns` 的 require：`[web-boilerplate.demo :as demo]`
2. `pathom-resources` map 裡的一個 entry：`:demo/state demo/state`

刪完後 `pathom.clj` 的 `start-pathom!` 只剩 `pcp/with-plan-cache`／`p.plugin/register` 兩段 threading，`core.clj` 的 `pathom-resources` 只剩 `:config/get-config config/get-config`，仍是自洽可編譯的狀態（沒有其他 resolver 註冊也不會壞——之後接上第一個真正的 domain 時再 `pci/register` 進去）。

## 開發

### REPL

```bash
bb clj-repl
```

啟動帶 cider middleware 的 nREPL server（`bb.edn` 的 `clj-repl` task，等同 `clj -M:dev:nrepl`），從編輯器（Calva／CIDER）連上後載入 `dev/user.clj`（`user` namespace）：

```clojure
user=> (start)   ; 啟動 server
user=> (stop)    ; 停止 server
user=> (reset)   ; clj-reload 依賴排序 unload → reload（唯一合法 reload 流程，修改 src/ 後一定要跑這個）
user=> (restart) ; reset + 重啟 server
user=> (portal)  ; 開 Portal 檢視器
```

改 `src/` 之後不是只改檔就結束，必須跑 `(reset)` 確認 reload 成功（有狀態的組件如 `db.clj`／`logging.clj` 靠 `before-ns-unload`／`after-ns-reload` hooks 停/啟）。禁止 `:reload`／`:reload-all`／在 REPL 裡直接 `(ns ...)`，都會破壞 JVM 運行時狀態，只能重啟 JVM 修復。

執行任何 Clojure 程式碼前先載入 SKILL `/brepl`（`.claude/skills/brepl/`，heredoc 模式跑 REPL），不要直接呼叫 `brepl` 指令。

### 隔離驗證

`/isolated-verify` command（`.claude/commands/isolated-verify.md` + `.claude/workflows/isolated-verify.js`）提供在獨立 git worktree + 持久 JVM 上實作並驗證功能的流程（`bb worktree-up` 起 JVM + workflow 跑 implement→reset→證據→review→commit）。用 `/isolated-verify` 觸發，不要自行進 worktree。

### Git hooks

```bash
./scripts/install-git-hooks.sh
```

安裝 pre-commit hook，擋掉殘留在 `src/` 的 `#p` debug 巨集（Portal tap）再讓你 commit。

### 執行測試

```bash
clj -M:test
```

### 檢查過期的依賴

```bash
clj -M:outdated
```

## 建構

### 建立 Uberjar

```bash
clj -T:build uber
```

建構完成後會產生 `target/web-boilerplate-0.1.0-standalone.jar`。

### 執行 Uberjar

```bash
java -jar target/web-boilerplate-0.1.0-standalone.jar
```

### 清理建構產物

```bash
clj -T:build clean
```

## License

見 `LICENSE`。
