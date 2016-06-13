(ns slark.api
  (:use [environ.core :refer [env]])
  (:require [clj-http.client :as http]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [clojure.java.io :as io]))

(def base-url "https://api.telegram.org/bot")
(def default-post-optional-keys #{:disable-notification
                                  :reply-to-message-id
                                  :reply-markup})

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
  [{:keys [token] :or {token (get-token)}} url-suffix]
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
  [{:keys [entire-response?] :as options} query-params url-suffix]
  (let [telegram-api-url (build-telegram-api-url options url-suffix) 
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
  ([options url-suffix required-data]
   (do-post-request options url-suffix required-data default-post-optional-keys))
  ([{:keys [entire-response?] :as options} url-suffix required-data optional-keys]
   (let [optional-data (to-telegram-format-keys (select-keys options optional-keys))
         multipart-data (merge-multipart required-data optional-data)
         telegram-api-url (build-telegram-api-url options url-suffix) 
         response (http/post telegram-api-url
                             {:throw-exceptions? false
                              :accept :json
                              :multipart multipart-data})]
     (get-result entire-response? response))))

(defn get-updates
  "Receive updates from telegram Bot via long-polling. For more info look at https://core.telegram.org/bots/api#getupdates. 

  Supplied options:
  :offset           - Identifier of the first update to be returned
  :limit            - Limits the number of updates to be retrieved. Values between 1—100 are accepted. Defaults to 100
  :timeout          - Timeout in seconds for long polling. Defaults to 0, i.e. usual short polling
  :token            - bot token. Default value is taken via Environ library by key :telegram-bot-token
  :entire-response? - if you want to get entire http response but not only telegram useful payload part. Defaults to false."
  [& [options]]
  (let [query-params (merge {:timeout 1
                             :offset  0
                             :limit   100}
                            options)]
    (do-get-request options query-params "/getUpdates")))

(defn set-webhook
  "Send setWebhook request to telegram API. For more detailed information look at https://core.telegram.org/bots/api#setwebhook. How to generate self-signed certificate - https://core.telegram.org/bots/self-signed 

  Supplied options:
  :url - HTTPS url to send updates to. Use an empty string to remove webhook integration. Defaults to empty string
  :certificate      - certificate file. One of the following types - InputStream, File, a byte-array, or an instance of org.apache.http.entity.mime.content.ContentBody
  :token            - bot token. Default value is taken via Environ library by key :telegram-bot-token
  :entire-response? - if you want to get entire http response but not only telegram useful payload part. Defaults to false."
  [& [{:keys [url certificate] :or {url ""} :as options}]]
  (let [partial-data [{:name "url" :content url}]
        multipart-data (if certificate
                         (conj partial-data {:name  "certificate" :content certificate})
                         partial-data)]
    (do-post-request options "/setWebhook" multipart-data [])))

(defn get-me
  "A simple method for testing your bot's auth token. Returns basic information about the bot in form of a User object. https://core.telegram.org/bots/api#getme

  Supplied options:
  :token            - bot token. Default value is taken via Environ library by key :telegram-bot-token
  :entire-response? - if you want to get entire http response but not only telegram useful payload part. Defaults to false."
  [& [options]]
  (do-get-request options {} "/getMe"))

(defn send-message
  "Use this method to send text messages. On success, the sent Message is returned. https://core.telegram.org/bots/api#sendmessage

  chat-id           - Unique identifier for the target chat or username of the target channel (in the format @channelusername)
  text              - Text of the message to be sent

  Any other telegram args could be passed through options map. Moreover supplied options:  
  :token            - bot token. Default value is taken via Environ library by key :telegram-bot-token
  :entire-response? - if you want to get entire http response but not only telegram useful payload part. Defaults to false."
  [chat-id text & [options]]
  (let [optional-params (select-keys options [:parse-mode
                                              :disable-web-page-preview
                                              :disable-notification
                                              :reply-to-message-id
                                              :reply-markup])
        query-params (merge {:chat-id chat-id :text text} optional-params)]
    (do-get-request options query-params "/sendMessage")))

(defn forward-message
  "Use this method to forward messages of any kind. On success, the sent Message is returned. https://core.telegram.org/bots/api#forwardmessage
  
  chat-id           - Unique identifier for the target chat or username of the target channel (in the format @channelusername)
  from-chat-id      - Unique identifier for the chat where the original message was sent (or channel username in the format @channelusername)
  message-id        - Unique message identifier

  Any other telegram args could be passed through options map. Moreover supplied options:  
  :token            - bot token. Default value is taken via Environ library by key :telegram-bot-token
  :entire-response? - if you want to get entire http response but not only telegram useful payload part. Defaults to false."
  [chat-id from-chat-id message-id & [options]]
  (let [optional-params (select-keys options [:disable-notification])
        query-params (merge {:chat-id chat-id
                             :from-chat-id from-chat-id
                             :message-id message-id} optional-params)]
    (do-get-request options query-params "/forwardMessage")))

(defn send-photo
  "Use this method to send photos. On success, the sent Message is returned. https://core.telegram.org/bots/api#sendphoto

  chat-id           - Unique identifier for the target chat or username of the target channel (in the format @channelusername)
  photo             - Photo to send. One of the following types - InputStream, File, a byte-array, or an instance of org.apache.http.entity.mime.content.ContentBody

  Any other telegram args could be passed through options map. Moreover supplied options:  
  :token            - bot token. Default value is taken via Environ library by key :telegram-bot-token
  :entire-response? - if you want to get entire http response but not only telegram useful payload part. Defaults to false."
  [chat-id photo & [options]]
  {:pre [(some? chat-id) (some? photo)]}
  (let [required-data [{:name "chat_id" :content (str chat-id)}
                       {:name "photo" :content photo}]]
    (do-post-request options "/sendPhoto" required-data
                     (conj default-post-optional-keys :caption))))

(defn send-audio
  "Use this method to send audio files, if you want Telegram clients to display them in the music player. Your audio must be in the .mp3 format. On success, the sent Message is returned. https://core.telegram.org/bots/api#sendaudio

  chat-id           - Unique identifier for the target chat or username of the target channel (in the format @channelusername)
  audio             - Audio file to send. One of the following types - InputStream, File, a byte-array, or an instance of org.apache.http.entity.mime.content.ContentBody  

  Any other telegram args could be passed through options map. Moreover supplied options:  
  :token            - bot token. Default value is taken via Environ library by key :telegram-bot-token
  :entire-response? - if you want to get entire http response but not only telegram useful payload part. Defaults to false."
  [chat-id audio options]
  {:pre [(some? chat-id) (some? audio)]}
  (let [required-data [{:name "chat_id" :content (str chat-id)}
                       {:name "audio" :content audio}]]
    (do-post-request options "/sendAudio" required-data (conj default-post-optional-keys
                                                              :duration
                                                              :performer
                                                              :title))))

(defn send-document
  "Use this method to send general files. On success, the sent Message is returned. https://core.telegram.org/bots/api#senddocument

  chat-id           - Unique identifier for the target chat or username of the target channel (in the format @channelusername)
  documents         - File to send. One of the following types - InputStream, File, a byte-array, or an instance of org.apache.http.entity.mime.content.ContentBody

  Any other telegram args could be passed through options map. Moreover supplied options:  
  :token            - bot token. Default value is taken via Environ library by key :telegram-bot-token
  :entire-response? - if you want to get entire http response but not only telegram useful payload part. Defaults to false."
  [chat-id document & [options]]
  {:pre [(some? chat-id) (some? document)]}
  (let [required-data [{:name "chat_id" :content (str chat-id)}
                       {:name "document" :content document}]]
    (do-post-request options "/sendDocument" required-data
                     (conj default-post-optional-keys :caption))))

(defn send-sticker
  "Use this method to send .webp stickers. On success, the sent Message is returned. https://core.telegram.org/bots/api#sendsticker

  chat-id           - Unique identifier for the target chat or username of the target channel (in the format @channelusername)
  sticker           - Sticker to send. One of the following types - InputStream, File, a byte-array, or an instance of org.apache.http.entity.mime.content.ContentBody

  Any other telegram args could be passed through options map. Moreover supplied options:  
  :token            - bot token. Default value is taken via Environ library by key :telegram-bot-token
  :entire-response? - if you want to get entire http response but not only telegram useful payload part. Defaults to false."
  [chat-id sticker & [options]]
  {:pre [(some? chat-id) (some? sticker)]}
  (let [required-data [{:name "chat_id" :content (str chat-id)}
                       {:name "sticker" :content sticker}]]
    (do-post-request options "/sendSticker" required-data)))


(defn send-video
  "Use this method to send video files, Telegram clients support mp4 videos (other formats may be sent as Document). On success, the sent Message is returned. https://core.telegram.org/bots/api#sendvideo

  chat-id           - Unique identifier for the target chat or username of the target channel (in the format @channelusername)
  video             - Video to send. One of the following types - InputStream, File, a byte-array, or an instance of org.apache.http.entity.mime.content.ContentBody

  Any other telegram args could be passed through options map. Moreover supplied options:  
  :token            - bot token. Default value is taken via Environ library by key :telegram-bot-token
  :entire-response? - if you want to get entire http response but not only telegram useful payload part. Defaults to false."
  [chat-id video & [options]]
  {:pre [(some? chat-id) (some? video)]}
  (let [required-data [{:name "chat_id" :content (str chat-id)}
                       {:name "video" :content video}]]
    (do-post-request options "/sendVideo" required-data (conj default-post-optional-keys
                                                              :duration
                                                              :width
                                                              :height
                                                              :caption))))

(defn send-voice
  "Use this method to send audio files, if you want Telegram clients to display the file as a playable voice message. For this to work, your audio must be in an .ogg file encoded with OPUS (other formats may be sent as Audio or Document). On success, the sent Message is returned. https://core.telegram.org/bots/api#sendvoice

  chat-id           - Unique identifier for the target chat or username of the target channel (in the format @channelusername)
  voice             - Audio file to send. One of the following types - InputStream, File, a byte-array, or an instance of org.apache.http.entity.mime.content.ContentBody

  Any other telegram args could be passed through options map. Moreover supplied options:  
  :token            - bot token. Default value is taken via Environ library by key :telegram-bot-token
  :entire-response? - if you want to get entire http response but not only telegram useful payload part. Defaults to false."
  [chat-id voice & [options]]
  {:pre [(some? chat-id) (some? voice)]}
  (let [required-data [{:name "chat_id" :content (str chat-id)}
                       {:name "voice" :content voice}]]
    (do-post-request options "/sendVoice" required-data (conj default-post-optional-keys
                                                              :duration))))

(defn send-location
  "Use this method to send point on the map. On success, the sent Message is returned. https://core.telegram.org/bots/api#sendlocation

  chat-id           - Unique identifier for the target chat or username of the target channel (in the format @channelusername)
  latitude          - Latitude of location. Float number
  longitude         - Longitude of location. Float number

  Any other telegram args could be passed through options map. Moreover supplied options:  
  :token            - bot token. Default value is taken via Environ library by key :telegram-bot-token
  :entire-response? - if you want to get entire http response but not only telegram useful payload part. Defaults to false."
  [chat-id latitude longitude & [options]]
  {:pre [(some? chat-id) (some? latitude) (some? longitude)]}
  (let [required-data [{:name "chat_id" :content (str chat-id)}
                       {:name "latitude" :content (str latitude)}
                       {:name "longitude" :content (str longitude)}]]
    (do-post-request options "/sendLocation" required-data)))

(defn send-venue
  "Use this method to send information about a venue. On success, the sent Message is returned. https://core.telegram.org/bots/api#sendvenue

  chat-id           - Unique identifier for the target chat or username of the target channel (in the format @channelusername)
  latitude          - Latitude of location. Float number
  longitude         - Longitude of location. Float number
  title             - Name of the venue
  address           - Address of the venue

  Any other telegram args could be passed through options map. Moreover supplied options:  
  :token            - bot token. Default value is taken via Environ library by key :telegram-bot-token
  :entire-response? - if you want to get entire http response but not only telegram useful payload part. Defaults to false."
  [chat-id latitude longitude title address & [options]]
  {:pre [(some? chat-id)
         (some? latitude)
         (some? longitude)
         (some? title)
         (some? address)]}
  (let [required-data [{:name "chat_id" :content (str chat-id)}
                       {:name "latitude" :content (str latitude)}
                       {:name "longitude" :content (str longitude)}
                       {:name "title" :content (str title)}
                       {:name "address" :content (str address)}]]
    (do-post-request options "/sendVenue" required-data (conj default-post-optional-keys
                                                              :foursquare-id))))

;; For debugging purposes
(comment
  (do
    (def chat-id (Integer/parseInt (env :chat-id)))))
