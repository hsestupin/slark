(ns slark.core-test
  (:require [clojure.test :refer :all]
            [slark.core :refer :all]))

(deftest bot-command?-test
  (testing "test bot-message?"
    (let [message {:message-id 23,
                   :from {:id 1234, :first-name "Sergey", :last-name "Stupin"}
                   :chat {:id 1234, :first-name "Sergey", :last-name "Stupin", :type "private"}
                   :date 1466715331
                   :text "/kuku me"
                   :entities [{:type "bot_command", :offset 0, :length 5}]}]
      (is (bot-command? message)))))

(deftest get-command-test
  (testing "test bot-message?"
    (let [message {:message-id 23,
                   :from {:id 1234, :first-name "Sergey", :last-name "Stupin"}
                   :chat {:id 1234, :first-name "Sergey", :last-name "Stupin", :type "private"}
                   :date 1466715331
                   :text "/kuku me"
                   :entities [{:type "bot_command", :offset 0, :length 5}]}]
      (is (= "kuku" (get-command message))))))

