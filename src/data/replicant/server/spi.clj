(ns data.replicant.server.spi
  (:require
   [data.replicant.server.impl.protocols :as cache]
   [data.rds.protocols :as rds])
  (:import
    [java.io Writer]
    [clojure.lang Keyword Symbol ISeq Associative IPersistentCollection MapEntry
                  PersistentHashSet PersistentTreeSet PersistentVector IFn]))

(def ^:dynamic *rds-cache*)
(def ^:dynamic *remote-lengths* [2500])
(def ^:private ^:dynamic *depth-length* 2500)
(def ^:dynamic *remote-depth* 50)

(defn object->rid
  [server obj]
  (cache/-object->rid server obj))

(defn rid->object
  [server rid]
  (cache/-rid->object server rid))

(defn has-remotes?
  "Returns true if remotify of obj would include remote object references."
  [obj]
  (binding [*remote-depth* (and *remote-depth* (dec *remote-depth*))
            *depth-length* (or (first *remote-lengths*) *depth-length*)
            *remote-lengths* (next *remote-lengths*)]
    (or
     (rds/-has-remotes? obj)
     (rds/-has-remotes? (meta obj)))))

(defn remotify
  "Cache obj as a remote on server. Returns uuid for the obj."
  [obj server]
  (binding [*depth-length* (or (first *remote-lengths*) *depth-length*)
            *remote-lengths* (next *remote-lengths*)]
    (let [robj (rds/-remotify obj server)]
      (if-let [m (meta robj)]
        (with-meta robj (rds/-remotify m server))
        robj))))

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
  "Remotify the first *depth-length* items in the head of coll"
  [server coll]
  (binding [*remote-depth* (and *remote-depth* (dec *remote-depth*))]
;;    (println "remotify-head " *remote-lengths* *depth-length*)
    (into [] (comp (take *depth-length*)
                   (map (fn [elem] (remotify elem server))))
          coll)))

(defn remotify-set
  [server coll]
  (if (<= (count coll) *depth-length*)
    (if (has-remotes? coll)
      (if (and *remote-depth* (zero? *remote-depth*))
        (map->Ref {:id (object->rid server coll)})
        (into #{} (remotify-head server coll)))
      coll)
    (map->RSet (cond-> {:id (object->rid server coll)
                        :count (count coll)}
                 (meta coll) (assoc :meta (remotify (meta coll) server))))))

(defn remotify-map
  [server coll]
;;  (println "remotify-map " *depth-length* " ?= " (count coll) " r? " (has-remotes? coll))
  (if (<= (count coll) *depth-length*)
    (if (has-remotes? coll)
      (if (and *remote-depth* (zero? *remote-depth*))
        (map->Ref {:id (object->rid server coll)})
        (apply hash-map (interleave (remotify-head server (keys coll))
                                    (remotify-head server (vals coll)))))
      coll)
    (map->RMap (cond-> {:id (object->rid server coll)
                        :count (count coll)}
                 (meta coll) (assoc :meta (remotify (meta coll) server))))))

(defn remotify-vector
  [server coll]
;;  (println "remotify-vector " *depth-length* " ?= " (count coll) " r? " (has-remotes? coll))
  (if (<= (count coll) *depth-length*)
    (if (has-remotes? coll)
      (if (and *remote-depth* (zero? *remote-depth*))
        (map->Ref {:id (object->rid server coll)})
        (into [] (remotify-head server coll)))
      coll)
    (map->RVec (cond-> {:id    (object->rid server coll)
                        :count (count coll)}
                 (meta coll) (assoc :meta (remotify (meta coll) server))))))

(defn remotify-seq
  [server coll]
  (if (<= (bounded-count (inc *depth-length*) coll) *depth-length*)
    (if (has-remotes? coll)
      (seq (remotify-head server coll))
      coll)
    (map->RSeq (cond-> {:head (remotify-head server coll)
                        :rest (object->rid server (drop *depth-length* coll))}
                 (meta coll) (assoc :meta (meta coll))))))

(defn remotify-fn
  [server f]
  (map->RFn {:id (object->rid server f)}))

(extend-protocol rds/HasRemote
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
;;    (println "has-remotes? " *remote-lengths* *depth-length*)
    (or (and *remote-depth* (neg? *remote-depth*))
        (> (bounded-count (inc *depth-length*) coll) *depth-length*)
        (transduce
         (take *depth-length*)
         (completing (fn [result item]
                       (if (has-remotes? item)
                         (reduced true)
                         false)))
         false
         coll))))

(extend-protocol rds/Remotify
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

(comment
  (require 'data.replicant.server.impl.cache)
  (def C
    (let [cache-builder (doto (com.github.benmanes.caffeine.cache.Caffeine/newBuilder)
                          (.softValues))]
      (data.replicant.server.impl.cache/create-remote-cache cache-builder)))

  (do (println :===================================)
      (binding [*remote-lengths* [3 1]
                *remote-depth* 1]
        (remotify [[1 2 3] [4 5 6]] C)))

  (do (println :===================================)
      (binding [*remote-lengths* [3 1]]
        (has-remotes? [[1 2 3] [4 5 6]])))
  
  (do (println :===================================)
      (binding [*remote-lengths* [1]]
        (remotify {:a {:b {:c 3}} :d 4} C)))

  (do (println :===================================)
      (binding [*remote-lengths* [2]]
        (remotify {:a {:b {:c 3}}} C)))
  
  (do (println :===================================)
      (map #(binding [*remote-lengths* [3 1]]
              (remotify % C))
           [#{1 2 #{3 4 #{5}}}
            [1 2 [3 4 [5]]]
            {:a {:b {:c 3}}}
            [1 2 [3 4]]
            [1 2 [3 4] [5 6] [7 8]]
            [1 2 [3 4] {5 6} #{7 8}]
            [1 2 [(java.util.Date.)]]]))

  (do (println :===================================)
      (map #(binding [*remote-lengths* [2]]
              (has-remotes? %))
           [#{1 2 #{3 4 #{5}}}
            [1 2 [3 4 [5]]]
            {:a {:b {:c 3}}}
            [1 2 [3 4]]
            [1 2 [(java.util.Date.)]]]))
)
