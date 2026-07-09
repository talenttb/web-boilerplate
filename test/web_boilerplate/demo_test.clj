(ns web-boilerplate.demo-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [web-boilerplate.demo :as demo]))

(deftest expenses-total-test
  (testing "加總所有支出金額"
    (is (= 12800 (demo/expenses-total [{:amount 3000} {:amount 6600} {:amount 2400} {:amount 800}]))))
  (testing "空支出回傳 0"
    (is (= 0 (demo/expenses-total [])))))

(deftest member-balances-test
  (testing "淨額總和恆為 0"
    (let [members ["小明" "小美" "阿宏"]
          expenses [{:payer "小明" :amount 3000} {:payer "小美" :amount 6600}
                    {:payer "阿宏" :amount 2400} {:payer "小明" :amount 800}]
          balances (demo/member-balances members expenses)]
      (is (= 0 (reduce + (map :balance balances))))))
  (testing "無支出時每人淨額為 0"
    (let [balances (demo/member-balances ["小明" "小美"] [])]
      (is (every? (comp zero? :balance) balances))))
  (testing "空成員回傳空 vector"
    (is (= [] (demo/member-balances [] [])))))

(deftest settle-transfers-test
  (testing "轉帳建議能讓每人淨額歸零"
    (let [members ["小明" "小美" "阿宏"]
          expenses [{:payer "小明" :amount 3000} {:payer "小美" :amount 6600}
                    {:payer "阿宏" :amount 2400} {:payer "小明" :amount 800}]
          balances (demo/member-balances members expenses)
          transfers (demo/settle-transfers balances)
          settled (reduce (fn [acc {:keys [from to amount]}]
                            (-> acc (update from + amount) (update to - amount)))
                          (into {} (map (juxt :member :balance) balances))
                          transfers)]
      (is (every? zero? (vals settled)))))
  (testing "筆數不超過成員數減一"
    (let [members ["小明" "小美" "阿宏" "小華"]
          expenses [{:payer "小明" :amount 4000} {:payer "小美" :amount 0}
                    {:payer "阿宏" :amount 0} {:payer "小華" :amount 0}]
          balances (demo/member-balances members expenses)
          transfers (demo/settle-transfers balances)]
      (is (<= (count transfers) (dec (count members))))))
  (testing "淨額全為 0 時不產生轉帳"
    (is (= [] (demo/settle-transfers [{:member "小明" :balance 0} {:member "小美" :balance 0}])))))
