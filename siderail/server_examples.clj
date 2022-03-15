(require :reload
         '[data.replicator.server.impl.cache :as cache]
         '[data.replicator.server.spi :as server])

(set! *print-length* (* server/*remotify-length* 2))

(def objects
  [1
   :a
   "hello"
   nil
   false
   (range 10)
   (range 100)
   (range 10000)
   (into [] (range 10))
   (into [] (range 1000))
   {:a 1 :b 2}
   (first {:a 1 :b 2})
   (zipmap (range 100)
           (repeat 42))
   {:a 1 :b (Object.)}
   [:a :b (Object.) :d :e]
   (list :a :b (Object.) :d :e)
   [[[[1]]]]]
  )

(map server/has-remotes? objects)

(def cache-builder (-> (com.google.common.cache.CacheBuilder/newBuilder)
                       (.maximumSize 100000)))
(def cache (cache/create-remote-cache cache-builder))

(doseq [obj objects]
  (prn obj)
  (print "=> " )
  (prn (server/remotify obj cache))
  (prn))




