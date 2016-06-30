(ns slark-test
  (:require [clojure.test :refer :all]
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

(defn new-udpate
  "Creates new test update"
  []
  {:update-id 5546354, :message {:message-id 108, :from {:id 19883246, :first-name "Sergey", :last-name "Stupin"}, :chat {:id 19883246, :first-name "Sergey", :last-name "Stupin", :type "private"}, :date 1467149444, :text "/ лгл г"}})


(deftest handle-updates-test
  (testing ""))
