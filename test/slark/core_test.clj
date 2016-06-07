(ns slark.core-test
  (:require [clojure.test :refer :all]
            [slark.core :refer :all]
            [slark.api :refer :all]
            [environ.core :refer [env]]))

(defn is-clojure-test-bot
  [{:keys [id first-name username] :as telegram-response}]
  (is (= id 161840425))
  (is (= first-name "clojuretestbot"))
  (is (= username "clojure_tests_bot")))

(deftest get-me-test
  (testing "/getMe raw function"
    (let [response (get-me :entire-response? true)
          telegram-response (extract-telegram-payload response)]
      (is (not (nil?  response)))
      (is (= 200 (:status response)))
      (is (:ok telegram-response))
      (is-clojure-test-bot (:result telegram-response))))

  (testing "/getMe payload-only function"
    (let [telegram-response (get-me)]
      (is (:ok telegram-response))
      (is-clojure-test-bot (:result telegram-response))))

  (testing "get-me throws returns Unauthorized"
    (let [telegram-response (get-me :token "1234:abcd")]
      (is (not (:ok telegram-response)))
      (is (= 401 (:error-code telegram-response)))
      (is (= "Unauthorized" (:description telegram-response))))))

(defn- get-chat-id
  []
  (Integer/parseInt (env :chat-id)))

(deftest send-message-test
  (testing "simple /sendMessage"
    (let [message (send-message :chat-id (get-chat-id)
                                :text "Hi, it's test message.")]
      (is (:ok message))
      (is-clojure-test-bot (get-in message [:result :from]))
      (is (= (get-chat-id) (get-in message [:result :chat :id])))
      (is (= "Hi, it's test message." (get-in message [:result :text])))))

  (testing "simple /sendMessage silently"
    (let [message (send-message :chat-id (get-chat-id)
                                :text "Hi, it's silent test message."
                                :disable-notification true)]
      (is (:ok message))
      (is-clojure-test-bot (get-in message [:result :from]))
      (is (= (get-chat-id) (get-in message [:result :chat :id])))
      (is (= "Hi, it's silent test message." (get-in message [:result :text])))))

  (testing "/sendMessage with Markdown parse-mode"
    (let [message (send-message :chat-id (get-chat-id)
                                :text "*bold* _italic_ [link](http://google.com)."
                                :disable-notification true
                                :parse-mode "Markdown")]
      (is (:ok message))
      (is-clojure-test-bot (get-in message [:result :from]))
      (is (= (get-chat-id) (get-in message [:result :chat :id])))
      (is (= "bold italic link." (get-in message [:result :text])))))

  (testing "/sendMessage with HTML parse-mode"
    (let [message (send-message :chat-id (get-chat-id)
                                :text "<b>bold</b> <i>italic</i> <a href=\"http://google.com\">link</a>."
                                :disable-notification true
                                :parse-mode "HTML")]
      (is (:ok message))
      (is-clojure-test-bot (get-in message [:result :from]))
      (is (= (get-chat-id) (get-in message [:result :chat :id])))
      (is (= "bold italic link." (get-in message [:result :text]))))))
