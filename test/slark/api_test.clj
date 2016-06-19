(ns slark.api-test
  (:require [clojure.test :refer :all]
            [slark.core :refer :all]
            [slark.api :refer :all]
            [environ.core :refer [env]]
            [clojure.java.io :as io]))

(def ^:const bot-id 161840425)
(def ^:const message-id 45)
(def test-options {:token "161840425:AAGbk0nBjhGL1EXXaSjVXUtCvI1R_FGoXoI"
                   :disable-notification true})

(defn is-clojure-test-bot
  [{:keys [id first-name username] :as telegram-response}]
  (is (= id bot-id))
  (is (= first-name "clojuretestbot"))
  (is (= username "clojure_tests_bot")))

(deftest get-me-test
  (testing "/getMe raw function"
    (let [response (get-me (conj test-options [:entire-response? true]))
          telegram-response (extract-telegram-payload response)]
      (is (not (nil?  response)))
      (is (= 200 (:status response)))
      (is (:ok telegram-response))
      (is-clojure-test-bot (:result telegram-response))))

  (testing "/getMe payload-only function"
    (let [telegram-response (get-me test-options)]
      (is (:ok telegram-response))
      (is-clojure-test-bot (:result telegram-response))))

  (testing "get-me throws returns Unauthorized"
    (let [telegram-response (get-me {:token "1234:abcd"})]
      (is (not (:ok telegram-response)))
      (is (= 401 (:error-code telegram-response)))
      (is (= "Unauthorized" (:description telegram-response))))))

(defn- get-chat-id
  []
  (Integer/parseInt (env :chat-id)))

(deftest send-message-test
  (testing "simple /sendMessage"
    (let [message (send-message (get-chat-id) "Hi, it's test message."
                                (conj test-options [:disable-notification false]))]
      (is (:ok message))
      (is-clojure-test-bot (get-in message [:result :from]))
      (is (= (get-chat-id) (get-in message [:result :chat :id])))
      (is (= "Hi, it's test message." (get-in message [:result :text])))))

  (testing "simple /sendMessage silently"
    (let [message (send-message (get-chat-id) "Hi, it's silent test message."
                                test-options)]
      (is (:ok message))
      (is-clojure-test-bot (get-in message [:result :from]))
      (is (= (get-chat-id) (get-in message [:result :chat :id])))
      (is (= "Hi, it's silent test message." (get-in message [:result :text])))))

  (testing "/sendMessage with Markdown parse-mode"
    (let [message (send-message (get-chat-id) "*bold* _italic_ [link](http://google.com)." (conj test-options [:parse-mode "Markdown"]))]
      (is (:ok message))
      (is-clojure-test-bot (get-in message [:result :from]))
      (is (= (get-chat-id) (get-in message [:result :chat :id])))
      (is (= "bold italic link." (get-in message [:result :text])))))

  (testing "/sendMessage with HTML parse-mode"
    (let [message (send-message (get-chat-id) "<b>bold</b> <i>italic</i> <a href=\"http://google.com\">link</a>." (conj test-options [:parse-mode "HTML"]))]
      (is (:ok message))
      (is-clojure-test-bot (get-in message [:result :from]))
      (is (= (get-chat-id) (get-in message [:result :chat :id])))
      (is (= "bold italic link." (get-in message [:result :text]))))))

(defn- remove-webhook []
  (set-webhook test-options))

(deftest set-webhook-test
  (testing "simple test webhook without cert"
    (remove-webhook)
    (let [update (set-webhook (conj test-options [:url "https://google.com"]))]
      (is (:ok update))
      (is (:result update))
      (is (= "Webhook was set" (:description update))))
    (let [update (remove-webhook)]
      (is (:ok update))
      (is (:result update))
      (is (= "Webhook was deleted" (:description update))))))

(deftest forward-message-test
  (testing "simple forward-message"
    (let [message (forward-message (get-chat-id) (get-chat-id) message-id test-options)]
      (is (:ok message))
      (is (= "Message to forward" (get-in message [:result :text]))))))

(deftest send-photo-test
  (testing "simple testing sending photo"
    (let [message (send-photo (get-chat-id) (io/file "resources/cat.jpg") test-options)]
      (is (:ok message))
      (is (vector? (get-in message [:result :photo]))))))

(deftest send-audio-test
  (testing "simple test audio sending"
    (let [message (send-audio (get-chat-id) (io/file "resources/bethoven.mp3")
                              (conj test-options [:duration 100]))]
      (is (:ok message))
      (let [audio (get-in message [:result :audio])]
        (is (= 100 (:duration audio)))
        (is (= "audio/mpeg" (:mime-type audio)))
        (is (= "A-M Classical" (:performer audio)))))))

(deftest send-sticker-test
  (testing "simple test sticker sending"
    (let [message (send-sticker (get-chat-id) "BQADAgADrwADCRdfAYkosIlx1dMuAg"
                                test-options)]
      (is (:ok message))
      (let [sticker (get-in message [:result :sticker])]
        (is (= "BQADAgADrwADCRdfAYkosIlx1dMuAg" (:file-id sticker)))))))

(deftest send-video-test
  (testing "simple test video sending"
    (let [message (send-video (get-chat-id) (io/file "resources/snow.mp4")
                              test-options)]
      (is (:ok message))
      (let [video (get-in message [:result :video])]
        (is (some? video))))))

(deftest send-voice-test
  (testing "simple test voice sending"
    (let [message (send-voice (get-chat-id) (io/file "resources/bethoven.mp3")
                              (conj test-options [:duration 20]))]
      (is (:ok message))
      (let [voice (get-in message [:result :voice])]
        (is (= 20 (:duration voice)))))))

(deftest send-location-test
  (testing "simple test location sending"
    (let [message (send-location (get-chat-id) 12.3 125.23 test-options)]
      (is (:ok message))
      (let [location (get-in message [:result :location])]
        (is (some? location))))))

(deftest send-venue-test
  (testing "simple test venue sending"
    (let [message (send-venue (get-chat-id) 58.23 98.03 "Test venue" "Test street"
                              test-options)]
      (is (:ok message))
      (let [venue (get-in message [:result :venue])]
        (is (= "Test venue" (:title venue)))
        (is (= "Test street" (:address venue)))))))

(deftest send-contact-test
  (testing "simple test contact sending"
    (let [message (send-contact (get-chat-id) "1234567890" "Alex"
                                test-options)]
      (is (:ok message))
      (let [contact (get-in message [:result :contact])]
        (is (= "1234567890" (:phone-number contact)))
        (is (= "Alex" (:first-name contact)))))))

(deftest send-chat-action-test
  (testing "expected error if undefined action was sent"
    (let [message (send-chat-action (get-chat-id) "wrong-action"
                                    test-options)]
      (is (not (:ok message)))
      (is (= 400 (:error-code message)))
      (is (= "Bad Request: wrong parameter action in request" (:description message)))))

  (testing "simple test chat action sending via string"
    (let [message (send-chat-action (get-chat-id) "upload_photo"
                                    test-options)]
      (is (:ok message))))

  (testing "simple test chat action sending via keyword"
    (let [message (send-chat-action (get-chat-id) :record-audio
                                    test-options)]
      (is (:ok message)))))
