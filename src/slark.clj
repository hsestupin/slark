(ns slark
  (:require [clojure.string :as str]
            [environ.core :refer :all]
            [slark.telegram :refer :all]
            [taoensso.timbre :as timbre
             :refer (log  trace  debug  info  warn  error  fatal  report
                          logf tracef debugf infof warnf errorf fatalf reportf
                          spy get-env log-env)]
            [clojure.core.async :as async :refer (go go-loop chan <! <!! >! >!! onto-chan)]))

(defn bot-command?
  "True if this update represents a bot command. Otherwise false"
  [{:keys [entities]}]
  (not-empty (filterv  #(= (:type %) "bot_command") entities)))

(defn get-command
  "Returns corresponding handler to message if it's a bot like message which looks like `/hi ...`. Otherwise returns nil."
  [{:keys [text] :as message}]
  (when (bot-command? message)
    (first (.split (.substring text 1) " "))))

(defn get-message
  [update]
  (or (:message update) (:edited-message update)))

(defn handle-update [handlers update state]
  (let [message (get-message update)
        command (get-command message)
        handler (handlers command)]
    (if handler
      (do
        (debug "Update" (:update-id update) "will be handled by" command)
        (handler update state))
      state)))

(defn handle-updates
  "Sequentially calls all handlers if one is defined updating state and returning
new state and max update-id as a result"
  [handlers updates current-state]
  (letfn [(apply-looking-for-max-id
            [[max-id state] update]
            [(max max-id (:update-id update))
             (handle-update handlers update state)])]
    (reduce apply-looking-for-max-id [0 current-state] updates)))

(defn stateless-handler
  "Creates new function which leaves current bot state unchanged and calls `f` with an update as argument. `f` should accept just 1 argument - telegram update."
  [f]
  (fn [update state]
    (f update)
    state))


(defn start-handle-loop
  "Method starts a new thread with a loop process which will handle incoming updates from chats. Returns future.

  1. handlers - map of bot command handlers
  2. optional map with following keys:
  * `:limit`   - limit argument passed to `get-updates` calls. Defaults to 100.
  * `:timeout` - timeout argument passed to `get-updates` calls. Defaults to 1 seconds
  * `:init-state`   - bot initial state. Defaults to empty map."
  [handlers & [{:keys [timeout limit init-state]
                :or {:timeout 1 :limit 100 :init-state {}}}]]
  (let [state-atom (atom init-state)]
    (future (loop [update-id 0 state init-state]
              (let [updates (:result (get-updates {:offset update-id
                                                   :timeout 1}))]
                (when (.isInterrupted (Thread/currentThread))
                  (throw (new InterruptedException)))
                (let [[max-id new-state] (handle-updates handlers updates state)]
                  (recur (inc max-id) new-state)))))))

(defn updates-chan
  "Creates new channel of telegram updates obtained via `get-updates` long-polling mechanism. 
Accept options for `get-updates` - `:limit` and `:timeout`. Also there are additional optional arguments: 

  * :chan-fn         - function which creates channel
  * :initial-offset  - first offset to begin getting updates with"
  [& [{:keys [chan-fn initial-offset]
       :or {chan-fn #(chan 10)
            initial-offset 0
            limit 100
            timeout 1}
       :as opts}]]
  (let [ch (chan-fn)]
    (go-loop [offset initial-offset]
      (let [updates (:result (get-updates (conj (select-keys opts [:timeout :limit])
                                                [:offset offset])))]
        (onto-chan ch updates false)
        (recur (reduce max offset (map :update-id updates)))))
    ch))

(comment
  (do
    (defn- echo
      [update]
      (let [message (get-message update)
            chat-id (get-in message [:chat :id])
            text (:text message)]
        (info "sending message to" chat-id)
        (send-message chat-id (str "received '" text "'"))))
    (def handlers {"start" (stateless-handler echo)})

    (def f (start-handle-loop handlers))))
