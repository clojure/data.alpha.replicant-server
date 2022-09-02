(ns data.replicant.server.spi
  (:require
    [data.replicant.server.impl.protocols :as p])
  (:import
    [java.io Writer]
    [clojure.lang Keyword Symbol ISeq Associative IPersistentCollection MapEntry
                  PersistentHashSet PersistentTreeSet PersistentVector IFn]))

(def ^:dynamic *rds-cache*)
(def ^:dynamic *remotify-length* 250)
(def ^:dynamic *remotify-level* 5)

(defn object->rid
  [server obj]
  (p/-object->rid server obj))

(defn rid->object
  [server rid]
  (p/-rid->object server rid))

(defn has-remotes?
  "Returns true if remotify of obj would include remote object references."
  [obj]
  (p/-has-remotes? obj))

(defn remotify
  "Cache obj as a remote on server. Returns uuid for the obj."
  [obj server]
  (let [robj (p/-remotify obj server)]
    (if-let [m (meta robj)]
      (with-meta robj (p/-remotify m server))
      robj)))

;; defrecords represent remote wire objects and print as "r/... data"
(defrecord Ref [id])
(defrecord RVec [id count])
(defrecord RSet [id count])
(defrecord RMap [id count])
(defrecord RSeq [head rest])
(defrecord RMapEntry [kv])
(defrecord RFn [id])

(defmethod print-method Ref [rref ^Writer w]
  (.write w (str "#r/id "))
  (let [{:keys [id]} rref]
    (@#'clojure.core/print id)))

(defn- record->map
  "Convert record into bare map for printing"
  [rec]
  (into {} rec))

(defmethod print-method RVec [rref ^Writer w]
  (.write w (str "#r/vec "))
  (@#'clojure.core/print-map (record->map rref) @#'clojure.core/pr-on w))

(defmethod print-method RSet [rref ^Writer w]
  (.write w (str "#r/set "))
  (@#'clojure.core/print-map (record->map rref) @#'clojure.core/pr-on w))

(defmethod print-method RMap [rref ^Writer w]
  (.write w (str "#r/map "))
  (@#'clojure.core/print-map (record->map rref) @#'clojure.core/pr-on w))

(defmethod print-method RMapEntry [rref ^Writer w]
  (.write w (str "#r/kv "))
  (.write w (str (:kv rref))))

(defmethod print-method RSeq [rref ^Writer w]
  (.write w (str "#r/seq "))
  (let [{:keys [head rest]} rref]
    (@#'clojure.core/print-map (record->map rref) @#'clojure.core/pr-on w)))

(defmethod print-method RFn [rref ^Writer w]
  (.write w (str "#r/fn "))
  (let [{:keys [id]} rref]
    (@#'clojure.core/print-map (record->map rref) @#'clojure.core/pr-on w)))

(defn remotify-head
  "Remotify the first *remotify-length* items in the head of coll"
  [server coll]
  (binding [*remotify-level* (and *remotify-level* (dec *remotify-level*))]
    ;;(println "remotify-head")  
    (into [] (comp (take *remotify-length*)
                   (map (fn [elem] (remotify elem server))))
          coll)))

(defn remotify-set
  [server coll]
  (if (<= (count coll) *remotify-length*)
    (if (has-remotes? coll)
      (if (and *remotify-level* (zero? *remotify-level*))
        (map->Ref {:id (object->rid server coll)})
        (into #{} (remotify-head server coll)))
      coll)
    (map->RSet (cond-> {:id (object->rid server coll)
                        :count (count coll)}
                 (meta coll) (assoc :meta (remotify (meta coll) server))))))

(defn remotify-map
  [server coll]
  (if (<= (count coll) *remotify-length*)
    (if (has-remotes? coll)
      (if (and *remotify-level* (zero? *remotify-level*))
        (map->Ref {:id (object->rid server coll)})
        (apply hash-map (interleave (remotify-head server (keys coll))
                                    (remotify-head server (vals coll)))))
      coll)
    (map->RMap (cond-> {:id (object->rid server coll)
                        :count (count coll)}
                 (meta coll) (assoc :meta (remotify (meta coll) server))))))

(defn remotify-vector
  [server coll]
  (if (<= (count coll) *remotify-length*)
    (if (has-remotes? coll)
      (if (and *remotify-level* (zero? *remotify-level*))
        (map->Ref {:id (object->rid server coll)})
        (into [] (remotify-head server coll)))
      coll)
    (map->RVec (cond-> {:id    (object->rid server coll)
                        :count (count coll)}
                 (meta coll) (assoc :meta (remotify (meta coll) server))))))

(defn remotify-seq
  [server coll]
  (if (<= (bounded-count (inc *remotify-length*) coll) *remotify-length*)
    (if (has-remotes? coll)
      (remotify-head server coll)
      coll)
    (map->RSeq (cond-> {:head (remotify-head server coll)
                        :rest (object->rid server (drop *remotify-length* coll))}
                 (meta coll) (assoc :meta (meta coll))))))

(defn remotify-fn
  [server f]
  (map->RFn {:id (object->rid server f)}))

(extend-protocol p/HasRemote
  nil (-has-remotes? [_] false)
  Boolean (-has-remotes? [_] false)
  Object (-has-remotes? [_] true)
  String (-has-remotes? [_] false)
  Keyword (-has-remotes? [_] false)
  Symbol (-has-remotes? [_] false)
  Number (-has-remotes? [_] false)
  IPersistentCollection
  (-has-remotes?
    [coll]
    (binding [*remotify-level* (and *remotify-level* (dec *remotify-level*))]
      (or (and *remotify-level* (neg? *remotify-level*))
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

  MapEntry
  (-remotify
    [obj server]
    (let [[k v] obj
          rk (remotify k server)
          rv (remotify v server)]
      (->RMapEntry [rk rv])))

  Associative
  (-remotify
    [obj server]
    (if (instance? IPersistentCollection obj)
      (remotify-map server obj)
      (map->Ref {:id (object->rid server obj)})))

  PersistentHashSet
  (-remotify [coll server] (remotify-set server coll))
  PersistentTreeSet
  (-remotify [coll server] (remotify-set server coll))

  PersistentVector
  (-remotify [coll server] (remotify-vector server coll))

  ISeq
  (-remotify [coll server] (remotify-seq server coll))

  IFn
  (-remotify [f server] (remotify-fn server f))

  Boolean (-remotify [x _] x)
  String (-remotify [x _] x)
  Keyword (-remotify [x _] x)
  Symbol (-remotify [x _] x)
  Number (-remotify [x _] x)
  nil (-remotify [x _] nil)

  Ref (-remotify [x _] x)
  RVec (-remotify [x _] x)
  RSet (-remotify [x _] x)
  RMap (-remotify [x _] x)
  RSeq (-remotify [x _] x)
  RMapEntry (-remotify [x _] x)
  RFn (-remotify [x _] x))

(defn register
  [server obj]
  (map->Ref {:id (object->rid server obj)}))

(comment
  (map #(binding [*remotify-level* 2]
          (remotify % data.replicant.server.reader/*server*))
       [#{1 2 #{3 4 #{5}}}
        [1 2 [3 4 [5]]]
        {:a {:b {:c 3}}}
        [1 2 [3 4]]
        [1 2 [3 4] [5 6] [7 8]]
        [1 2 [3 4] {5 6} #{7 8}]
        ])
  
  (map #(binding [*remotify-level* 2]
          (has-remotes? %))
       [#{1 2 #{3 4 #{5}}}
        [1 2 [3 4 [5]]]
        {:a {:b {:c 3}}}
        [1 2 [3 4]]
        [1 2 [(java.util.Date.)]]])
)
