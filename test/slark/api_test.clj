(ns slark.api-test
  (:require [clojure.test :refer :all]
            [slark.core :refer :all]
            [slark.api :refer :all]
            [environ.core :refer [env]]))

(def ^:const test-token "161840425:AAGbk0nBjhGL1EXXaSjVXUtCvI1R_FGoXoI")
(def ^:const bot-id 161840425)
(def ^:const message-id 45)

(defn is-clojure-test-bot
  [{:keys [id first-name username] :as telegram-response}]
  (is (= id bot-id))
  (is (= first-name "clojuretestbot"))
  (is (= username "clojure_tests_bot")))

(deftest get-me-test
  (testing "/getMe raw function"
    (let [response (get-me :token test-token :entire-response? true)
          telegram-response (extract-telegram-payload response)]
      (is (not (nil?  response)))
      (is (= 200 (:status response)))
      (is (:ok telegram-response))
      (is-clojure-test-bot (:result telegram-response))))

  (testing "/getMe payload-only function"
    (let [telegram-response (get-me :token test-token)]
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
    (let [message (send-message :token test-token
                                :chat-id (get-chat-id)
                                :text "Hi, it's test message.")]
      (is (:ok message))
      (is-clojure-test-bot (get-in message [:result :from]))
      (is (= (get-chat-id) (get-in message [:result :chat :id])))
      (is (= "Hi, it's test message." (get-in message [:result :text])))))

  (testing "simple /sendMessage silently"
    (let [message (send-message :token test-token
                                :chat-id (get-chat-id)
                                :text "Hi, it's silent test message."
                                :disable-notification true)]
      (is (:ok message))
      (is-clojure-test-bot (get-in message [:result :from]))
      (is (= (get-chat-id) (get-in message [:result :chat :id])))
      (is (= "Hi, it's silent test message." (get-in message [:result :text])))))

  (testing "/sendMessage with Markdown parse-mode"
    (let [message (send-message :token test-token
                                :chat-id (get-chat-id)
                                :text "*bold* _italic_ [link](http://google.com)."
                                :disable-notification true
                                :parse-mode "Markdown")]
      (is (:ok message))
      (is-clojure-test-bot (get-in message [:result :from]))
      (is (= (get-chat-id) (get-in message [:result :chat :id])))
      (is (= "bold italic link." (get-in message [:result :text])))))

  (testing "/sendMessage with HTML parse-mode"
    (let [message (send-message :token test-token
                                :chat-id (get-chat-id)
                                :text "<b>bold</b> <i>italic</i> <a href=\"http://google.com\">link</a>."
                                :disable-notification true
                                :parse-mode "HTML")]
      (is (:ok message))
      (is-clojure-test-bot (get-in message [:result :from]))
      (is (= (get-chat-id) (get-in message [:result :chat :id])))
      (is (= "bold italic link." (get-in message [:result :text])))))

  (testing "bad /sendMessage request"
    (let [message (send-message :token test-token
                                :chat-id (get-chat-id))]
      (is (not (:ok message)))
      (is (= 400 (:error-code message)))
      (is (= "Bad Request: Message text is empty" (:description message))))))

(defn- remove-webhook []
  (set-webhook :token test-token))

(deftest set-webhook-test
  (testing "simple test webhook without cert"
    (remove-webhook)
    (let [update (set-webhook :token test-token
                              :url "https://google.com")]
      (is (:ok update))
      (is (:result update))
      (is (= "Webhook was set" (:description update))))
    (let [update (remove-webhook)]
      (is (:ok update))
      (is (:result update))
      (is (= "Webhook was deleted" (:description update))))))

(deftest forward-message-test
  (testing "simple forward-message"
    (let [message (forward-message :token test-token
                                   :chat-id (get-chat-id)
                                   :from-chat-id (get-chat-id)
                                   :disable-notification true
                                   :message-id message-id)]
      (is (:ok message))
      (is (= "Message to forward" (get-in message [:result :text]))))))

(deftest send-photo-test
  (testing "simple testing sending photo"
    (let [message (send-photo :token test-token
                              :chat-id (get-chat-id)
                              :disable-notification true
                              :photo "resources/cat.jpg")]
      (is (:ok message))
      (is (vector? (get-in message [:result :photo]))))))

(deftest send-audio-test
  (testing "simple test audio sending"
    (let [message (send-audio :token test-token
                              :chat-id (get-chat-id)
                              :disable-notification true
                              :audio "resources/bethoven.mp3"
                              :duration 100)]
      (is (:ok message))
      (let [audio (get-in message [:result :audio])]
        (is (= 100 (:duration audio)))
        (is (= "audio/mpeg" (:mime-type audio)))
        (is (= "A-M Classical" (:performer audio)))))))

(deftest send-sticker-test
  (testing "simple test sticker sending"
    (let [message (send-sticker :token test-token
                                :chat-id (get-chat-id)
                                :disable-notification true
                                :sticker "BQADAgADrwADCRdfAYkosIlx1dMuAg")]
      (is (:ok message))
      (let [sticker (get-in message [:result :sticker])]
        (is (= "BQADAgADrwADCRdfAYkosIlx1dMuAg" (:file-id sticker)))))))

(deftest send-video-test
  (testing "simple test video sending"
    (let [message (send-video :token test-token
                              :chat-id (get-chat-id)
                              :disable-notification true
                              :video "resources/snow.mp4")]
      (is (:ok message))
      (let [video (get-in message [:result :video])]
        (is (some? video))))))

(deftest send-voice-test
  (testing "simple test voice sending"
    (let [message (send-voice :token test-token
                              :chat-id (get-chat-id)
                              :disable-notification true
                              :voice "resources/bethoven.mp3"
                              :duration 20)]
      (is (:ok message))
      (let [voice (get-in message [:result :voice])]
        (is (= 20 (:duration voice)))))))

(deftest send-location-test
  (testing "simple test location sending"
    (let [message (send-location :token test-token
                                 :chat-id (get-chat-id)
                                 :disable-notification true
                                 :latitude 12.3
                                 :longitude 125.23)]
      (is (:ok message))
      (let [location (get-in message [:result :location])]
        (is (some? location))))))

(deftest send-venue-test
  (testing "simple test venue sending"
    (let [message (send-venue :token test-token
                              :chat-id (get-chat-id)
                              :disable-notification true
                              :latitude 58.23
                              :longitude 98.03
                              :title "Test venue"
                              :address "Test street")]
      (is (:ok message))
      (let [venue (get-in message [:result :venue])]
        (is (= "Test venue" (:title venue)))
        (is (= "Test street" (:address venue)))))))
