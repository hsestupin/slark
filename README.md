# slark

A Clojure library which might help you to create telegram bots in clojure. [Generated docs](http://hsestupin.github.io/slark/)

## Usage

Firstly you have to provide bot token. You can pass token manually:

```clojure
user> (require '[slark.api :as s])
nil
user> (def chat-id 1234)
#'user/chat-id
user> (s/send-message chat-id "hello-world" {:token "1234:ABCD"})
{:ok true, :result {:message-id 19, :from {:id 737373, :first-name "bot-name", :username "some_bot_name"}, :chat {:id 1234, :first-name "Sergey", :last-name "Stupin", :type "private"}, :date 1465858266, :text "hello-world"}}
```

If token is not provided manually then token will be obtained via [Environ](https://github.com/weavejester/environ) library. For example you might create file `.lein-env` in project directory with the following content:

```clojure
{:telegram-bot-token  "your_token"}
```

and then you can send message to chat like this:

```clojure
user> (require '[slark.api :as s])
nil
user> (def chat-id 1234)
#'user/chat-id
user> (s/send-message chat-id "hello-world")
{:ok true, :result {:message-id 19, :from {:id 737373, :first-name "bot-name", :username "some_bot_name"}, :chat {:id 1234, :first-name "Sergey", :last-name "Stupin", :type "private"}, :date 1465858266, :text "hello-world"}}
```

You can find more examples in [tests](https://github.com/hsestupin/slark/blob/master/test/slark/api_test.clj).

## Dependency

```clojure
[org.clojars.hsestupin/slark "0.0.2"]
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
