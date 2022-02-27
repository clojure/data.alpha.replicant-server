(import [com.google.common.cache CacheBuilder RemovalListener Cache])

(require :reload '[data.remote.server :as server])

(def builder (-> (CacheBuilder/newBuilder)
                 (.maximumSize 3)))
(def cache
  (server/create-remote-cache builder))

(.asMap (.rid->obj cache))
(.identity->rid cache)

(server/object->rid cache [1 2])
