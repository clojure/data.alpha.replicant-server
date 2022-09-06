(ns data.replicant.server.reader
  (:require
    [data.replicant.server.spi :as spi]))

(defn lid-reader
  "Read '#l/id id' and return the cached object"
  [rid]
  `(let [val#  (data.replicant.server.spi/rid->object spi/*rds-cache* ~rid)
         mval# (if (not (nil? val#))
                 (if (instance? clojure.lang.IObj val#)
                   (with-meta val# {:r/id ~rid})
                   val#)
                 (throw (ex-info (str "Remote data structure not found in cache for rid " ~rid)
                                 {:id ~rid})))]
     mval#))
