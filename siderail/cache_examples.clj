(import [com.google.common.cache CacheBuilder RemovalListener Cache])

(require :reload
  '[data.replicator.server.impl.cache :as cache]
  '[data.replicator.server.spi :as server])

(def builder (-> (CacheBuilder/newBuilder)
                 (.maximumSize 3)))
(def cache
  (cache/create-remote-cache builder))

(.asMap (.rid->obj cache))
(.identity->rid cache)

(server/object->rid cache [1 2])
