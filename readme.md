# Replicant

`replicant` is a library for remote implementations of the Clojure
data structures and a remote REPL server hosted over prepl.

## Rationale

TODO

## Usage

### Standalone server

To run a standalone replicant socket server from this repo:

```shell
clj -X data.replicant.server.prepl/start-remote-replicant
```

You can pass additional arguments from [start-server](https://clojure.github.io/clojure/clojure.core-api.html#clojure.core.server/start-server) on the command line.

### Adding to existing app

Add the replicant dep to your app:

```clojure
io.github.cognitect-labs/replicant {:git/sha "cef24742a2bcdfb070a3b3bf8106dbe2dcb5a332"}
```

And then call:

```clojure
(require '[data.replicant.server.prepl :as pr])
(pr/start-remote-replicant)
```

Also takes map of options (or kwargs in Clojure 1.11+) ala [start-server](https://clojure.github.io/clojure/clojure.core-api.html#clojure.core.server/start-server), defaults to port 5555.

## Connecting Morse

Check out the Morse repo:

```shell
# checkout
git clone git@github.com:cognitect-labs/rebl.git
cd rebl

# switch to Morse branch
git checkout morse

# start (also takes `:port 5555` etc)
clj -A:dev:jfx -X cognitect.rebl/morse
```

Once you connect, the REPL pane in Morse is a remote client of the server (via a socket) of the server you started above. Expressions you type there are evaluated in the replicant server process. This is just like any remote socket-based repl.

"Large" data structures (via length or depth) are "remotified" - stored in a cache on the server, passed as a remote reference (you may see this get printed in some places in Morse as #l/id #uuid ...), then used inside Morse via remote implementations of the persistent collection interfaces. When more data is needed, a call is made internally over the remote connection to retrieve more data. This is transparent to Morse - it is just using Clojure data.

Tap and out stream on the server also show up in Morse, as usual.

## Copyright and License

Copyright © 2022 Cognitect

Licensed under the Eclipse Public License, Version 2.0
