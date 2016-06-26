# slark

A Clojure library which might help you to create telegram bots in clojure. [Generated docs](http://hsestupin.github.io/slark/). *Slark** is an alpha quality software and also API may change in future.

## Your first bot 

Writing telegram bots should be as painful as possible. Here is a tiny snippent to give you an idea how easy to write bots with **Slark**:

```clojure
(ns user
  (:require [slark.api :refer :all]
            [slark.core :refer :all]))
			
;; Bot reaction to a command is just a function with update argument.
(defn- echo [update]
  (let [message (get-message update)
        chat-id (get-in message [:chat :id])
        text (:text message)]
  (send-message chat-id (str "Received '" text "'") {:token *your-token*})))
    
;; Define bot handlers-map. So when user will type `/echo something` the function echo 
;; wil be invoked
(def handlers {"echo" echo})
	
;; Start your bot. And that's it. f - is a clojure Future object. 
;; You can interrupt it with `(cancel-future f)`
(def f (start-handle-loop handlers))
```

## Telegram API usage

Firstly you have to provide bot token. You can pass token manually:

```clojure
user> (require '[slark.api :as a])
nil
user> (def chat-id 1234)
#'user/chat-id
user> (a/send-message chat-id "hello-world" {:token "1234:ABCD"})
{:ok true, :result {:message-id 19, :from {:id 737373, :first-name "bot-name", :username "some_bot_name"}, :chat {:id 1234, :first-name "Sergey", :last-name "Stupin", :type "private"}, :date 1465858266, :text "hello-world"}}
```

If token is not provided manually then token will be obtained via [Environ](https://github.com/weavejester/environ) library. For example you might create file `.lein-env` in project directory with the following content:

```clojure
{:telegram-bot-token  "your_token"}
```

and then you can send message to chat like this:

```clojure
user> (require '[slark.api :as a])
nil
user> (def chat-id 1234)
#'user/chat-id
user> (a/send-message chat-id "hello-world")
{:ok true, :result {:message-id 19, :from {:id 737373, :first-name "bot-name", :username "some_bot_name"}, :chat {:id 1234, :first-name "Sergey", :last-name "Stupin", :type "private"}, :date 1465858266, :text "hello-world"}}
```

You can find more examples in [tests](https://github.com/hsestupin/slark/blob/master/test/slark/api_test.clj).

## Dependency

```clojure
[org.clojars.hsestupin/slark "0.0.3"]
```

```xml
<dependency>
  <groupId>org.clojars.hsestupin</groupId>
  <artifactId>slark</artifactId>
  <version>0.0.2</version>
</dependency>
```

## License

Copyright © 2016 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
