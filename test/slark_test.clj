(ns slark-test
  (:require [clojure.test :refer :all]
            [slark :refer :all]
            [clojure.core.async :refer (close! put! poll! go go-loop chan <! <!! >! >!!
                                               alt! alts! alt!! alts!! timeout offer!)]))

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

(defn gen-updates
  "Generate vector of `n` updates "
  [n]
  (into [] (repeatedly n gen-update)))


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
        {:ok true :result (into [] [first-update])}))))

(deftest updates-onto-chan-test
  (testing "Mechanism of push new updates onto a supplied channel."
    (testing "get updates from channel"
      (let [updates (chan)
            get-updates-fn (get-updates-from-coll-fn (gen-updates 3))
            terminate (updates-onto-chan updates {:get-updates-fn get-updates-fn})]
        (dotimes [n 3]
          (let [[val ch] (alts!! [updates (timeout 100)])]
            (is (:update-id val))))
        (is (nil? (poll! updates)))
        (terminate)))

    (testing "offset increments"
      (let [updates (chan 1)
            telegram-updates-ch (chan 1)
            last-offset (atom 0)
            get-updates-fn (fn get-updates-fn [offset]
                             (reset! last-offset offset)
                             {:ok true :result [(<!! telegram-updates-ch)]})
            terminate (updates-onto-chan updates {:get-updates-fn get-updates-fn})]
        (is (= 0 @last-offset))
        (>!! telegram-updates-ch (gen-update 10))
        (<!! updates)
        (is (= 11 @last-offset))
        (terminate)))
    
    (testing "get-updates-fn shouldn't be called while go-loop parking on pushing onto channel"
      (let [updates (chan 1)
            telegram-updates-ch (chan 1)
            get-updates-fn (get-updates-from-ch-fn telegram-updates-ch)
            terminate (updates-onto-chan updates {:get-updates-fn get-updates-fn})]
        (is (nil? (poll! updates)) "no updates has been pushed yet")
        (let [update (gen-update)]
          (is (offer! telegram-updates-ch update) "simulate new update from telegram")
          (let [[val ch] (alts!! [updates (timeout 100)])]
            (is (= ch updates) "expect update from updates channel")
            (is (= (:update-id update) (:update-id val)))))
        (is (offer! telegram-updates-ch (gen-update))
            "simulate new update again. Fill `updates` buffer")
        (is (offer! telegram-updates-ch (gen-update))
            "Park while pushing into full `updates` buffer.")
        (is (offer! telegram-updates-ch (gen-update))
            "Fill `telegram-updates-ch` buffer")
        (is (not (offer! telegram-updates-ch (gen-update)))
            "new updates will not be pulled from get-updates unless we don't consume `updates` channel")
        (terminate)))))

