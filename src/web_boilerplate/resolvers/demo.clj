(ns web-boilerplate.resolvers.demo
  (:require
   [com.wsscode.pathom3.connect.operation :as pco]
   [web-boilerplate.db :as db]
   [web-boilerplate.demo :as demo]))

(pco/defresolver split-members [{:demo/keys [state]} _]
  {::pco/output [:demo.split/members]}
  {:demo.split/members (:members @state)})

(pco/defresolver split-expenses [{:demo/keys [state]} _]
  {::pco/output [:demo.split/expenses]}
  {:demo.split/expenses (:expenses @state)})

(pco/defresolver split-total [_ {:demo.split/keys [expenses]}]
  {::pco/input  [:demo.split/expenses]
   ::pco/output [:demo.split/total]}
  {:demo.split/total (demo/expenses-total expenses)})

(pco/defresolver split-balances [_ {:demo.split/keys [members expenses]}]
  {::pco/input  [:demo.split/members :demo.split/expenses]
   ::pco/output [:demo.split/balances]}
  {:demo.split/balances (demo/member-balances members expenses)})

(pco/defresolver split-transfers [_ {:demo.split/keys [balances]}]
  {::pco/input  [:demo.split/balances]
   ::pco/output [:demo.split/transfers]}
  {:demo.split/transfers (demo/settle-transfers balances)})

(pco/defresolver demo:<split-bill-view>
  [_ {:demo.split/keys [members expenses total balances transfers]}]
  {::pco/input  [:demo.split/members :demo.split/expenses :demo.split/total
                 :demo.split/balances :demo.split/transfers]
   ::pco/output [:demo/<split-bill-view>]}
  {:demo/<split-bill-view>
   (demo/split-bill-view {:members members :expenses expenses :total total
                          :balances balances :transfers transfers})})

(pco/defmutation expense-add! [{:demo/keys [state]} {:keys [payer description amount]}]
  {::pco/op-name 'demo.expense/add!
   ::pco/output  [:demo.expense.add/ok?]}
  (demo/add-expense! state {:payer payer :description description :amount amount})
  {:demo.expense.add/ok? true})

(pco/defmutation expense-remove! [{:demo/keys [state]} {:keys [id]}]
  {::pco/op-name 'demo.expense/remove!
   ::pco/output  [:demo.expense.remove/ok?]}
  (demo/remove-expense! state id)
  {:demo.expense.remove/ok? true})

(pco/defmutation member-add! [{:demo/keys [state]} {:keys [name]}]
  {::pco/op-name 'demo.member/add!
   ::pco/output  [:demo.member.add/ok?]}
  (demo/add-member! state name)
  {:demo.member.add/ok? true})

(def all-resolvers
  [split-members
   split-expenses
   split-total
   split-balances
   split-transfers
   demo:<split-bill-view>
   expense-add!
   expense-remove!
   member-add!])

(pco/defresolver trip-archive-list [{:db/keys [ds]} _]
  {::pco/output [{:demo.trip/archive-list [:demo.trip/id :demo.trip/snapshot]}]}
  {:demo.trip/archive-list
   (vec (for [{:keys [data]} (db/get-ref-by-kind ds "trip")]
          {:demo.trip/id (:id data) :demo.trip/snapshot data}))})

(pco/defresolver trip-snapshot [{:db/keys [ds]} {:demo.trip/keys [id]}]
  {::pco/input  [:demo.trip/id]
   ::pco/output [:demo.trip/snapshot]}
  {:demo.trip/snapshot (:data (db/get-ref ds (str "trip_" id) {:select [:data]}))})

(pco/defresolver trip-commit-history [{:db/keys [ds]} {:demo.trip/keys [id]}]
  {::pco/input  [:demo.trip/id]
   ::pco/output [:demo.trip/history]}
  {:demo.trip/history (mapv :data (db/get-commits ds (str "trip_" id)))})

(def db-example-resolvers
  "接上 PostgreSQL 後的取數範例（未註冊）：pathom env 加 :db/ds、registry 改 (pci/register (into all-resolvers db-example-resolvers)) 即可用。三顆分別示範 get-ref-by-kind／get-ref＋:select 投影／get-commits（append-only 的 audit log）。"
  [trip-archive-list trip-snapshot trip-commit-history])

(comment
  (def ds (db/get-datasource))
  (db/merge! ds "trip_demo1" {:kind "trip" :id "demo1" :name "花蓮三日" :total 12800})
  (db/get-ref-by-kind ds "trip")
  (:data (db/get-ref ds "trip_demo1" {:select [:data]}))
  (db/get-commits ds "trip_demo1"))
