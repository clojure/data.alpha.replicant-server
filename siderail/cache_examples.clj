(import [com.github.benmanes.caffeine.cache Caffeine])

(require :reload
  '[data.replicator.server.impl.cache :as cache]
  '[data.replicator.server.spi :as server])

(def builder (-> (Caffeine/newBuilder)
                 (.maximumSize 3)))
(def cache
  (cache/create-remote-cache builder))

(.asMap (.rid->obj cache))
(.identity->rid cache)

(server/object->rid cache [1 2])
