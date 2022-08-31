(ns data.replicant.client.impl.rds
  (:require
    [data.replicant.client.spi :as spi]
    [data.replicant.client.impl.protocols :as p])
  (:import
    [java.io Writer]
    [java.util Collection Map]
    [clojure.lang IDeref Seqable Associative ILookup Sequential Indexed Counted IFn
                  IMeta IPersistentCollection IPersistentStack IPersistentMap IPersistentSet
                  IPersistentVector ArityException]))

(set! *warn-on-reflection* true)

(deftype Relay [rid remote]
  IDeref
  (deref [this] (spi/remote-fetch remote this))

  p/IRelay
  (relay-seq [this]
    (spi/remote-seq remote this))
  (relay-entry [this k]
    (spi/remote-entry remote this k))
  Object
  (toString [this]
    (spi/remote-string remote this))
  corep/Datafiable
  (datafy [this]
    (spi/remote-datafy remote this))
  corep/Navigable
  (nav [this k v]
    (spi/remote-nav remote this k v)))

(defmethod print-method Relay [^Relay relay ^Writer w]
  (.write w (str "#l/id "))
  (@#'print (.-rid relay)))

(defmethod print-dup Relay [^Relay relay ^Writer w]
  (.write w (str "#l/id "))
  (@#'print (.-rid relay)))

(defn relay
  [rid remote]
  (->Relay rid remote))

(defn remote-seq
  "Read '#r/seq {:head [h e a d] :rest rid} and return a seq"
  [head rest-relay]
  (concat head
    (reify Seqable
      (seq [_] (p/relay-seq rest-relay)))))

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

  IPersistentVector

  Collection

  IPersistentStack
  (peek [this]
    (when (pos? count) (.valAt this (dec count))))

  Indexed
  (nth [this n] (.valAt this n))

  Counted

  IMeta
  (meta [this] metadata)

  IFn
  (invoke [this k]
    (.valAt this k))
  (invoke [this k not-found]
    (if-let [e (.entryAt this k)]
      (val e)
      not-found))
  (applyTo [this args]
    (condp = (count args)
      1 (.invoke this (nth args 0))
      2 (.invoke this (nth args 0) (nth args 1))
      (throw (ArityException. (count args) "RemoteVector"))))

  Iterable
  (iterator [this] (clojure.lang.SeqIterator. (seq this))))

(defn remote-vector
  [relay count metadata]
  (->RemoteVector relay count metadata))

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

  Iterable
  (iterator [this] (clojure.lang.SeqIterator. (seq this)))

  Map
  (size [this] count)

  IMeta
  (meta [this] metadata)

  IFn
  (invoke [this k] (.valAt this k))
  (invoke [this k not-found]
          (if-let [e (.entryAt this k)]
            (val e)
            not-found))
  (applyTo [this args]
           (condp = (count args)
             1 (.invoke this (nth args 0))
             2 (.invoke this (nth args 0) (nth args 1))
             (throw (ArityException. (count args) "RemoteMap")))))

(defn remote-map
  [relay count metadata]
  (->RemoteMap relay count metadata))

(deftype RemoteSet
  [relay count metadata]
  clojure.lang.Seqable
  (seq [this] (p/relay-seq relay))

  IPersistentCollection
  (count [this] count)
  (empty [this] #{})

  Collection
  (size [this] count)

  Counted

  IPersistentSet
  (contains [this k] (boolean (p/relay-entry relay k)))
  (get [this k] (val (p/relay-entry relay k)))

  IMeta
  (meta [this] metadata))

  ;;IFn
  ;;Iterable
  

(defn remote-set
  [relay count metadata]
  (->RemoteSet relay count metadata))
