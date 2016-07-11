(ns slark
  (:require [clojure.string :as str]
            [environ.core :refer :all]
            [slark.telegram :refer :all]
            [taoensso.timbre :as timbre
             :refer (log  trace  debug  info  warn  error  fatal  report
                          logf tracef debugf infof warnf errorf fatalf reportf
                          spy get-env log-env)]
            [clojure.core.async :as async :refer
             (close! put! poll! go go-loop chan <! <!! >! >!!
                     pub sub alt! alts! alt!! alts!!)]))

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

(defn- get-updates-async
  "Async execute get-udpates function because it might utilize blocking IO."
  [get-updates-fn offset]
  (let [result-ch (chan 0)]
    (go (>! result-ch (get-updates-fn offset)))
    result-ch))

(defn updates-onto-chan
  "Puts telegram updates obtained via `:get-updates-fn` into the supplied channel with `>!`. Also returns a function which will terminate go-loop when called. 

  Supplied `:get-updates-fn` is a 1 argument function which is called with current update offset. By default it just delegates getting updates to `(get-updates {:offset offset})`. `:get-updates-fn` will not be called unless go-loop is parking trying to push update to channel with `>!` 
  
  Also there are additional optional arguments: `:initial-offset`  - first offset to begin getting updates with. 

  By default the supplied channel will not be closed after telegram error or termination request, but it can be determined by the `:close?` parameter."
  [ch & [{:keys [initial-offset close? get-updates-fn]
          :or {initial-offset 0
               close? false
               get-updates-fn (fn [offset]
                                (get-updates {:offset offset}))}
          :as opts}]]
  (let [terminate-ch (chan)
        close-ch-fn (fn [] (when close?
                             (close! ch)))]
    (go-loop [offset initial-offset]
      (debug "Trying to get-udpates with offset" offset)
      (let [get-updates-ch (get-updates-async get-updates-fn offset)]
        (alt!
          terminate-ch (warn "Termination request") (close-ch-fn)
          get-updates-ch
          ([{:keys [ok result] :as response}]
           (alt!
             [[ch response]] (if ok
                               (recur (reduce max offset
                                              (mapv (comp inc :update-id) result)))
                               (do
                                 (warn "Telegram error" response)
                                 (close-ch-fn)))
             terminate-ch (warn "Termination request") (close-ch-fn))))))
    (fn terminate! []
      (put! terminate-ch :terminate))))

(defn any-command?
  "Creates predicate function which returns true if update 
represents any of supplied commands."
  [& [commands]]
  (fn [update]
    (let [message (get-message update)]
      (and (bot-command? message)
           (some (partial = (get-command message)) commands)))))

(defn conj-command
  "If update represents a command then return an update conjoined with 
`[:command command]`"
  [update]
  (let [message (get-message update)]
    (if-let [command (get-command message)]
      (conj update [:command command])
      update)))


(defn command-handling
  "Creates transducer which handles supplied `command` updates. Second argument is a function which takes an Update map. "
  [command handler]
  (fn [rf]
    (fn
      ([] (rf))
      ([result] (rf result))
      ([result update]
       (let [message (get-message update)]
         (when ((any-command? command) update) 
           (handler update))
         (rf result update))))))

(comment
  (do
    (defn- echo
      [update]
      (let [message (get-message update)
            chat-id (get-in message [:chat :id])
            text (:text message)]
        (send-message chat-id (str "received '" text "'"))))

    (def to-pub (chan 1 (comp
                         (filter (comp not-empty :result))
                         (map #(do (println %) (:result %)))
                         cat
                         (map conj-command))))
    (def p (pub to-pub :command))
    (def terminate (updates-onto-chan to-pub))

    (def echo-channel (chan 10))

    (sub p "echo" echo-channel)
    

    (go-loop [update (<! echo-channel)]
      (when update
        (echo update)
        (info "Update" (:update-id update) "processed")
        (recur (<! echo-channel))))))
