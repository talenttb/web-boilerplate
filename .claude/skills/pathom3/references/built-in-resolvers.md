# Built-in Resolvers 完整參考

Pathom 內建的 resolver 生成器，位於 `com.wsscode.pathom3.connect.built-in.resolvers` (別名 `pbir`)。

## 目錄

1. [Constants](#constants)
2. [Aliasing](#aliasing)
3. [Equivalence](#equivalence)
4. [Single Attribute Transformation](#single-attribute-transformation)
5. [Static Tables](#static-tables)
6. [Static Attribute Map](#static-attribute-map)
7. [Attribute Tables](#attribute-tables)
8. [Environment Tables](#environment-tables)
9. [EDN Files](#edn-files)
10. [Global Data](#global-data)

## Constants

提供常數值的 resolver。

```clojure
; 常數值
(pbir/constantly-resolver :math/PI 3.1415)

; 動態生成的常數（每次呼叫都會執行函數）
(pbir/constantly-fn-resolver ::now (fn [_] (java.util.Date.)))

; 錯誤處理範例
(pbir/constantly-fn-resolver ::throw-error 
  (fn [_] (throw (ex-info "Error" {}))))
```

## Aliasing

建立單向別名，將一個屬性對應到另一個屬性。

```clojure
(pbir/alias-resolver :specific.impl.product/id :generic.ui.product/id)

; 等同於
(pco/defresolver spec->generic-prod-id [{:specific.impl.product/keys [id]}]
  {:generic.ui.product/id id})
```

**注意**：別名是單向的。如果只有 `:generic.ui.product/id`，無法反推回 `:specific.impl.product/id`。

## Equivalence

建立雙向等價關係，表示兩個屬性語義相同。

```clojure
(pbir/equivalence-resolver :acme.product/upc :affiliate.product/upc)
```

這實際上會建立兩個方向的 alias resolver。

## Single Attribute Transformation

單屬性轉換，將一個屬性轉換成另一個屬性。

```clojure
; 毫秒轉秒
(pbir/single-attr-resolver :track/duration-ms :track/duration-seconds 
  #(/ % 1000))

; 秒轉毫秒
(pbir/single-attr-resolver :track/duration-seconds :track/duration-ms 
  #(* % 1000))

; 需要存取 env 時使用 single-attr-with-env-resolver
(pbir/single-attr-with-env-resolver :input :output
  (fn [env value] (transform env value)))
```

## Static Tables

根據索引鍵提供靜態資料的 resolver。

```clojure
; 基本用法
(pbir/static-table-resolver :song/id
  {1 {:song/name "Song A" :song/duration 280}
   2 {:song/name "Song B" :song/duration 150}})

; 指定 resolver 名稱（推薦使用完整命名符號）
(pbir/static-table-resolver `song-analysis :song/id
  {1 {:song/duration 280 :song/tempo 98}
   2 {:song/duration 150 :song/tempo 130}})
```

輸出會從表格資料自動推斷。

## Static Attribute Map

類似 static tables，但只提供單一屬性。

```clojure
(pbir/static-attribute-map-resolver :song/id :song/name
  {1 "Song A"
   2 "Song B"})
```

## Attribute Tables

表格資料來自另一個屬性（而非靜態資料）。

```clojure
; 提供表格資料的 resolver
(pbir/constantly-resolver ::song-analysis
  {1 {:song/duration 280 :song/tempo 98}
   2 {:song/duration 150 :song/tempo 130}})

; 使用屬性表格
(pbir/attribute-table-resolver ::song-analysis :song/id
  [:song/duration :song/tempo])
```

## Environment Tables

類似 attribute tables，但表格資料來自環境 (env)。

```clojure
; 定義 resolver
(pbir/env-table-resolver ::song-analysis :song/id
  [:song/duration :song/tempo])

; 使用時將表格加入 env
(def song-details-table
  {1 {:song/duration 280 :song/tempo 98}
   2 {:song/duration 150 :song/tempo 130}})

(psm/smart-map 
  (assoc (pci/register registry) ::song-analysis song-details-table)
  {:song/id 2})
```

## EDN Files

從 EDN 檔案載入屬性（編譯時載入）。

```clojure
; my-config.edn
; {:my.system.server/port 1234
;  :my.system.resources/path "resources/public"}

(def registry
  [(pbir/edn-file-resolver "my-config.edn")
   full-url])
```

### EDN 檔案中的靜態表格

```clojure
; my-config.edn
{:my.system.server/port 1234
 :my.system/generic-db
 ^{:com.wsscode.pathom3/entity-table :my.system/user-id}
 {4 {:my.system.user/name "Anne"}
  2 {:my.system.user/name "Fred"}}}
```

**注意**：這是巨集，在 ClojureScript 中會在編譯時讀取 EDN。

## Global Data

與 EDN Files 相同功能，但直接使用資料。

```clojure
(pbir/global-data-resolver 
  {:my.system.server/port 1234
   :my.system.resources/path "resources/public"})
```
