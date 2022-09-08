(ns data.replicant.server.test-spi
  (:require
   [clojure.test :refer :all]
   [data.replicant.server.impl.cache :as server.cache]
   [data.replicant.server.spi :as server.spi]))

(deftest remote-and-print 
  (let [cache-builder (doto (com.github.benmanes.caffeine.cache.Caffeine/newBuilder) (.softValues))
        cache (server.cache/create-remote-cache cache-builder)]
    (binding [server.spi/*rds-cache* (constantly cache)
              *print-meta* true]
      (are [expected-str val]
           (= expected-str (-> val (server.spi/remotify cache) pr-str))
        "[1 2]" [1 2]
        "" (vec (range 500))
        ))))

(comment
  (remote-and-print)

  )