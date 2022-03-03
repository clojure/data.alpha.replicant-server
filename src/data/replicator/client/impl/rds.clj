(ns data.replicator.client.impl.rds
  (:require
    [data.replicator.client.spi :as spi]
    [data.replicator.client.impl.protocols :as p])
  (:import
    [clojure.lang IDeref Seqable Associative]))

(set! *warn-on-reflection* true)

(defn relay
  [rid remote]
  (reify
    IDeref
    (deref [_] (spi/remote-fetch remote rid))

    p/IRelay
    (relay-seq [_]
      (spi/remote-seq remote rid))
    (relay-entry [_ k]
      (spi/remote-entry remote rid k))))

(defn remote-seq
  [head rest-relay]
  (concat head (reify Seqable
                 (seq [_]
                   (p/relay-seq rest-relay)))))

(defn remote-vector
  [relay count metadata]
  (with-meta
    (reify
      clojure.lang.Associative
      (containsKey [this k] (boolean (.entryAt this k)))
      (entryAt [this k] (p/relay-entry relay k))

      clojure.lang.Seqable
      (seq [this] (p/relay-seq relay))

      clojure.lang.IPersistentCollection
      (count [this] count)
      (empty [this] [])

      clojure.lang.ILookup
      (valAt [this k] (val (.entryAt this k)))

      clojure.lang.Sequential

      clojure.lang.IPersistentStack
      (peek [this]
        (when (pos? count) (.valAt this (dec count))))

      clojure.lang.Indexed
      (nth [this n] (.valAt this n))

      clojure.lang.Counted

      ;clojure.lang.IMeta
      ;(meta [this] m)

      ;;clojure.lang.IFn
      ;;java.util.Iterable
      )
    (assoc metadata :id relay)))

(defn remote-map
  [relay count metadata]
  (with-meta
    (reify
      clojure.lang.Associative
      (containsKey [this k] (boolean (.entryAt this k)))
      (entryAt [this k] (p/relay-entry relay k))

      clojure.lang.Seqable
      (seq [this] (p/relay-seq relay))

      clojure.lang.IPersistentCollection
      (count [this] count)
      (empty [this] {})

      clojure.lang.ILookup
      (valAt [this k] (val (.entryAt this k)))

      clojure.lang.Counted

      clojure.lang.IPersistentMap

      ;clojure.lang.IMeta
      ;(meta [this] m)

      ;;clojure.lang.IFn
      ;;java.util.Iterable
      )
    (assoc metadata :id relay)))

(defn remote-set
  [relay count metadata]
  (with-meta
    (reify
      clojure.lang.Seqable
      (seq [this] (p/relay-seq relay))

      clojure.lang.IPersistentCollection
      (count [this] count)
      (empty [this] #{})

      clojure.lang.Counted

      clojure.lang.IPersistentSet
      (contains [this k] (boolean (p/relay-entry relay k)))
      (get [this k] (val (p/relay-entry relay k)))

      ;clojure.lang.IMeta
      ;(meta [this] m)

      ;;clojure.lang.IFn
      ;;java.util.Iterable
      )
    (assoc metadata :id relay)))