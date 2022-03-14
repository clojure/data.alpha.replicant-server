(ns data.replicator.client.impl.rds
  (:require
    [data.replicator.client.spi :as spi]
    [data.replicator.client.impl.protocols :as p])
  (:import
    [java.io Writer]
    [clojure.lang IDeref Seqable Associative ILookup Sequential Indexed Counted
                  IMeta IPersistentCollection IPersistentStack IPersistentMap IPersistentSet]))

(set! *warn-on-reflection* true)

(deftype Relay [rid remote]
  IDeref
  (deref [this] (spi/remote-fetch remote this))

  p/IRelay
  (relay-seq [this]
    (spi/remote-seq remote this))
  (relay-entry [this k]
    (spi/remote-entry remote this k)))

(defmethod print-method Relay [^Relay relay ^Writer w]
  (.write w (str "#l/id "))
  (@#'print (.-rid relay)))

(defn relay
  [rid remote]
  (->Relay rid remote))

;(defn remote-seq
;  [head rest-relay]
;  (concat head (reify Seqable
;                 (seq [_]
;                   (p/relay-seq rest-relay)))))

(deftype RemoteVector
  [relay count metadata]
  Associative
  (containsKey [this k] (boolean (.entryAt this k)))
  (entryAt [this k] (p/relay-entry relay k))

  Seqable
  (seq [this] (p/relay-seq relay))

  IPersistentCollection
  (count [this] count)
  (empty [this] [])

  ILookup
  (valAt [this k] (val (.entryAt this k)))

  Sequential

  IPersistentStack
  (peek [this]
    (when (pos? count) (.valAt this (dec count))))

  Indexed
  (nth [this n] (.valAt this n))

  Counted

  IMeta
  (meta [this] metadata)

  ;;IFn
  ;;Iterable
  )

;(defmethod print-method RemoteVector [^RemoteVector v ^Writer w]
;  (.write w (str "#l/id "))
;  (@#'print (.-rid relay)))

(defn remote-vector
  [relay count metadata]
  (->RemoteVector relay count (assoc metadata :id relay)))

(deftype RemoteMap
  [relay count metadata]
  Associative
  (containsKey [this k] (boolean (.entryAt this k)))
  (entryAt [this k] (p/relay-entry relay k))

  Seqable
  (seq [this] (p/relay-seq relay))

  IPersistentCollection
  (count [this] count)
  (empty [this] {})

  ILookup
  (valAt [this k] (val (.entryAt this k)))

  Counted

  IPersistentMap

  IMeta
  (meta [this] metadata)

  ;;IFn
  ;;Iterable
  )

(defn remote-map
  [relay count metadata]
  (->RemoteMap relay count (assoc metadata :id relay)))

(deftype RemoteSet
  [relay count metadata]
  clojure.lang.Seqable
  (seq [this] (p/relay-seq relay))

  IPersistentCollection
  (count [this] count)
  (empty [this] #{})

  Counted

  IPersistentSet
  (contains [this k] (boolean (p/relay-entry relay k)))
  (get [this k] (val (p/relay-entry relay k)))

  IMeta
  (meta [this] metadata)

  ;;IFn
  ;;Iterable
  )

(defn remote-set
  [relay count metadata]
  (->RemoteSet relay count (assoc metadata :id relay)))