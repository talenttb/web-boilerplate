(ns web-boilerplate.resolvers.demo
  (:require
   [com.wsscode.pathom3.connect.operation :as pco]
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
