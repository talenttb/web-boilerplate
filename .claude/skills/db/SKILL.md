---
name: db
description: web-boilerplate 的 append-only DB 慣例（commits/refs 兩張通用表、entity shape、:kind 規則、對外投影）。觸發詞：db, append-only, commits, refs, get-ref, write!, merge!, delete!, eid, kind, datasource, optimistic CAS。新增 domain、寫 DB 讀寫、設計 entity 前載入。
---

# Append-only DB

DB 層（`web-boilerplate.db`）採 append-only pattern。詳細架構見 `README.md`「Append-only DB」段。

## 兩張通用表
- `commits`：append-only log，每次寫入新增一筆
- `refs`：current state 投影，每個 eid 一筆

所有 domain 共用，透過 `data.kind` 區分。

## Entity shape

```clojure
{:eid  "<kind>_..."
 :data {:kind "<domain>" ...}}  ; kind 是唯一 hard 要求
```

## API

公開函數見 `src/web_boilerplate/db.clj`，對外常用：

- `get-ref` / `get-ref-by-kind`：讀 current state；`get-ref-by-kind` 自動過濾 soft-deleted
- `get-commits` / `get-commit-at`：歷史（= 免費 undo / audit log）/ time travel
- `write!` / `merge!` / `delete!`：optimistic CAS 寫入（衝突 throw）/ patch / soft delete
- 投影選項 `{:select [:data]}`：省 wire、避免外洩 `:commit-id` / `:t` 等 db meta

對外 resolver 一律走 `{:select [:data]}` 投影、回傳前 unpack `:data`：`:eid` / `:commit-id` / `:t` 是 db 內部欄位，不對 caller 暴露。需要時間資訊把它寫進 data（如 `:updated-at`，標準 ISO-8601 UTC string）。

## 慣例

- **新 domain 不開 table、不寫 DDL、不 `ensure-index!`**（既有 `refs_kind_active` 已涵蓋 kind 過濾）
- **邏輯 key 用 map**（如 `{:kind, :symbol}`），轉 eid 字串是 domain helper 的事（呼應「避免字串拼接作為 key」）
- **`:kind` 只是純 domain 名稱**，不准塞次分類：
  ```
  ✗ :kind "chart-overlay/trendline"
  ✗ :kind "user-admin"
  ✓ :kind "chart-overlay"  :type "trendline"
  ✓ :kind "user"           :role "admin"
  ```
  要細分，**另開欄位**（`:type` / `:subtype` / `:role` / `:variant`）。
