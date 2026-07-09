---
description: 在隔離 worktree + 持久 JVM 上實作並驗證一個功能（主 session 起 JVM + 叫 isolated-verify workflow + 交付 live endpoint 等你驗 + 點頭才 merge）
argument-hint: <任務描述，例如：分帳結算卡改依金額排序>
disable-model-invocation: true
---

你現在是「隔離驗證」的主 session 編排者。任務描述：$ARGUMENTS

這是你（主 session）的劇本。互動、叫 workflow、撐 JVM、收尾都在這一層做——worker 那層交給 `isolated-verify` workflow。依序執行：

## 1. 開隔離環境 + 起持久 JVM
- 用 `EnterWorktree` 開一個新 branch 的 worktree（cwd 切入）。
- 在 worktree 內用 Bash `run_in_background` 跑 `bb worktree-up`（它會找空 port + copy-secrets + 背景啟 JVM；會 block，所以一定 background）。
- 等 JVM 起來：用 Bash `run_in_background` 跑 `bb wait-nrepl`（它 block 到 `.nrepl-port` 出現就印 port 並 exit；timeout 90s → exit 1，回報失敗別硬上）。**不要**自己寫 shell for/poll 迴圈等——`$(…)` 會被擋成「Contains expansion」、每次都重問。
- 這顆 JVM 由你全程持有，**使用者驗證期間不要關**。

## 2. 起 web + 讀三個 port（port 一律讀檔/讀 config，不要自己編）
- 載入 `brepl` skill，連入跑 `(user/start)`（起 web + Portal）。
- 讀 port：
  - `nreplPort` = 該 worktree `.nrepl-port` 檔內容
  - `webPort`   = brepl `(get-in (config/get-config) [:server :port])`
  - `portalPort`= brepl `(get-in (config/get-config) [:portal :port])`
- `worktreePath` = `git rev-parse --show-toplevel`（目前 cwd）
- `branch` = `git rev-parse --abbrev-ref HEAD`

## 3. 叫 workflow
- 呼叫 `Workflow`，name `isolated-verify`，args **必須傳真正的 JSON 物件，不可傳 JSON 字串**（傳字串的話 script 收到 string、`args.worktreePath` 是 undefined、直接回 `ok:false`）：
  `{ worktreePath, nreplPort, webPort, portalPort, taskSpec: "$ARGUMENTS", branch }`
- 它背景跑（implement→evidence→gate→review→commit），跑完會通知你。

## 4. 收結果、檢查
- `ok=false` 或 `evidencePaths` 空 → 把 `reason` 如實告訴使用者，問要不要重試。不要自己宣稱成功。使用者若給了調整方向（要改 taskSpec），不要直接併進去重跑——先走 step 6「說要改」那道「確認背景＋目標、問過才動手」的 gate，點頭後才回 step 3。
- `ok=true` → 進 step 5。

## 5. 固定模板交付 + 把回合交回使用者驗證（照這格式，不要自由發揮；**這一步不問 merge**）
```
✅ 隔離驗證完成（尚未 merge）
web    → http://localhost:<webPort>/
nREPL  → <nreplPort>
Portal → http://localhost:<portalPort>
證據   → <逐一列出 evidencePaths 每個絕對路徑>
review → <review.verdict>（concerns 就把 review.issues 逐條列出）
branch → <branch> @ <commitHash>
```
- 交付完這個模板，最後加一句「請你開上面的 web 親自驗證；驗完回我『OK / 可以 merge』我才會 merge，要改直接講」，然後**就停在這裡、把回合交回給使用者**。
- **這一步不要用 `AskUserQuestion`、不要列 merge 選單、不要自己 merge、不要關 JVM。** 「workflow 完成」是通知，不是 merge 授權——授權是使用者親自驗證後、另一個回合的事。

## 6. 使用者回來、明確點頭後才收尾
- 使用者**明確說 OK / 可以 merge** → 依序 squash merge 收尾：
  1. `git -C <main-path> status` 確認 main 乾淨（不乾淨就回報、別硬 merge）。
  2. `git -C <main-path> merge --squash <branch>`：把整條 branch 壓成 staged 改動，**不自動產 commit**。
  3. `git -C <main-path> commit`：用 conventional-commit 訊息（正體中文，描述這個功能；結尾加 `Co-Authored-By` trailer）落一筆——這是 squash 後 main 上唯一的 commit。
  4. `ExitWorktree`（移除 worktree）：branch 還 checked out 在 worktree 時不能刪，先移 worktree 釋放。
  5. `git -C <main-path> branch -D <branch>`：squash merge 不被 git 記成已合併，`-d` 會擋，用 `-D` 強刪。
  - 回報 merge 結果 + squash commit hash。
- 使用者**說要改** → **先別急著回 step 3**。修改反饋等同一個新任務，動手前必須先做背景＋目標確認、問過才開跑：
  1. 用自己的話覆述：使用者要改什麼、為什麼改（背景）、改完要達成的狀態（目標），並列出你看到的分歧點 / 不確定處。
  2. 背景＋目標講清楚、等使用者確認無誤後，明確問一句「這樣動手對嗎？」。
  3. 使用者點頭後，才把修正併進 taskSpec、回 step 3 再叫一次 workflow。
  - 沒拿到「背景＋目標確認」＋「動手點頭」前什麼都別改；**JVM 不要關**。
- 使用者**還在驗 / 沒明確點頭** → 什麼都別做，繼續等，JVM 不關。**沒有「OK」就絕不 merge。**

## 硬規則
- 使用者過程中的任何修改反饋（step 4 調整方向、step 6 說要改），一律先覆述背景＋目標、問過「這樣動手對嗎」才回 step 3，不直接把反饋翻成 taskSpec 就開跑。
- JVM 由主 session 持有，使用者明確點頭前不 merge、不刪 branch / worktree。
- 不啟動 runner、不動 `:caps`（即時 feed 由 main 的 runner fan-out；worktree 只驗靜態）。
- 所有 worktree 共用同一個 DB（append-only + optimistic CAS 本就能正確並發），不另起 DB。
- port 一律讀檔 / 讀 config 得來，不要用記憶或臆測的值。
