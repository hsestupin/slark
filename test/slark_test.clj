(ns slark-test
  (:require [clojure
             [data :refer :all]
             [test :refer :all]]
            [clojure.core.async
             :refer
             [<!
              <!!
              >!
              >!!
              alt!
              alt!!
              alts!
              alts!!
              chan
              close!
              go
              go-loop
              offer!
              poll!
              put!
              timeout]]
            [slark :refer :all]))

(deftest bot-command?-test
  (testing "test bot-message?"
    (let [message {:message-id 23,
                   :from {:id 1234, :first-name "Sergey", :last-name "Stupin"}
                   :chat {:id 1234, :first-name "Sergey", :last-name "Stupin", :type "private"}
                   :date 1466715331
                   :text "/kuku me"
                   :entities [{:type "bot_command", :offset 0, :length 5}]}]
      (is (bot-command? message))))
  (testing "test not a bot command"
    (let [message {:message-id 23,
                   :from {:id 1234, :first-name "Sergey", :last-name "Stupin"}
                   :chat {:id 1234, :first-name "Sergey", :last-name "Stupin", :type "private"}
                   :date 1466715331
                   :text "/kuku me"}]
      (is (not (bot-command? message))))))

(deftest get-command-test
  (testing "test bot-message?"
    (let [message {:message-id 23,
                   :from {:id 1234, :first-name "Sergey", :last-name "Stupin"}
                   :chat {:id 1234, :first-name "Sergey", :last-name "Stupin", :type "private"}
                   :date 1466715331
                   :text "/kuku me"
                   :entities [{:type "bot_command", :offset 0, :length 5}]}]
      (is (= "kuku" (get-command message))))))

(defn- rand-long
  []
  (long (rand Long/MAX_VALUE)))

(defn gen-update
  "Generate new test update"
  ([] (gen-update (rand-long)))
  ([update-id]
   {:update-id update-id
    :message {:message-id (rand-long)
              :from {:id (rand-long)
                     :first-name "Sergey"
                     :last-name "Stupin"}
              :chat {:id (rand-long) :first-name "Sergey", :last-name "Stupin", :type "private"}
              :date (System/currentTimeMillis)
              :text "test message"}}))


(defn get-updates-from-ch-fn
  "Creates function which push new udpates from supplied channel. 
  It's a mock for real Telegram implementation."
  [ch]
  (fn get-updates-fn [offset]
    {:ok true :result [(<!! ch)]}))

(defn get-updates-from-coll-fn
  "Creates function which push new udpates from supplied coll.
  It's a mock for real Telegram implementation."
  [coll]
  (let [remained (atom (seq coll))]
    (fn [offset]
      (let [first-update (first @remained)]
        (swap! remained next)
        (if first-update
          {:ok true :result (into [] [first-update])}
          {:ok true :result :end})))))

(deftest updates-onto-chan-test
  (testing "Mechanism of push new updates onto a supplied channel."
    (testing "get updates from channel"
      (let [updates (chan)
            get-updates-fn (get-updates-from-coll-fn (take 3 (repeatedly gen-update)))
            terminate (updates-onto-chan updates {:get-updates-fn get-updates-fn})]
        (dotimes [n 3]
          (let [[val ch] (alts!! [updates (timeout 100)])]
            (is (= ch updates) "expect update from updates channel")
            (is (:ok val))
            (is (:update-id (first (:result val))))))
        (terminate)))))

(defn map=
  "Map deep comparison"
  [map1 map2]
  (let [d (diff map1 map2)]
    (and (nil? (first d))
         (nil? (second d)))))

(deftest conj-command-test
  (testing "Non-command update doesn't change with `conj-command`"
    (let [update (gen-update)]
      (is (map= update (conj-command update)))))
  (testing "Command updates gets extended with :command value"
    (let [update (->
                  (gen-update)
                  (update-in [:message :text]
                             (constantly "/test hello"))
                  (update-in [:message]
                             #(conj % [:entities [{:type "bot_command" :offset 0 :length 5}]])))]
      (is (map= (conj update [:command "test"]) (conj-command update))))))
