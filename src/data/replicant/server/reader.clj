(ns data.replicant.server.reader
  (:require
    [data.replicant.server.spi :as spi]))

(defn lid-reader
  "Read '#l/id id' and return the cached object"
  [rid]
  `(let [val#  (data.replicant.server.spi/rid->object spi/*rds-cache* ~rid)
         mval# (if (instance? clojure.lang.IObj val#)
                (with-meta val# {:r/id ~rid})
                val#)]
     mval#))

