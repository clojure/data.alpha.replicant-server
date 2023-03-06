;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.data.alpha.replicant.server.impl.cache
  (:require
    [clojure.data.alpha.replicant.server.impl.protocols :as p])
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
  [^Cache cache]
  p/Cache
  (-put
    [_ k obj]
    (.put cache k obj))
  (-get
    [_ k]
    (.getIfPresent cache k)))

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
