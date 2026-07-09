# Claude 開發指南

此文件記錄專案的開發慣例和架構決策，供 AI agent 參考。

## 專案是什麼

web-boilerplate：Clojure server-driven web 樣板，示範 Pathom3 + Datastar + append-only DB 這套組合——clone 改名（`web-boilerplate`→專案名、`web_boilerplate`→目錄名）即可開新專案。

- **主軸是 SIMPLE**：用 Pathom3 讓「程式」自動規劃資料最佳路徑，而不是工程師手刻取數鏈——全專案設計都朝 simple 收斂。
- **AI-friendly**：開發可直接透過 REPL / Portal / log file 介入協助。
- **架構總覽 / 目錄地圖**：見 `README.md`（含 codebase map）。

## 開發原則
- 文件＆溝通需使用正體中文
- **所有設計必須 Backward Compatible（最高原則）**：任何變更都不能破壞既有呼叫者與既有資料。底下兩條是具體落實：
  - **禁止 In-place 改寫既有函數／resolver 行為**（程式碼層）：不要原地改寫既有 function / resolver 的 return shape、input 語意、data source、side effect。即使呼叫方式看起來沒變，只要下游可觀察到差異都算 breaking change。需要新行為 → **新增**一個命名清楚的函數／resolver，舊的保持運作
  - **禁止 Migration（DB / Application / 資料格式）**（資料層）：不做任何形式的 migration。新增欄位採 nullable 或預設值、改名用 alias、不破壞既有 code 與資料
  - escape hatch：確實需要 breaking change **必須先跟用戶討論確認**才能動手
  - **計畫授權字樣**：計畫檔內含「**使用者已授權本計畫點名的 breaking changes**」字樣時，計畫中**明確點名**要改的舊函數／resolver／行為，等於已經做過上一條的「跟用戶討論確認」，**不必再問一次**——以計畫為主照改，不得為了保舊碼扭曲計畫的實作。計畫**沒點名**的部分、**沒有此字樣**的計畫（含討論／迭代中的草稿），照舊受本原則約束。此字樣只授權「能改什麼」，**不是開工指令**——何時動手依使用者明確指示
- **不要過早優化、不要太快拆小 function**：撰寫功能函數時全部放在一個命名良好的函數裡，發現過長再詢問是否用組合方式撰寫；inline function 為主，不需要 helper/util/micro function（有共用需求先與使用者討論）
- **設計思考第一步從 Clojure/Lisp 的角度出發**：出設計前先盤點「這個問題能被哪些 core fns / 慣用 pattern 直接承載？」（data-first、group-by / merge-with / partition-by 這類 core fns、seq 抽象、destructuring、defmulti…），設計說明需點名用了哪些 Clojure 特性、為什麼；少數場合用命令式無妨
- **禁止添加註解**：靠 fn name 表達意圖——overview 時看 name 就知道在幹嘛，需要細項確認才看 docstring（不是 docstring 沒用，而是命名先承載意圖）。確認某段複雜度高需要註解，**必須先詢問用戶確認**才能添加。需要放範例時用 comment 在該 fn 後方加上（comment 更能體現 clojure 的互動性），不用註解做 demo
- **對齊底層 SDK 設計**：呼叫底層 SDK（第三方 vendor）避免不必要的抽象層，直接用 SDK 提供的資料結構；**避免字串拼接作為 key**，用結構化的 map / vector（不用 "member-id:expense-id" 這種複合字串 key）
- **req 這個參數貫穿相關的全部函數**，需要時直接原地組合，不要提早過濾需要的資料
- **不要根據 sub-tool 的 summary 自己腦補解釋**：WebFetch / Agent / Sub-LLM 的 summary 跟預期對不上時，用 `curl` / `Read` / `Grep` 拿 raw 驗證，不要自編「應該是 redirect 吧 / 內容不全吧」這類解釋。raw 拿不到請真實說明並請使用者協助

## 工作流程
- **修改 `src/` 後必須 Reset**：唯一的 reload 方式是 `(user/reset)`（clj-reload 自動依賴排序 unload → reload，並觸發各 namespace 的 reload hooks）。不要只改檔就結束，必須確認 reload 成功；之後直接用 curl / 瀏覽器確認結果
- **禁止 `:reload` / `:reload-all` / 在 brepl 裡跑 `(ns ...)`**：都會破壞 JVM 運行時狀態（protocol dispatch 斷裂 / eval context 跑掉），只能重啟 JVM 修復。唯一正確 reload 流程就是 `(user/reset)`，沒有例外
- **執行任何 Clojure 程式碼前必先載入 SKILL `/brepl`**，用 skill 的 heredoc 模式跑，不直接呼叫 `brepl` 指令
- **多步驟實作以 Subagent-Driven 為主**：每個 task 派一個 fresh subagent，完成後由主 session 審查再進下一個，保留 checkpoint 與 review 機會
- **討論完先 plan recap 再 stop and yield**：背景知識收集完，先給精簡 plan recap（剛理解到什麼、打算怎麼做），然後把回合交回使用者；**不得自行開 worktree 或開工實作**，等使用者明確下指令
- **不要自行進 worktree / 隔離驗證**：開 worktree（`EnterWorktree` / `git worktree add` / `bb worktree-up`）一律由使用者明確觸發（打 `/isolated-verify` 或字面要求）。討論、分析、選型、方向性同意（「可以」「沒問題」）都不是開工指令。隔離驗證機制見 `/isolated-verify` command

### Debug 規則
- 回答前先說：你現在掌握哪些事實？哪些是推測？
- 提出任何「為什麼壞掉」的假設前，先取得 raw evidence（印實際值 / 貼 log / 檢查實際 DOM/network/file）
- 沒有證據時明確說「我不知道，需要你提供 X」，不要列舉可能原因清單
- 一次只驗證一個假設，驗完再進下一個。禁止「可能是 A，也可能是 B，也可能是 C」，改成「我需要看到 X 才能判斷」

### 狀態管理與 Reload Hooks
有狀態的組件用 `defonce` + `atom`，並**必須**實作對應 reload hooks：

| 組件類型 | Reload 行為 | Hooks（範例） |
|---------|------------|--------------|
| 無狀態資料 | 重新載入 | `after-ns-reload` 重新載入（`config.clj`） |
| 短暫狀態 | 停止並重啟 | `before-ns-unload` 停 + `after-ns-reload` 啟（`logging.clj`, `pathom.clj`） |
| 長連線（需登入/WebSocket） | **保持狀態** | `delay` + `defonce`，不加 hooks |

長連線組件避免在 reload 時重啟，以免中斷開發流程（如重新登入）。

## 領域慣例（progressive disclosure：做到該情境才載入對應 skill）

| 情境 | 載入 |
|------|------|
| 撰寫 resolver / Pathom / EQL | SKILL `/pathom3`（通用 library + 末段本專案 hexagonal / prefix / var 命名 / 新增步驟） |
| 碰 DB / append-only / entity / eid | SKILL `/db` |
| 加需驗證輸入的函數 / 定義資料契約（Malli） | SKILL `/malli` |
| CSS / 版面 / semantic HTML / `data-*` 狀態樣式 | SKILL `/css` |
| Datastar / SSE / signal / `data-*` / patch-elements | SKILL `/datastar` |
| 執行 Clojure 程式碼 | SKILL `/brepl` |
| 隔離驗證（worktree 平行實作 + 自驗） | `/isolated-verify` command |

**強制**：上表情境**回答或實作前必先 invoke 對應 skill，不得憑記憶答**——Datastar 1.0 與 CSS 慣例都已大改，舊知識會答錯。

## 文件更新
- 任何新增或修改功能時，必須同步更新 `README.md`（精簡地更新）
