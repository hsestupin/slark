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

(defn- do-post-request
  "Do a HTTP POST multipart/form-data request to a telegram bot API with specified url-suffix and multipart-data"
  [{:keys [entire-response?] :as params} multipart-data url-suffix]
  (let [telegram-api-url (build-telegram-api-url params url-suffix) 
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
    (do-post-request params multipart-data "/setWebhook")))

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

(defn- merge-multipart
  [partial-data optional-params]
  (reduce (fn [data [k v]]
            (conj data {:name k :content (str v)}))
          partial-data
          optional-params))

(defn send-photo
  "Use this method to send photos. On success, the sent Message is returned. https://core.telegram.org/bots/api#sendphoto

  You might want to get entire http response but not only a telegram payload part - to extract an entire http response add 'entire-response? true' to arguments"
  [& {:keys [chat-id photo] :as params}]
  (let [partial-data [{:name "chat_id" :content (str chat-id)}
                      {:name "photo" :content (as-input-file-or-string photo)}]
        optional-params (to-telegram-format-keys
                         (select-keys params
                                      [:caption
                                       :disable-notification
                                       :reply-to-message-id
                                       :reply-markup]))
        multipart-data (merge-multipart partial-data optional-params)]
    (do-post-request params multipart-data "/sendPhoto")))

(defn send-audio
  "Use this method to send audio files, if you want Telegram clients to display them in the music player. Your audio must be in the .mp3 format. On success, the sent Message is returned.

  You might want to get entire http response but not only a telegram payload part - to extract an entire http response add 'entire-response? true' to arguments"
  [& {:keys [chat-id audio] :as params}]
  (let [partial-data [{:name "chat_id" :content (str chat-id)}
                      {:name "audio" :content (as-input-file-or-string audio)}]
        optional-params (to-telegram-format-keys
                         (select-keys params
                                      [:duration
                                       :performer
                                       :title
                                       :disable-notification
                                       :reply-to-message-id
                                       :reply-markup]))
        multipart-data (merge-multipart partial-data optional-params)]
    (do-post-request params multipart-data "/sendAudio")))


;; For debugging purposes
(comment
  (do
    (def chat-id (Integer/parseInt (env :chat-id)))))

