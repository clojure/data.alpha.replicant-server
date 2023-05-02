# replicant-server

A Clojure library providing remote implementations of the Clojure data structures and a remote REPL server hosted over prepl.

This software is considered an alpha release and subject to change.

## Rationale

While the ability to connect to Clojure REPLs over a wire is a powerful lever for programmers, the transport of data over an active connection is problemmatic in numerous ways:

- Transport of large or deep collection is slow
- Transport of lazy sequences forces realization
- Not all objects are able to transport, some examples being:
  - Objects with types unavailble to both sides of the wire
  - Objects holding local resources (e.g. files, sockets, etc.)
  - Functions
- Not all objects are printable, and therefore unable to transport

Replicant works to alleviate the issues outlined above by providing the following capabilities:

- Replicant transports large collections by passing partial data over the wire. On the other side, Replicant constructs Remote Data Structures that act as Clojure data for read-only operations and request more data as needed
- Replicant transports only the head of lazy sequences and on the other side can fetch more data as needed
- Replicant handles objects that cannot transport by constructing remote references that may later serve as handles for evaluation when requested
- Replicant transports function references that allow remote invocation
- Replicant uses a Datafy context to allow extensibility in the way that objects print on the wire

To this end Replicant provides two libraries: [Replicant Server](https://github.com/clojure/data.alpha.replicant-server) (this library) and [Replicant Client](https://github.com/clojure/data.alpha.replicant-client).

## Docs

* [API.md](API.md)

# Release Information

Latest release:

[deps.edn](https://clojure.org/reference/deps_and_cli) dependency information:

The replicant-server library is intended for use as a git dep:

```clojure
io.github.clojure/data.alpha.replicant-server {:git/tag "v2023.05.02.01" :git/sha "947d8a8"}
``` 

## Usage

replicant-server is meant to run in-process. Once added as a dependency, the following will launch an embedded remote PREPL awaiting a [replicant-client](https://github.com/clojure/data.alpha.replicant-client) or socket connection.

```clojure
(require '[clojure.data.alpha.replicant.server.prepl :as replicant])
(replicant/start :host "hostname")
```

The function `start` takes a map of options allowing customized values for `:port` and `:name` parameters. By default the function runs as if the following was passed:

```clojure
(replicant/start {:host "hostname", :name "rds", :port 5555})
```

You can stop a named Replicant server using the `stop-replicant` function, passing the name given it at start time:

```clojure
(replicant/stop "rds")
```

Stopping an active Replicant server will close all clients connected to it and clear its remote data cache.

# Developer Information

* [GitHub project](https://github.com/clojure/data.alpha.replicant-server)
* [How to contribute](https://clojure.org/community/contributing)
* [Bug Tracker](https://clojure.atlassian.net/browse/DRDS)

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
