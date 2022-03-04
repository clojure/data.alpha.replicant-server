(ns data.remote.api
  (:require
    [data.replicator.server.impl.protocols :as p]
    [data.replicator.server.spi :as server])
  (:import
    [data.replicator.server.spi Ref])
  (:refer-clojure :exclude [seq]))

(set! *warn-on-reflection* true)

(def cache (java.util.concurrent.ConcurrentHashMap.))

(extend-protocol p/Server
  java.util.concurrent.ConcurrentMap
  (-cache-remote-ref
    [m obj]
    (let [id (long (System/identityHashCode obj))
          uuid (java.util.UUID. id id)]
      (.putIfAbsent m uuid {:obj obj :uuid uuid})
      (:uuid (.get m uuid)))))

(defprotocol Fetch
  (-fetch [rid cache]))

(extend-protocol Fetch
  Ref
  (-fetch [rid cache]
    (let [ret (.get ^java.util.concurrent.ConcurrentHashMap cache (:id rid))]
      (when ret rid)))

  Object
  (-fetch [rid cache]
    (let [ret (.get ^java.util.concurrent.ConcurrentHashMap cache (:id rid))]
      ret)))

(defn fetch
  ([rid] (-fetch rid cache))
  ([rid depth-controls]
   ))

(defn seq [rid]
  (when-let [ret (fetch rid)]
    (server/remotify (clojure.core/seq (:obj ret)) cache)))

(defn entryAt [rid k]
  (when-let [ret (fetch rid)]
    (server/remotify (-> ret :obj (get k)) cache)))

(comment
  (def oo (Object.))
  
  (def r (server/remotify oo cache))

  (= r
     (fetch r)
     )

  (def big-v (into [] (range 1000)))
  (def s (server/remotify big-v cache))

  (fetch s)
  (:rest (seq s))

  (def big-m
    (apply hash-map
           (interleave
            (concat
             (for [ch (range 97 123)] (-> ch char str keyword))
             (for [ch (range 65 91)] (-> ch char str keyword)))
            (range 0 52))))

  (def m (server/remotify big-m cache))

  (fetch m)
  (entryAt m :A)

)
