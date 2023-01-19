# replicant-server

`replicant-server` is a library for remote implementations of the Clojure data structures and a remote REPL server hosted over prepl.

## Rationale


## Usage

### Adding to existing app

Add the replicant dep to your app:

```clojure
io.github.cognitect-labs/replicant {:git/sha "TODO"}
```

And then call:

```clojure
(require '[clojure.data.alpha.replicant.server.prepl :as pr])
(pr/start-remote-replicant)
```

TODO: better ns
TODO: better server start function

Also takes map of options (or kwargs in Clojure 1.11+) a la [start-server](https://clojure.github.io/clojure/clojure.core-api.html#clojure.core.server/start-server), defaults to port 5555.

## Connecting to Replicant

"Large" data structures (via length or depth) are "remotified" - stored in a cache on the server, passed as a remote reference. When more data is needed, a call is made internally over the remote connection to retrieve more data.

## Contributing 

[Contributing to Clojure projects](https://clojure.org/community/contributing) requires a signed Contributor Agreement. Pull requests and GitHub issues are not accepted; please use the [core.async JIRA project](TODO) to report problems or enhancements.

## License

Copyright © 2017-2022 Rich Hickey and contributors

Distributed under the Eclipse Public License, the same as Clojure.

## Changelog

TODO
