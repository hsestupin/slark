(ns slark.api
  (:use [environ.core :refer [env]])
  (:require [clj-http.client :as http]
            [clojure.data.json :as json]))

(def base-url "https://api.telegram.org/bot")

(defn get-token []
  (env :telegram-bot-token))

(defn get-updates
  "Receive updates from telegram Bot via long-polling"
  [& {:keys [token] :as params}]
  (let [token (or token (get-token))
        bot-url (str base-url token "/getUpdates")
        query-params (merge {:timeout 1
                             :offset  0
                             :limit   100}
                            params)
        resp (http/get bot-url
                       {:accept :json :query-params query-params})]
    (-> resp
        :body
        (json/read-str :key-fn keyword)
        :result)))

