(ns slark.api
  (:use [environ.core :refer [env]])
  (:require [clj-http.client :as http]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [clojure.java.io :as io]))

(def base-url "https://api.telegram.org/bot")

(defn get-token []
  (env :telegram-bot-token))

(defn- to-telegram-format-keys
  "Recursively transforms all map keys to telegram format replacing '-' with '_'"
  [m]
  (let [to-telegram-format (fn [[k v]]
                             [(-> k
                                  name
                                  (str/replace "-" "_"))
                              v])]
    (clojure.walk/postwalk (fn [x]
                             (if (map? x)
                               (into {} (map to-telegram-format x))
                               x))
                           m)))

(defn- build-telegram-api-url
  [{:keys [token] :or {token (get-token)} :as params} url-suffix]
  (str base-url token url-suffix))

(defn extract-telegram-payload
  [response]
  (-> response
      :body
      ;; clojurify response keys. Write chat-id instead of chat_id 
      (json/read-str :key-fn #(keyword (str/replace % "_" "-")))))

(defn- get-result
  [entire-response? response]
  (if entire-response?
    response
    (extract-telegram-payload response)))

(defn- do-get-request
  "Do a HTTP GET request to a telegram bot API with specified url-suffix and query-params"
  [{:keys [entire-response?] :as params} query-params url-suffix]
  (let [telegram-api-url (build-telegram-api-url params url-suffix) 
        response (http/get telegram-api-url
                           {:throw-exceptions? false
                            :accept :json
                            :query-params (to-telegram-format-keys query-params)})]
    (get-result entire-response? response)))

(defn- merge-multipart
  [required-data optional-params]
  (reduce (fn [data [k v]]
            (conj data {:name k :content (str v)}))
          required-data
          optional-params))

(defn- do-post-request
  "Do a HTTP POST multipart/form-data request to a telegram bot API with specified url-suffix and multipart-data"
  [{:keys [entire-response?] :as params} url-suffix required-data optional-keys]
  (let [optional-data (to-telegram-format-keys (select-keys params optional-keys))
        multipart-data (merge-multipart required-data optional-data)
        telegram-api-url (build-telegram-api-url params url-suffix) 
        response (http/post telegram-api-url
                            {:throw-exceptions? false
                             :accept :json
                             :multipart multipart-data})]
    (get-result entire-response? response)))

(defn get-updates
  "Receive updates from telegram Bot via long-polling. 
  Supported parameters: :timeout, :offset, :limit.

  For more info look at https://core.telegram.org/bots/api#getupdates

  You might want to get entire http response but not only a telegram payload part - to extract an entire http response add 'entire-response? true' to arguments"
  [& {:as params}]
  (let [query-params (merge {:timeout 1
                             :offset  0
                             :limit   100}
                            params)]
    (do-get-request params query-params "/getUpdates")))

(defn set-webhook
  "Send setWebhook request to telegram API. For more detailed information look at https://core.telegram.org/bots/api#setwebhook.
  How to generate self-signed certificate - https://core.telegram.org/bots/self-signed

  You might want to get entire http response but not only a telegram payload part - to extract an entire http response add 'entire-response? true' to arguments"
  [& {:keys [url certificate] :or {url ""} :as params}]
  (let [cert-file (clojure.java.io/file certificate)
        partial-data [{:name "url" :content url}]
        multipart-data (if (and cert-file (.exists cert-file))
                         (conj partial-data {:name  "certificate" :content cert-file})
                         partial-data)]
    (do-post-request params "/setWebhook" multipart-data [])))

(defn get-me
  "A simple method for testing your bot's auth token. Returns basic information about the bot in form of a User object. https://core.telegram.org/bots/api#getme

  You might want to get entire http response but not only a telegram payload part - to extract an entire http response add 'entire-response? true' to arguments"
  [& {:as params}]
  (do-get-request params {} "/getMe"))

(defn send-message
  "Use this method to send text messages. On success, the sent Message is returned. https://core.telegram.org/bots/api#sendmessage

  You might want to get entire http response but not only a telegram payload part - to extract an entire http response add 'entire-response? true' to arguments"
  [& {:as params}]
  (let [query-params (select-keys params [:chat-id
                                          :text
                                          :parse-mode
                                          :disable-web-page-preview
                                          :disable-notification
                                          :reply-to-message-id
                                          :reply-markup])]
    (do-get-request params query-params "/sendMessage")))

(defn forward-message
  "Use this method to forward messages of any kind. On success, the sent Message is returned. https://core.telegram.org/bots/api#forwardmessage

  You might want to get entire http response but not only a telegram payload part - to extract an entire http response add 'entire-response? true' to arguments"
  [& {:as params}]
  (let [query-params (select-keys params [:chat-id
                                          :from-chat-id
                                          :disable-notification
                                          :message-id])]
    (do-get-request params query-params "/forwardMessage")))

(defn as-input-file-or-string
  "Tries to cast argument to InputFile. Otherwise returns String"
  [file-or-string]
  (let [file (io/file file-or-string)]
    (if (and file (.exists file))
      file
      file-or-string)))

(defn send-photo
  "Use this method to send photos. On success, the sent Message is returned. https://core.telegram.org/bots/api#sendphoto

  You might want to get entire http response but not only a telegram payload part - to extract an entire http response add 'entire-response? true' to arguments"
  [& {:keys [chat-id photo] :as params}]
  {:pre [(some? chat-id) (some? photo)]}
  (let [required-data [{:name "chat_id" :content (str chat-id)}
                       {:name "photo" :content (as-input-file-or-string photo)}]]
    (do-post-request params "/sendPhoto" required-data [:caption
                                                        :disable-notification
                                                        :reply-to-message-id
                                                        :reply-markup])))

(defn send-audio
  "Use this method to send audio files, if you want Telegram clients to display them in the music player. Your audio must be in the .mp3 format. On success, the sent Message is returned. https://core.telegram.org/bots/api#sendaudio

  You might want to get entire http response but not only a telegram payload part - to extract an entire http response add 'entire-response? true' to arguments"
  [& {:keys [chat-id audio] :as params}]
  {:pre [(some? chat-id) (some? audio)]}
  (let [required-data [{:name "chat_id" :content (str chat-id)}
                       {:name "audio" :content (as-input-file-or-string audio)}]]
    (do-post-request params "/sendAudio" required-data [:duration
                                                        :performer
                                                        :title
                                                        :disable-notification
                                                        :reply-to-message-id
                                                        :reply-markup])))

(defn send-document
  "Use this method to send general files. On success, the sent Message is returned. https://core.telegram.org/bots/api#senddocument

  You might want to get entire http response but not only a telegram payload part - to extract an entire http response add 'entire-response? true' to arguments"
  [& {:keys [chat-id document] :as params}]
  {:pre [(some? chat-id) (some? document)]}
  (let [required-data [{:name "chat_id" :content (str chat-id)}
                       {:name "document" :content (as-input-file-or-string document)}]]
    (do-post-request params "/sendDocument" required-data [:caption
                                                           :disable-notification
                                                           :reply-to-message-id
                                                           :reply-markup])))

(defn send-sticker
  "Use this method to send .webp stickers. On success, the sent Message is returned. https://core.telegram.org/bots/api#sendsticker

  You might want to get entire http response but not only a telegram payload part - to extract an entire http response add 'entire-response? true' to arguments"
  [& {:keys [chat-id sticker] :as params}]
  {:pre [(some? chat-id) (some? sticker)]}
  (let [required-data [{:name "chat_id" :content (str chat-id)}
                       {:name "sticker" :content (as-input-file-or-string sticker)}]]
    (do-post-request params "/sendSticker" required-data [:disable-notification
                                                          :reply-to-message-id
                                                          :reply-markup])))


(defn send-video
  "Use this method to send video files, Telegram clients support mp4 videos (other formats may be sent as Document). On success, the sent Message is returned. https://core.telegram.org/bots/api#sendvideo

  You might want to get entire http response but not only a telegram payload part - to extract an entire http response add 'entire-response? true' to arguments"
  [& {:keys [chat-id video] :as params}]
  {:pre [(some? chat-id) (some? video)]}
  (let [required-data [{:name "chat_id" :content (str chat-id)}
                       {:name "video" :content (as-input-file-or-string video)}]]
    (do-post-request params "/sendVideo" required-data [:duration
                                                        :width
                                                        :height
                                                        :caption
                                                        :disable-notification
                                                        :reply-to-message-id
                                                        :reply-markup])))


(defn send-voice
  "Use this method to send audio files, if you want Telegram clients to display the file as a playable voice message. For this to work, your audio must be in an .ogg file encoded with OPUS (other formats may be sent as Audio or Document). On success, the sent Message is returned. https://core.telegram.org/bots/api#sendvoice

  You might want to get entire http response but not only a telegram payload part - to extract an entire http response add 'entire-response? true' to arguments"
  [& {:keys [chat-id voice] :as params}]
  {:pre [(some? chat-id) (some? voice)]}
  (let [required-data [{:name "chat_id" :content (str chat-id)}
                       {:name "voice" :content (as-input-file-or-string voice)}]]
    (do-post-request params "/sendVoice" required-data [:duration
                                                        :disable-notification
                                                        :reply-to-message-id
                                                        :reply-markup])))

;; For debugging purposes
(comment
  (do
    (def chat-id (Integer/parseInt (env :chat-id)))))

