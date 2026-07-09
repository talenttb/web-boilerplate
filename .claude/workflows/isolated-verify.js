export const meta = {
  name: 'isolated-verify',
  description: '在主 session 已起好的隔離 worktree+JVM 上：implement→(user/reset)→產證據(一律落檔)→證據 gate(沒過自動重跑)→review→commit，回傳結構化結果(含 ports/證據路徑)給主 session 交付使用者',
  phases: [
    { title: 'Implement' },
    { title: 'Evidence' },
    { title: 'Gate' },
    { title: 'Review' },
    { title: 'Commit' },
  ],
}

let input = args
if (typeof input === 'string') { try { input = JSON.parse(input) } catch (_) { input = null } }
if (!input || !input.worktreePath || !input.nreplPort || !input.taskSpec || !input.branch) {
  return { ok: false, reason: '缺前置 args：需要 worktreePath / nreplPort / webPort / portalPort / taskSpec / branch 的 JSON 物件（主 session 要先 bb worktree-up 並讀 .nrepl-port + config 填入；注意 args 要傳物件，不是 JSON 字串）' }
}

const { worktreePath, nreplPort, webPort, portalPort, taskSpec, branch } = input

const base = (body) => `
你在「既存」worktree 作業，絕對路徑：${worktreePath}
不要開新 worktree、不要跑 bb worktree-up、不要 isolation。
所有 git 一律 git -C ${worktreePath}；所有 Clojure 一律 brepl --p ${nreplPort}（heredoc 模式）。
硬規則：禁註解/docstring；Backward Compatible（不可 in-place 改寫既有 fn/resolver、不做 migration）；單人使用。
改 src/ 後唯一 reload 是 (user/reset)，禁 :reload / :reload-all。
${body}`

const MAX = 3
let implResult = null
let evResult = null

for (let i = 0; i < MAX; i++) {
  phase('Implement')
  const impl = await agent(base(`
任務：${taskSpec}
做完變更後 brepl --p ${nreplPort} 跑 (user/reset)，斷言沒有 exception
（看到 7888 Address already in use 屬正常忽略；硬失敗會直接 throw、不會印 ✓ Code reloaded——沒看到 ✓ 就是壞了，修好再繼續）。`), {
    label: `implement#${i + 1}`, phase: 'Implement',
    schema: {
      type: 'object', additionalProperties: false,
      required: ['changedFiles', 'resetOk'],
      properties: {
        changedFiles: { type: 'array', items: { type: 'string' } },
        resetOk: { type: 'boolean' },
      },
    },
  })
  if (!impl.resetOk) { log(`第 ${i + 1} 次 reset 失敗，重跑 implement`); continue }

  phase('Evidence')
  const ev = await agent(base(`
做靜態 sanity 自驗（不驗 streaming / live tick）。證據「一律落檔」到 ${worktreePath}/workspace/：
- 改畫面 / DOM → 用 playwright 截圖，launch 依改動種類分流：
  · 純 DOM／功能／內容（結構、文字、屬性、公開 API 在不在）→ chromium.launch({ headless: true })，快即可。
  · CSS／樣式／對齊／canvas／任何像素渲染 → 真 Chrome：chromium.launch({ channel: "chrome", headless: false })，不可 headless。
  · 混合或拿不準 → 一律走真 Chrome（superset，不會漏）。
  視覺分支（CSS/canvas）固定 newPage({ viewport: { width: 1440, height: 900 } })，screenshot 用 clip 框目標元素或 viewport，禁 fullPage。
  載 http://localhost:${webPort}/<頁面>，截圖存 ${worktreePath}/workspace/<語意檔名>.png
  （flat 檔名，禁 /tmp、禁 final_runs 深層結構）。回報／顯示截圖時一律給完整絕對路徑。
- 純後端 / 資料 / 函數 → brepl 驗 return / DB，(tap> {:asserted .. :actual ..}) 進 Portal，
  並 (spit "${worktreePath}/workspace/evidence-<語意名>.edn" (pr-str {:asserted .. :actual ..})) 把同一份斷言+實際值落檔。
- 純 refactor / 改名 / config → 跑 probe 證明既有行為沒壞（同一 query reset 前後同值、或頁面照樣 render），結果一樣寫進 ${worktreePath}/workspace/ 的檔。
回傳每個證據檔的絕對路徑、種類、一句 sanity 結論。`), {
    label: `evidence#${i + 1}`, phase: 'Evidence',
    schema: {
      type: 'object', additionalProperties: false,
      required: ['evidencePaths', 'evidenceKind', 'sanity'],
      properties: {
        evidencePaths: { type: 'array', items: { type: 'string' } },
        evidenceKind: { type: 'string', enum: ['screenshot', 'portal-tap', 'probe'] },
        sanity: { type: 'string' },
      },
    },
  })

  phase('Gate')
  const gate = await agent(base(`
逐一用 Bash 對下列每個證據檔跑 ls -la，照實回報實際 byte size（不要臆測，看不到就回 0）。
另外對每個 .png 截圖：用 Read 直接看圖，確認畫面真的呈現了這次改動該有的東西
（不是空白、不是錯誤頁、不是 loading），回報 visualOk 與一句「你看到什麼」（非截圖檔填 visualOk:true、visualNote:"非截圖"）：
${ev.evidencePaths.map((p) => `- ${p}`).join('\n')}`), {
    label: `gate#${i + 1}`, phase: 'Gate',
    schema: {
      type: 'object', additionalProperties: false,
      required: ['files'],
      properties: {
        files: {
          type: 'array',
          items: {
            type: 'object', additionalProperties: false,
            required: ['path', 'size', 'visualOk', 'visualNote'],
            properties: { path: { type: 'string' }, size: { type: 'number' }, visualOk: { type: 'boolean' }, visualNote: { type: 'string' } },
          },
        },
      },
    },
  })

  const gateOk = gate.files.length >= ev.evidencePaths.length
    && gate.files.every((f) => f.size > 0)
    && gate.files.filter((f) => f.path.endsWith('.png')).every((f) => f.visualOk === true)
  if (gateOk) { implResult = impl; evResult = ev; break }
  log(`第 ${i + 1} 次證據 gate 沒過（有檔不存在或 size 0），重跑 implement`)
}

if (!evResult) {
  return {
    ok: false,
    reason: `${MAX} 次內無法產出可檢查的證據檔`,
    ports: { nrepl: nreplPort, web: webPort, portal: portalPort },
  }
}

phase('Review')
const review = await agent(base(`
git -C ${worktreePath} diff 自己看過，對照本專案 CLAUDE.md 硬規則 review 這次改動
（Backward Compatible、禁 in-place 改 resolver、禁 migration、resolver prefix、append-only、禁註解、:kind 不塞次分類）。
並確認剛產的證據（${evResult.evidencePaths.join(', ')}）確實對應這次改動、不是無關的舊檔。
回傳 verdict（pass / concerns）與問題清單。`), {
  label: 'review', phase: 'Review', agentType: 'clj-convention-reviewer',
  schema: {
    type: 'object', additionalProperties: false,
    required: ['verdict', 'issues'],
    properties: {
      verdict: { type: 'string', enum: ['pass', 'concerns'] },
      issues: { type: 'array', items: { type: 'string' } },
    },
  },
})

phase('Commit')
const commit = await agent(base(`
git -C ${worktreePath} diff 確認後，把改動 commit 到 branch ${branch}（只 commit，不 merge / push / 刪 branch / worktree）。
回傳 commit hash。`), {
  label: 'commit', phase: 'Commit',
  schema: {
    type: 'object', additionalProperties: false,
    required: ['commitHash'],
    properties: { commitHash: { type: 'string' } },
  },
})

return {
  ok: true,
  changedFiles: implResult.changedFiles,
  evidencePaths: evResult.evidencePaths,
  evidenceKind: evResult.evidenceKind,
  sanity: evResult.sanity,
  review,
  branch,
  commitHash: commit.commitHash,
  ports: { nrepl: nreplPort, web: webPort, portal: portalPort },
}
