# slark

A Clojure library which might help you to create telegram bots in clojure. [Generated docs](http://hsestupin.github.io/slark/). **Slark** is an alpha quality software and also API may change in future.

## Your first bot 

Writing telegram bots should be as painful as possible. 
Here is a tiny snippent to give you an idea how easy to write bots with **Slark** and **core.async**:

```clojure
(ns user
  (:require [slark :refer :all]
            [slark.telegram :refer :all]))

;; Here we define our command handler which accepts an Update as argument. 
;; Notice it's just a simple plain function
(defn- echo
  [update]
  (let [message (get-message update)
        chat-id (get-in message [:chat :id])
        text (:text message)]
    (send-message chat-id (str "received '" text "'"))))

;; Create **core.async** channel which will take telegram updates. 
;; Originally without any transducer being applied this channel will transmit update batches like:
(comment
{:ok true,
 :result
 [{:update-id 5546450,
   :message
   {:message-id 207,
    :from {:id 1234, :first-name "Sergey,", :last-name "Stupin"},
    :chat
    {:id 1234,
     :first-name "Sergey,",
     :last-name "Stupin,",
     :type "private"},
    :date 1467668230,
    :text "/echo hi,",
    :entities [{:type "bot_command,", :offset 0, :length 5}]}}]}
    )
    
(def c (chan 1 (comp 
				;; ignore empty update results
                (filter (comp not-empty :result))
				;; just print for fun
                (map #(do (println %) (:result %)))
				;; push updates one by one to channel instead of an array
				;; (look at docs https://core.telegram.org/bots/api#getupdates)
                cat
				;; command-handling function is part of the Slark API. 
				;;It creates fully functionl clojure transducer
                (command-handling "echo" echo))))
				
;; Notice how we just applied clojure transducers to our channel. 
;; Here is an excellent introduction to transducers http://elbenshira.com/blog/understanding-transducers/ 

;; updates-onto-chan - it's a very thin layer on top of Telegram native API.
;; To stop processing telegram updates - just call `(terminate)`.
(def terminate (updates-onto-chan c))

;; to make sure that channel's buffer's limit will not be exceeded 
;; we have to take values from channel 
(go-loop [update (<! c)]
  (when update
    (do (println "Update" (:update-id update) "processed")
        (recur (<! c)))))
```
That's it! Now you can type **/echo hello world** in your chat with bot and receive echo answer.

## Telegram API usage

Firstly you have to provide bot token. You can pass token manually:

```clojure
user> (require '[slark.telegram :as t])
nil
user> (def chat-id 1234)
#'user/chat-id
user> (t/send-message chat-id "hello-world" {:token "1234:ABCD"})
{:ok true, :result {:message-id 19, :from {:id 737373, :first-name "bot-name", :username "some_bot_name"}, :chat {:id 1234, :first-name "Sergey", :last-name "Stupin", :type "private"}, :date 1465858266, :text "hello-world"}}
```

If token is not provided manually then token will be obtained via [Environ](https://github.com/weavejester/environ) library. For example you might create file `.lein-env` in project directory with the following content:

```clojure
{:telegram-bot-token  "your_token"}
```

and then you can send message to chat like this:

```clojure
user> (require '[slark.telegram :as t])
nil
user> (def chat-id 1234)
#'user/chat-id
user> (t/send-message chat-id "hello-world")
{:ok true, :result {:message-id 19, :from {:id 737373, :first-name "bot-name", :username "some_bot_name"}, :chat {:id 1234, :first-name "Sergey", :last-name "Stupin", :type "private"}, :date 1465858266, :text "hello-world"}}
```

You can find more examples in [tests](https://github.com/hsestupin/slark/blob/master/test/slark/telegram_test.clj).

## Dependency

```clojure
[org.clojars.hsestupin/slark "0.0.5"]
```

```xml
<dependency>
  <groupId>org.clojars.hsestupin</groupId>
  <artifactId>slark</artifactId>
  <version>0.0.5</version>
</dependency>
```

## License

Copyright © 2016 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
