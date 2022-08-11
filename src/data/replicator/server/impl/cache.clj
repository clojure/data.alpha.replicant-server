(ns data.replicator.server.impl.cache
  (:require
    [data.replicator.server.impl.protocols :as p])
  (:import
    [com.github.benmanes.caffeine.cache Caffeine Cache RemovalListener]
    [java.util UUID]
    [java.util.function Function]
    [java.util.concurrent ConcurrentMap ConcurrentHashMap]))

(set! *warn-on-reflection* true)

(defn- ju-function
  "Wrap Clojure function as j.u.Function."
  ^Function [f]
  (reify Function
    (apply [_ x] (f x))))

(defn- gc-removed-value-listener
  "Return a cache removal listener that calls f on the removed value."
  ^RemovalListener [f]
  (reify RemovalListener
    (onRemoval [_this _k v _cause] (f v))))

;; create with create-remote-cache
(deftype RemoteCache
  [^ConcurrentMap identity->rid
   ^Cache rid->obj]
  p/Cache
  (-object->rid
    [_ obj]
    (.computeIfAbsent
      identity->rid
      (System/identityHashCode obj)
      (-> (fn [_] (let [rid (UUID/randomUUID)]
                    (.put rid->obj rid obj)
                    rid))
        ju-function)))
  (-rid->object
    [_ k]
    (.getIfPresent rid->obj k)))

(defn create-remote-cache
  "Given a caffeine cache builder, return a p/Cache that uses uuids for remote ids."
  [^Caffeine builder]
  (let [identity->rid (ConcurrentHashMap.)
        rid->obj (.. builder
                   (removalListener
                     (-> (fn [obj]
                           (.remove identity->rid (System/identityHashCode obj)))
                       gc-removed-value-listener))
                   build)]
    (RemoteCache. identity->rid rid->obj)))