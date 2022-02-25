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
(defrecord Rvec [id count])
(defrecord Rset [id count])
(defrecord Rseq [head rest])

(defmethod print-method Ref [rref ^java.io.Writer w]
  (.write w (str "#r"))
  (@#'clojure.core/print-map rref @#'clojure.core/pr-on w))

(defmethod print-method Rvec [rref ^java.io.Writer w]
  (.write w (str "#r/vec"))
  (let [{:keys [id count]} rref]
    (@#'clojure.core/print-map {:r/id id :count count} @#'clojure.core/pr-on w)))

(defmethod print-method Rset [rref ^java.io.Writer w]
  (.write w (str "#r/set"))
  (let [{:keys [id count]} rref]
    (@#'clojure.core/print-map {:r/id id :count count} @#'clojure.core/pr-on w)))

(defmethod print-method Rseq [rref ^java.io.Writer w]
  (.write w (str "#r/seq"))
  (let [{:keys [head rest]} rref]
    (@#'clojure.core/print-map {:head head :r/id rest} @#'clojure.core/pr-on w)))

(defn remotify-head
  "remotify the first n items in the head of coll.  Returns
  nil if the entire collection fits in n and is unchanged by
  remotify."
  [server coll]
  (loop [coll coll
         result (transient [])
         n *remotify-length*
         datafied? false]
    (if (or (nil? coll) (zero? n))
      (when (or datafied? (seq coll)) (persistent! result))
      (let [[item & more] coll
            d (remotify item server)]
        (recur
         more
         (conj! result d)
         (dec n)
         (or datafied? (not (identical? d item))))))))

(defn remotify-set
  [server coll]
  (if (< (count coll) *remotify-length*)
    (if (has-remotes? coll)
      (with-meta (into #{} (remotify-head server coll)) {:r/id (cache-remote-ref server coll)})
      coll)
    (map->Rset {:id (cache-remote-ref server coll)
                :count (count coll)})))

(defn remotify-vector
  [server coll]
  (if (< (count coll) *remotify-length*)
    (if (has-remotes? coll)
      (with-meta (into [] (remotify-head server coll)) {:r/id (cache-remote-ref server coll)})
      coll)
    (map->Rset {:id (cache-remote-ref server coll)
                :count (count coll)})))

(defn remotify-seq
  [server coll]
  (if (< (count coll) *remotify-length*)
    (if (has-remotes? coll)
      (with-meta (remotify-head server coll) {:r/id (cache-remote-ref server coll)})
      coll)
    (map->Rseq {:head (take *remotify-length* coll)
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
