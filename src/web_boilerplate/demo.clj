(ns web-boilerplate.demo
  (:require
   [jsonista.core :as json]
   [web-boilerplate.util :as util]))

(defonce state
  (atom {:members ["小明" "小美" "阿宏"]
         :expenses [{:id (util/gen-nanoid) :payer "小明" :description "高鐵來回" :amount 3000}
                    {:id (util/gen-nanoid) :payer "小美" :description "民宿兩晚" :amount 6600}
                    {:id (util/gen-nanoid) :payer "阿宏" :description "熱炒晚餐" :amount 2400}
                    {:id (util/gen-nanoid) :payer "小明" :description "租機車" :amount 800}]}))

(defn expenses-total [expenses]
  (reduce + 0 (map :amount expenses)))

(defn member-balances [members expenses]
  (let [n (count members)]
    (if (zero? n)
      []
      (let [total (expenses-total expenses)
            base (quot total n)
            extra (mod total n)
            paid-by (reduce (fn [acc {:keys [payer amount]}] (update acc payer (fnil + 0) amount)) {} expenses)]
        (vec (map-indexed
               (fn [i member]
                 (let [share (+ base (if (< i extra) 1 0))
                       paid (get paid-by member 0)]
                   {:member member :paid paid :share share :balance (- paid share)}))
               members))))))

(defn settle-transfers [balances]
  (loop [creditors (->> balances
                         (keep (fn [{:keys [member balance]}] (when (pos? balance) [member balance])))
                         (sort-by second >))
         debtors (->> balances
                      (keep (fn [{:keys [member balance]}] (when (neg? balance) [member (- balance)])))
                      (sort-by second >))
         transfers []]
    (if (or (empty? creditors) (empty? debtors))
      transfers
      (let [[creditor credit] (first creditors)
            [debtor debt] (first debtors)
            amount (min credit debt)]
        (recur (->> (rest creditors) (cons [creditor (- credit amount)]) (remove (comp zero? second)) (sort-by second >))
               (->> (rest debtors) (cons [debtor (- debt amount)]) (remove (comp zero? second)) (sort-by second >))
               (conj transfers {:from debtor :to creditor :amount amount}))))))

(comment
  (expenses-total (:expenses @state))
  (member-balances (:members @state) (:expenses @state))
  (settle-transfers (member-balances (:members @state) (:expenses @state)))
  ;;
  )

(defn add-member! [state name]
  (swap! state update :members (fnil conj []) name))

(defn add-expense! [state {:keys [payer description amount]}]
  (swap! state update :expenses (fnil conj [])
         {:id (util/gen-nanoid)
          :payer payer
          :description description
          :amount (long (if (number? amount) amount (Double/parseDouble (str amount))))}))

(defn remove-expense! [state id]
  (swap! state update :expenses (fn [expenses] (vec (remove #(= id (:id %)) expenses)))))

(comment
  (add-member! state "小華")
  (add-expense! state {:payer "小明" :description "測試" :amount 100})
  (remove-expense! state (:id (last (:expenses @state))))
  ;;
  )

(def split-bill-head
  [:head
   [:meta {:charset "UTF-8"}]
   [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
   [:title "旅費分帳"]
   [:link {:rel "icon" :href "data:,"}]
   [:link {:rel "stylesheet" :href "/css/app.css"}]
   [:link {:rel "stylesheet" :href "/css/demo.css"}]
   [:script {:type "module"
             :src "https://cdn.jsdelivr.net/gh/starfederation/datastar@1.0.0-RC.7/bundles/datastar.js"}]])

(defn split-bill-view [{:keys [members expenses total balances transfers]}]
  [:section {:id "split-bill-view" :class "split-bill"
             :data-signals (json/write-value-as-string
                             {:action "" :memberName ""
                              :expensePayer (first members) :expenseDescription "" :expenseAmount ""
                              :expenseId ""})}
   [:h1 "旅費分帳"]
   [:article {:class "split-bill-card"}
    [:h2 "成員"]
    [:ul {:class "member-chips"}
     (for [member members] [:li member])]
    [:form {:data-on:submit__prevent "$action='add-member'; @post('/demo')"}
     [:input {:type "text" :data-bind "memberName" :placeholder "姓名" :required true}]
     [:button {:type "submit"} "加入"]]]
   [:article {:class "split-bill-card"}
    [:h2 "支出"]
    [:table
     [:thead [:tr [:th "誰付"] [:th "項目"] [:th "金額"] [:th ""]]]
     [:tbody
      (for [{:keys [id payer description amount]} expenses]
        [:tr
         [:td payer]
         [:td description]
         [:td {:data-num true} amount]
         [:td [:button {:type "button"
                        :data-on:click (str "$action='remove-expense'; $expenseId='" id "'; @post('/demo')")}
              "✕"]]])]
     [:tfoot
      [:tr
       [:td [:select {:data-bind "expensePayer"}
             (for [member members] [:option {:value member} member])]]
       [:td [:input {:type "text" :data-bind "expenseDescription" :placeholder "項目"}]]
       [:td [:input {:type "number" :data-bind "expenseAmount" :placeholder "金額"}]]
       [:td [:button {:type "button" :data-on:click "$action='add-expense'; @post('/demo')"} "新增"]]]]]]
   [:article {:class "split-bill-card"}
    [:h2 "結算"]
    [:dl {:class "split-bill-summary"}
     [:div [:dt "總支出"] [:dd {:data-num true} total]]
     [:div [:dt "每人均攤"] [:dd {:data-num true} (when (seq members) (quot total (count members)))]]]
    [:table
     [:thead [:tr [:th "成員"] [:th "已付"] [:th "淨額"]]]
     [:tbody
      (for [{:keys [member paid balance]} balances]
        [:tr
         [:td member]
         [:td {:data-num true} paid]
         [:td {:data-num true :data-tone (cond (pos? balance) "positive" (neg? balance) "negative" :else "neutral")} balance]])]]
    [:h3 "轉帳建議"]
    (if (seq transfers)
      [:ul {:class "settle-transfers"}
       (for [{:keys [from to amount]} transfers]
         [:li (str from " → " to " $" amount)])]
      [:p "已結清"])]])
