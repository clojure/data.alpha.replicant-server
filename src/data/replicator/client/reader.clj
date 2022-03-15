(ns data.replicator.client.reader
  (:require
    [data.replicator.client.impl.protocols :as p]
    [data.replicator.client.impl.rds :as rds])
  (:import
    [clojure.lang MapEntry Seqable]))

(def ^:dynamic *remote-client* nil)

(defn rid-reader
  "Read '#r/id id' and return a relay"
  [rid]
  (rds/relay rid *remote-client*))

(defn seq-reader
  "Read '#r/seq {:head [h e a d] :rest rid} and return a seq"
  [{:keys [head rest] :as m}]
  (concat head
    (reify Seqable
      (seq [_] (p/relay-seq (rid-reader rest))))))

(defn kv-reader
  "Read '#r/kv [k v] and return a map entry"
  [entry]
  (MapEntry. (nth entry 0) (nth entry 1)))

(defn vector-reader
  "Read '#r/vec {:id rid :count N}' and return a vector"
  [{:keys [id count meta] :as m}]
  (rds/remote-vector (rid-reader id) count meta))

(defn map-reader
  "Read '#r/map {:id rid :count N}' and return a vector"
  [{:keys [id count meta] :as m}]
  (rds/remote-map (rid-reader id) count meta))

(defn set-reader
  "Read '#r/set {:id rid :count N}' and return a vector"
  [{:keys [id count meta] :as m}]
  (rds/remote-set (rid-reader id) count meta))

(defn install-readers
  "Install reader set via the *default-data-reader-fn*"
  []
  (let [prior *default-data-reader-fn*]
    (set! *default-data-reader-fn*
      (fn [tag val]
        (let [rfn (get {'r/id #'rid-reader
                        'r/seq #'seq-reader
                        'r/kv #'kv-reader
                        'r/vec #'vector-reader
                        'r/map #'map-reader
                        'r/set #'set-reader} tag)]
          (cond rfn (rfn val)
                prior (prior tag val)))))))