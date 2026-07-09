---
name: malli
description: web-boilerplate 用 Malli 做 boundary / contract validation 的慣例 + Malli 語法速查。觸發詞：malli, schema, m/validate, m/explain, m/validator, humanize, 驗證, contract, boundary。新增需驗證輸入的函數、定義資料契約、回人類可讀錯誤前載入。
---

# Malli

## 本專案怎麼用 Malli

每個需要驗證的 namespace 自己定義 **named schema 當 contract source of truth**，在邊界入口 inline 驗證：`m/validate` 失敗 → `m/explain` → `me/humanize` 包成可讀錯誤 throw（帶 `:humanized` / `:details`）。

真實範例（直接讀，別憑記憶）：
- `src/web_boilerplate/config.clj` — `AppConfig`
- `src/web_boilerplate/db.clj` — `EntityWrite` / `IndexSpec`

慣例：schema 對齊上游（vendor SDK）的資料結構；保持簡潔不過度設計。

## 語法速查

Vector syntax：`type` 或 `[type props & children]`

```clojure
:string                          ; 純 type
[:string {:min 1, :max 10}]      ; 帶 properties
[:map                            ; map（預設 open）
 [:x :int]
 [:y {:optional true} :int]      ; optional key
 [:z :string]]
[:map {:closed true} [:x :int]]  ; closed：多餘 key 即 fail
[:map-of :string :int]           ; homogeneous map
[:enum "ok" "error"]             ; 列舉
[:tuple :double :double]         ; 定長
[:and :int [:> 18]]              ; 組合
[:maybe :string]                 ; nilable
[:vector :int] [:set :keyword] [:sequential :any]
```

常用 type：`:string :int :double :boolean :keyword :qualified-keyword :uuid :any` + core predicate（`string?` 等）。注意 predicate 不吃 `:min` / `:max` properties，需要 properties 用 `:string` 這種真 schema。

## 驗證 & 人類可讀錯誤

```clojure
(m/validate schema value)          ; => true / false
(def valid? (m/validator schema))  ; 預編譯，熱路徑用
(m/explain schema value)           ; => nil 或 {:errors [...] :value ... :schema ...}
(me/humanize (m/explain schema value))
;; => {:tags #{["should be a keyword"]} :address {:city ["missing required key"]}}
```

boundary 驗證的標準收尾：

```clojure
(when-not (m/validate S v)
  (throw (ex-info "..." {:details (me/humanize (m/explain S v))})))
```

## 完整參考

上游 README（語法 / transform / registry / JSON Schema 等全部）：https://github.com/metosin/malli

常用章節：Syntax、Validation、Humanized error messages、Value transformation / Coercion、To and from JSON、Schema registry。
