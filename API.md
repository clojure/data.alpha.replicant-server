# clojure.data.alpha.replicant.server.prepl 





## `datafy`
``` clojure

(datafy v)
```


Remote API: Called by a replicant client to retrieve a datafy representation for an object v.
<br><sub>[source](src/clojure/data/alpha/replicant/server/prepl.clj#L147-L150)</sub>
## `entry`
``` clojure

(entry m k)
(entry
 m
 k
 {:keys [rds/lengths rds/level],
  :as depth-opts,
  :or {lengths server.spi/*remote-lengths*, level server.spi/*remote-depth*}})
```


Remote API: Called by a replicant client to retrieve a value mapped at key k for a collection m. Takes an
  object v and remotifies it if the :rds/lengths and :rds/level values cause a
  depth options threshold crossings. Expects a bound server.spi/*rds-cache* value.
<br><sub>[source](src/clojure/data/alpha/replicant/server/prepl.clj#L119-L138)</sub>
## `fetch`
``` clojure

(fetch v)
(fetch
 v
 {:keys [rds/lengths rds/level],
  :as depth-opts,
  :or {lengths server.spi/*remote-lengths*, level server.spi/*remote-depth*}})
```


Remote API: Called by a replicant client to retrieve an object from the cache. Takes an
  object v and remotifies it if the :rds/lengths and :rds/level values cause a
  depth options threshold crossings. Expects a bound server.spi/*rds-cache* value.
<br><sub>[source](src/clojure/data/alpha/replicant/server/prepl.clj#L88-L103)</sub>
## `rds-prepl`
``` clojure

(rds-prepl rds-cache)
```


Uses *in* and *out* streams to serve RDS data given an RDS cache.
<br><sub>[source](src/clojure/data/alpha/replicant/server/prepl.clj#L53-L58)</sub>
## `remotify-proc`
``` clojure

(remotify-proc val)
```

<sub>[source](src/clojure/data/alpha/replicant/server/prepl.clj#L31-L34)</sub>
## `seq`
``` clojure

(seq v)
(seq
 v
 {:keys [rds/lengths rds/level],
  :as depth-opts,
  :or {lengths server.spi/*remote-lengths*, level server.spi/*remote-depth*}})
```


Remote API: Called by a replicant client to retrieve a seq for a collection. Takes an
  object v and remotifies it if the :rds/lengths and :rds/level values cause a
  depth options threshold crossings. Expects a bound server.spi/*rds-cache* value.
<br><sub>[source](src/clojure/data/alpha/replicant/server/prepl.clj#L105-L117)</sub>
## `start-replicant`
``` clojure

(start-replicant)
(start-replicant & {:keys [port cache server-name], :or {port 5555, server-name "rds"}})
```


Local API: Starts a named Replicant server in the current process on a connected socket client thread.
  By default this function starts a server named "rds" listening on localhost:5555 and initializes a
  default Replicant cache. Callers may pass an options map with keys :server-name, :port, and :cache to
  override those defaults.
<br><sub>[source](src/clojure/data/alpha/replicant/server/prepl.clj#L60-L75)</sub>
## `stop-replicant`
``` clojure

(stop-replicant server-name)
```


Local API: Stops a named Replicant server in the current process. Stopping an active Replicant server
  closes all clients connected to it and clears its remote data cache.
<br><sub>[source](src/clojure/data/alpha/replicant/server/prepl.clj#L77-L81)</sub>
## `string`
``` clojure

(string v)
```


Remote API: Called by a replicant client to retrieve a string representation for an object v.
<br><sub>[source](src/clojure/data/alpha/replicant/server/prepl.clj#L142-L145)</sub>

