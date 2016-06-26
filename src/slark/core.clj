(ns slark.core
  (:require [clojure.string :as str]
            [environ.core :refer :all]
            [slark.api :refer :all]
            [taoensso.timbre :as timbre
             :refer (log  trace  debug  info  warn  error  fatal  report
                          logf tracef debugf infof warnf errorf fatalf reportf
                          spy get-env log-env)]))

(defn bot-command?
  "True if this update represents a bot command. Otherwise false"
  [{:keys [entities]}]
  (filterv  #(= (:type %)) "bot_command"))

(defn get-command
  "Returns corresponding handler to message if it's a bot like message which looks like `/hi ...`. Otherwise returns nil."
  [{:keys [text] :as message}]
  (when (and (bot-command? message)
             (str/starts-with? text "/"))
    (first (.split (.substring text 1) " "))))

(defn get-message
  [update]
  (or (:message update) (:edited-message update)))

(defn handle-update [handlers update]
  (let [message (get-message update)]
    (when-let [command (get-command message)]
      (when-let [handler (handlers command)]
        (debug "Update" (:update-id update) "will be handled by" command)
        (handler update)))))

(defn start-handle-loop
  "Method starts a new thread with a loop process which will handle incoming updates from chats. Returns future.

  1. handlers - map of bot command handlers
  2. optional map with following keys:
  * `:limit` - limit argument passed to `get-updates` calls. Defaults to 100.
  * `:timeout` - timeout argument passed to `get-updates` calls. Defaults to 1 seconds"
  [handlers & [{:keys [timeout limit]
                :or {:timout 1 :limit 100}}]]
  (future (loop [update-id 0]
            (let [updates (:result (get-updates {:offset update-id
                                                 :timeout 1}))]
              (if (empty? updates)
                (recur update-id)
                (let [updates-ids (mapv (fn [update]
                                          (debug "Handle update:\n" update)
                                          (handle-update handlers update)
                                          (:update-id update))
                                        updates)]
                  (recur (inc (apply max updates-ids)))))))))

(comment
  (do
    (defn- echo
      [update]
      (let [message (get-message update)
            chat-id (get-in message [:chat :id])
            text (:text message)]
        (info "sending message to" chat-id)
        (send-message chat-id (str "received '" text "'"))))
    (def handlers {"start" echo})

    (def f (start-handle-loop handlers))))

