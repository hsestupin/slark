# slark

	A Clojure library which might help you to create telegram bots in clojure. [Denerated docs](http://hsestupin.github.io/slark/)

## Usage

Firstly you have to provide bot token. You can pass token manually:

```clojure
(def chat-id 1234)
(send-message chat-id "Hello world" {:token "1234:ABCD"})
```

If token is not provided manually then token will be obtained via [Environ](https://github.com/weavejester/environ) library. For example you might create file `.lein-env` in project directory with the following content:

```clojure
{:telegram-bot-token  "your_token"}
```

and then you can send message to user like this:

```clojure
(def chat-id 1234)
(send-message chat-id "Hello world")
```

You can find more examples in [tests](https://github.com/hsestupin/slark/blob/master/test/slark/api_test.clj).

## Dependency

```clojure
[org.clojars.hsestupin/slark "0.0.1"]
```

```xml
<dependency>
  <groupId>org.clojars.hsestupin</groupId>
  <artifactId>slark</artifactId>
  <version>0.0.1</version>
</dependency>
```

## License

Copyright © 2016 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
