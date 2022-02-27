(ns data.remote.server
  (:import
   [com.google.common.cache CacheBuilder RemovalListener Cache]
   [java.util Collection]
   [java.util.concurrent ConcurrentMap])
  (:require
   [data.remote.impl.protocols :as p]))

(def ^:dynamic *remotify-length* 25)
(def ^:dynamic *remotify-level* 3)

(defn- ju-function
  "Wrap Clojure function as j.u.Function."
  [f]
  (reify java.util.function.Function
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
  (let [identity->rid (java.util.concurrent.ConcurrentHashMap.)
        rid->obj (.. builder
                     (removalListener
                      (-> (fn [obj]
                            (.remove identity->rid (System/identityHashCode obj)))
                          gc-removed-value-listener))
                     build)]
    (RemoteCache. identity->rid rid->obj)))

(defn object->rid
  [server obj]
  (p/-object->rid server obj))

(defn rid->object
  [server obj]
  (p/-rid->object server obj))

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
      (with-meta (into #{} (remotify-head server coll)) {:r/id (object->rid server coll)})
      coll)
    (map->RSet {:id (object->rid server coll)
                :count (count coll)})))

(defn remotify-map
  [server coll]
  (if (<= (count coll) *remotify-length*)
    (if (has-remotes? coll)
      (with-meta (apply hash-map (interleave (remotify-head server (keys coll))
                                             (remotify-head server (vals coll))))
        {:r/id (object->rid server coll)})
      coll)
    (map->RMap {:id (object->rid server coll)
                :count (count coll)})))

(defn remotify-vector
  [server coll]
  (if (<= (count coll) *remotify-length*)
    (if (has-remotes? coll)
      (with-meta (into [] (remotify-head server coll)) {:r/id (object->rid server coll)})
      coll)
    (map->RVec {:id (object->rid server coll)
                :count (count coll)})))

(defn remotify-seq
  [server coll]
  (if (<= (bounded-count *remotify-length* coll) *remotify-length*)
    (if (has-remotes? coll)
      (with-meta (remotify-head server coll) {:r/id (object->rid server coll)})
      coll)
    (map->RSeq {:head (take *remotify-length* coll)
                :rest (object->rid server (drop *remotify-length* coll))})))

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
  (-remotify [obj server] (map->Ref {:id (object->rid server obj)}))

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
      (map->Ref {:id (object->rid server obj)})))

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
