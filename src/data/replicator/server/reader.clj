(ns data.replicator.server.reader
  (:require
    [data.replicator.server.spi :as spi]))

(def ^:dynamic *server* nil)

(defn lid-reader
  "Read '#l/id id' and return the cached object"
  [rid]
  (let [val  (spi/rid->object *server* rid)
        mval (if (instance? clojure.lang.IObj val)
               (with-meta val {:r/id rid})
               val)]
    (if (seq? mval)
      `(quote ~mval)
      mval)))

(defn install-readers
  "Install reader set via the *default-data-reader-fn*"
  []
  (let [prior *default-data-reader-fn*]
    (set! *default-data-reader-fn*
      (fn [tag val]
        (let [rfn (get {'l/id #'lid-reader} tag)]
          (cond rfn (rfn val)
                prior (prior tag val)))))))

