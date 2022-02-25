(ns data.remote.server
  (:import
   [java.util Collection]
   [java.util.concurrent ConcurrentMap])
  (:require
   [data.remote.impl.protocols :as p]))

(def ^:dynamic *remotify-length* 25)
(def ^:dynamic *remotify-level* 3)

(defn cache-remote-ref
  "Returns ref uuid."
  [server obj]
  (p/-cache-remote-ref server obj))

(defn has-remotes?
  "Returns true if remotify of obj would include remote object references."
  [obj]
  (p/-has-remotes? obj))
  
;; TODO: add depth check
(defn remotify
  "Cache obj as a remote on server. Returns uuid for the obj."
  [obj server]
  (p/-remotify obj server))

;; defrecords represent remote wire objects and print as "r/... data"
(defrecord Ref [id])
(defrecord RVec [id count])
(defrecord RSet [id count])
(defrecord RMap [id count])
(defrecord RSeq [head rest])
(defrecord RMapEntry [kv])

(defmethod print-method Ref [rref ^java.io.Writer w]
  (.write w (str "#r"))
  (@#'clojure.core/print-map rref @#'clojure.core/pr-on w))

(defmethod print-method RVec [rref ^java.io.Writer w]
  (.write w (str "#r/vec"))
  (let [{:keys [id count]} rref]
    (@#'clojure.core/print-map {:r/id id :count count} @#'clojure.core/pr-on w)))

(defmethod print-method RSet [rref ^java.io.Writer w]
  (.write w (str "#r/set"))
  (let [{:keys [id count]} rref]
    (@#'clojure.core/print-map {:r/id id :count count} @#'clojure.core/pr-on w)))

(defmethod print-method RMap [rref ^java.io.Writer w]
  (.write w (str "#r/map"))
  (let [{:keys [id count]} rref]
    (@#'clojure.core/print-map {:r/id id :count count} @#'clojure.core/pr-on w)))

(defmethod print-method RMapEntry [rref ^java.io.Writer w]
  (.write w (str "#r/kv"))
  (.write w (str (:kv rref))))

(defmethod print-method RSeq [rref ^java.io.Writer w]
  (.write w (str "#r/seq"))
  (let [{:keys [head rest]} rref]
    (@#'clojure.core/print-map {:head head :r/id rest} @#'clojure.core/pr-on w)))

(defn remotify-head
  "Remotify the first *remotify-length* items in the head of coll"
  [server coll]
  (loop [coll coll
         result (transient [])
         n *remotify-length*]
    (if (or (nil? coll) (zero? n))
      (persistent! result)
      (let [[item & more] coll
            d (remotify item server)]
        (recur
         more
         (conj! result d)
         (dec n))))))

(defn remotify-set
  [server coll]
  (if (<= (count coll) *remotify-length*)
    (if (has-remotes? coll)
      (with-meta (into #{} (remotify-head server coll)) {:r/id (cache-remote-ref server coll)})
      coll)
    (map->RSet {:id (cache-remote-ref server coll)
                :count (count coll)})))

(defn remotify-map
  [server coll]
  (if (<= (count coll) *remotify-length*)
    (if (has-remotes? coll)
      (with-meta (apply hash-map (interleave (remotify-head server (keys coll))
                                             (remotify-head server (vals coll))))
        {:r/id (cache-remote-ref server coll)})
      coll)
    (map->RMap {:id (cache-remote-ref server coll)
                :count (count coll)})))

(defn remotify-vector
  [server coll]
  (if (<= (count coll) *remotify-length*)
    (if (has-remotes? coll)
      (with-meta (into [] (remotify-head server coll)) {:r/id (cache-remote-ref server coll)})
      coll)
    (map->RVec {:id (cache-remote-ref server coll)
                :count (count coll)})))

(defn remotify-seq
  [server coll]
  (if (<= (bounded-count *remotify-length* coll) *remotify-length*)
    (if (has-remotes? coll)
      (with-meta (remotify-head server coll) {:r/id (cache-remote-ref server coll)})
      coll)
    (map->RSeq {:head (take *remotify-length* coll)
                :rest (cache-remote-ref server (drop *remotify-length* coll))})))

(extend-protocol p/Server
  java.util.concurrent.ConcurrentMap
  (-cache-remote-ref
    [m obj]
    (let [id (long (System/identityHashCode obj))]
      (.putIfAbsent m id {:obj obj :uuid (java.util.UUID/randomUUID)})
      (:uuid (.get m id)))))

(extend-protocol p/HasRemote
  nil (-has-remotes? [_] false)
  Boolean (-has-remotes? [_] false)
  Object (-has-remotes? [_] true)
  String (-has-remotes? [_] false)
  clojure.lang.Keyword (-has-remotes? [_] false)
  clojure.lang.Symbol (-has-remotes? [_] false)
  Number (-has-remotes? [_] false)
  clojure.lang.IPersistentCollection
  (-has-remotes?
    [coll]
    (binding [*remotify-level* (dec *remotify-level*)]
      (or (zero? *remotify-level*)
          (transduce
           (take *remotify-length*)
           (completing (fn [result item] (if (has-remotes? item)
                                           (reduced true)
                                           false)))
           false
           coll)))))

(extend-protocol p/Remotify
  Object
  (-remotify [obj server] (map->Ref {:id (cache-remote-ref server obj)}))

  clojure.lang.IMapEntry
  (-remotify
    [obj server]
    (let [[k v] obj
          rk (remotify k server)
          rv (remotify v server)]
      (->RMapEntry [rk rv])))

  clojure.lang.Associative
  (-remotify
    [obj server]
    (if (instance? clojure.lang.IPersistentCollection obj)
      (remotify-map server obj)
      (map->Ref {:id (cache-remote-ref server obj)})))

  clojure.lang.PersistentHashSet
  (-remotify [coll server] (remotify-set server coll))
  clojure.lang.PersistentTreeSet
  (-remotify [coll server] (remotify-set server coll))

  clojure.lang.PersistentVector
  (-remotify [coll server] (remotify-vector server coll))

  clojure.lang.ISeq
  (-remotify [coll server] (remotify-seq server coll))

  Boolean (-remotify [x _] x)
  String (-remotify [x _] x)
  clojure.lang.Keyword (-remotify [x _] x)
  clojure.lang.Symbol (-remotify [x _] x)
  Number (-remotify [x _] x)
  nil (-remotify [x _] nil))
