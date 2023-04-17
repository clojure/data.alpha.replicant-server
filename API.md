# clojure.data.alpha.replicant.server.prepl 





## `datafy`
``` clojure

(datafy obj)
```

<sub>[source](src/clojure/data/alpha/replicant/server/prepl.clj#L136-L141)</sub>
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

<sub>[source](src/clojure/data/alpha/replicant/server/prepl.clj#L112-L128)</sub>
## `f`
<sub>[source](src/clojure/data/alpha/replicant/server/prepl.clj#L148-L148)</sub>
## `fetch`
``` clojure

(fetch v)
(fetch
 v
 {:keys [rds/lengths rds/level],
  :as depth-opts,
  :or {lengths server.spi/*remote-lengths*, level server.spi/*remote-depth*}})
```

<sub>[source](src/clojure/data/alpha/replicant/server/prepl.clj#L83-L95)</sub>

## `seq`
``` clojure

(seq v)
(seq
 v
 {:keys [rds/lengths rds/level],
  :as depth-opts,
  :or {lengths server.spi/*remote-lengths*, level server.spi/*remote-depth*}})
```

<sub>[source](src/clojure/data/alpha/replicant/server/prepl.clj#L99-L108)</sub>
## `start-replicant`
``` clojure

(start-replicant)
(start-replicant & {:keys [port cache], :or {port 5555}})
```

<sub>[source](src/clojure/data/alpha/replicant/server/prepl.clj#L60-L71)</sub>
## `stop-replicant`
``` clojure

(stop-replicant nom)
```

<sub>[source](src/clojure/data/alpha/replicant/server/prepl.clj#L73-L74)</sub>
## `string`
``` clojure

(string v)
```

<sub>[source](src/clojure/data/alpha/replicant/server/prepl.clj#L132-L134)</sub>

