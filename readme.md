# replicant-server

A Clojure a library providing remote implementations of the Clojure data structures and a remote REPL server hosted over prepl.

## Rationale

While the ability to connect to Clojure REPLs over a wire is a powerful lever for programmers, the transport of data-structures can bog down an active connection. Replicant works to avoid sending data over the wire until it's requested. "Large" data structures (via length or depth) are "remotified" - stored in a cache on the server, passed as a remote reference. When more data is needed, a call is made internally over the remote connection to retrieve more data.

## Docs

* [API](https://clojure.github.io/replicant-server)
* [Reference](https://clojure.org/reference/replicant)

# Release Information

Latest release:

[deps.edn](https://clojure.org/reference/deps_and_cli) dependency information:

As a git dep:

```clojure
io.github.clojure/replicant-server {:git/tag "vTODO" :git/sha "TODO"}
``` 

## Usage

replicant-server is meant to run in-process. Once added as a dependency, the following will launch an embedded remote PREPL awaiting a [replicant-client](https://github.com/clojure/replicant-client) or socket connection.

```clojure
(require '[clojure.data.alpha.replicant.server.prepl :as rs])
(rs/start-replicant)
```

The function `start-replicant` takes a map of options allowing customized values for `:address`, `:port`, and `:name` parameters.

# Developer Information

[![Tests](https://github.com/clojure/replicant-server/actions/workflows/ci.yml/badge.svg)](https://github.com/clojure/replicant-server/actions/workflows/ci.yml)

* [GitHub project](https://github.com/clojure/replicant-server)
* [How to contribute](https://clojure.org/community/contributing)
* [Bug Tracker](https://clojure.atlassian.net/browse/RSERVER)

# Copyright and License

Copyright © 2023 Rich Hickey and contributors

All rights reserved. The use and
distribution terms for this software are covered by the
[Eclipse Public License 1.0] which can be found in the file
epl-v10.html at the root of this distribution. By using this software
in any fashion, you are agreeing to be bound by the terms of this
license. You must not remove this notice, or any other, from this
software.

[Eclipse Public License 1.0]: http://opensource.org/licenses/eclipse-1.0.php
