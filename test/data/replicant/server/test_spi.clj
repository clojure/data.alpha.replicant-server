;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

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
