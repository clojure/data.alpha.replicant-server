(ns data.replicator.server.impl.cache
  (:require
    [data.replicator.server.impl.protocols :as p])
  (:import
    [com.google.common.cache CacheBuilder RemovalListener Cache]
    [java.util.function Function]
    [java.util.concurrent ConcurrentMap ConcurrentHashMap]))

(set! *warn-on-reflection* true)

(defn- ju-function
  "Wrap Clojure function as j.u.Function."
  [f]
  (reify Function
    (apply [_ x] (f x))))

(defn- gc-removed-value-listener
  "Return a guava removal listener that calls f on the removed value."
  [f]
  (reify RemovalListener
    (onRemoval [_ note] (f (.getValue note)))))

;; create with create-remote-cache
(deftype RemoteCache
  [^ConcurrentMap identity->rid
   ^Cache rid->obj]
  p/Server
  (-object->rid
    [_ obj]
    (.computeIfAbsent
      identity->rid
      (System/identityHashCode obj)
      (-> (fn [_] (let [rid (java.util.UUID/randomUUID)]
                    (.put rid->obj rid obj)
                    rid))
        ju-function)))
  (-rid->object
    [_ k]
    (.getIfPresent rid->obj k)))

(defn create-remote-cache
  "Given a guava cache builder, return a p/Server that uses uuids for remote ids."
  [^CacheBuilder builder]
  (let [identity->rid (ConcurrentHashMap.)
        rid->obj (.. builder
                   (removalListener
                     (-> (fn [obj]
                           (.remove identity->rid (System/identityHashCode obj)))
                       gc-removed-value-listener))
                   build)]
    (RemoteCache. identity->rid rid->obj)))