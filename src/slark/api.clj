(ns slark.api
  (:use [environ.core :refer [env]])
  (:require [clj-http.client :as http]
            [clojure.data.json :as json]))

(def base-url "https://api.telegram.org/bot")

(defn get-token []
  (env :telegram-bot-token))

(defn- build-telegram-api-url
  [{:keys [token] :or {token (get-token)} :as params} url-suffix]
  (str base-url token url-suffix))

(defn- extract-telegram-payload
  [response]
  (-> response
      :body
      (json/read-str :key-fn keyword)))

(defn- get-result
  [payload-only response]
  (if payload-only
    (extract-telegram-payload response)
    response))

(defn- do-get-request
  "Do a HTTP GET request to a telegram bot API with specified url-suffix and query-params"
  [{:keys [payload-only] :as params} query-params url-suffix]
  (let [telegram-api-url (build-telegram-api-url params url-suffix) 
        response (http/get telegram-api-url
                           {:accept :json :query-params query-params})]
    (get-result payload-only response)))

(defn get-updates
  "Receive updates from telegram Bot via long-polling. 
  Supported parameters: :timeout, :offset, :limit.

  For more info look at https://core.telegram.org/bots/api#getupdates

  You might want to get not all http response but telegram API part only - to extract payload part only add ':payload-only true' to arguments"
  [& {:as params}]
  (let [query-params (merge {:timeout 1
                             :offset  0
                             :limit   100}
                            params)]
    (do-get-request params query-params "/getUpdates")))

(defn set-webhook
  "Send setWebhook request to telegram API. For more detailed information look at https://core.telegram.org/bots/api#setwebhook.
  How to generate self-signed certificate - https://core.telegram.org/bots/self-signed

  You might want to get not all http response but telegram API part only - to extract payload part only add ':payload-only true' to arguments"
  [& {:keys [url certificate payload-only] :or {url ""} :as params}]
  (let [telegram-api-url (build-telegram-api-url params "/setWebhook")
        cert-file (clojure.java.io/file certificate)
        partial-data [{:name "url" :content url}]
        multipart-data (if (and cert-file (.exists cert-file))
                         (conj partial-data {:name  "certificate" :content cert-file})
                         partial-data)
        response (http/post telegram-api-url {:accept :json
                                              :multipart multipart-data})]
    (get-result payload-only response)))

(defn get-me
  "A simple method for testing your bot's auth token. Returns basic information about the bot in form of a User object. https://core.telegram.org/bots/api#getme

  You might want to get not all http response but telegram API part only - to extract payload part only add ':payload-only true' to arguments"
  [& {:as params}]
  (do-get-request params {} "/getMe"))

(defn send-message
  "Use this method to send text messages. On success, the sent Message is returned. https://core.telegram.org/bots/api#sendmessage

  You might want to get not all http response but telegram API part only - to extract payload part only add ':payload-only true' to arguments"
  [& {:keys [chat-id text] :as params}]
  (let [query-params {:chat_id chat-id
                      :text text}]
    (do-get-request params query-params "/sendMessage")))


;; For debugging purposes
(comment
  (do
    (def chat-id (Integer/parseInt (env :chat-id)))))
