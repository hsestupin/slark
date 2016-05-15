(ns slark.core
  (:use [environ.core :refer [env]]))

(defn create-handler
  [{:keys [message_id from chat date text] :as message}]
  (println (str (:first_name from) "says" text)))




