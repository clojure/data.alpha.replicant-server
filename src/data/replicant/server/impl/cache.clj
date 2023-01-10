(ns data.replicant.server.impl.cache
  (:require
    [data.replicant.server.impl.protocols :as p])
  (:import
    [com.github.benmanes.caffeine.cache Caffeine Cache RemovalListener]
    [java.util UUID]
    [java.util.function Function]
    [java.util.concurrent ConcurrentMap ConcurrentHashMap]))

(set! *warn-on-reflection* true)

(defn ju-function
  "Wrap Clojure function as j.u.Function."
  ^Function [f]
  (reify Function
    (apply [_ x] (f x))))

(defn- gc-removed-value-listener
  "Return a cache removal listener that calls f on the removed value."
  ^RemovalListener [f]
  (reify RemovalListener
    (onRemoval [_this _k v _cause] (f v))))

(def ^ConcurrentMap identity->rid (ConcurrentHashMap.))

;; create with create-remote-cache
(deftype RemoteCache
  [^Cache rid->obj]
  p/Cache
  (-object->rid
    [_ k obj]
    (.put rid->obj k obj))
  (-rid->object
    [_ k]
    (.getIfPresent rid->obj k)))

(defn create-remote-cache
  "Given a caffeine cache builder, return a p/Cache that uses uuids for remote ids."
  [^Caffeine builder]
  (let [rid->obj (.. builder
                   (removalListener
                     (-> (fn [obj]
                           (.remove identity->rid (System/identityHashCode obj)))
                       gc-removed-value-listener))
                   build)]
    (RemoteCache. rid->obj)))
