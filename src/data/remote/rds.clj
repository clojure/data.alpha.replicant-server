(ns data.remote.rds
  (:require
    [clojure.edn :as edn])
  (:import [clojure.lang Seqable IDeref IMeta Associative]))

(defprotocol IRemote
  (fetch-remote
    [this uuid]
    [this uuid depth-opts])
  (seq-remote
    [this uuid]
    [this uuid depth-opts])
  (entry-remote
    [this uuid k] ;; not-found?
    [this uuid k depth-opts]))

(defn relay
  [uuid rctx]
  (reify
    IDeref
    (deref [_] (fetch-remote rctx uuid))

    Seqable
    (seq [_] (seq-remote rctx uuid))

    Associative
    (entryAt [_ k] (entry-remote rctx uuid k))))

;;; TEST HARNESS

(declare id-reader)
(declare seq-reader)
(declare kv-reader)
(declare vec-reader)
(declare map-reader)
(declare set-reader)

(defn read-fancy
  [s]
  (edn/read-string
    {:readers {'r/id id-reader
               'r/seq seq-reader
               'r/kv kv-reader
               'r/vec vec-reader
               'r/map map-reader
               'r/set set-reader}}
    s))

;; dummy state: fetch use uuid, entry use [uuid k], seq use {uuid :seq}
(def state
  {1 #(read-fancy "#r/seq {:head [1 2 3] :rest #r/id 2}")
   {2 :seq} #(read-fancy "#r/seq {:head [4 5 6] :rest #r/id 3}")
   {3 :seq} #(list 7 8 9)

   ;; a vector with seq support
   4 #(read-fancy "#r/vec {:id 4 :count 3}") ;; [100 200 300]
   [4 0] #(read-fancy "#r/kv [0 100]")
   [4 1] #(read-fancy "#r/kv [1 200]")
   [4 2] #(read-fancy "#r/kv [2 300]")
   {4 :seq} #(read-fancy "#r/seq {:head [100 200] :rest #r/id 5}")
   5 #(list 300)
   {5 :seq} #(list 300)

   ;; a map with seq support
   10 #(read-fancy "#r/map {:id 10 :count 2}") ;; {:a 1 :b 2}
   [10 :a] #(read-fancy "#r/kv [:a 1]")
   [10 :b] #(read-fancy "#r/kv [:b 2]")
   {10 :seq} #(list (read-fancy "#r/kv [:a 1]") (read-fancy "#r/kv [:b 2]"))

   ;; a set with seq support
   20 #(read-fancy "#r/set {:id 20 :count 2}") ;; #{:a :b}
   [20 :a] #(read-fancy "#r/kv [:a :a]")
   [20 :b] #(read-fancy "#r/kv [:b :b]")
   {20 :seq} #(list :a :b)
   })

(defn id-reader
  [uuid]
  (relay uuid
    (reify IRemote
      (fetch-remote [_ uuid]
        ;(println "! fetch-remote" uuid)
        ((get state uuid)))
      (fetch-remote [_ uuid depth-opts] ((get state uuid)))

      (seq-remote [_ uuid]
        ;(println "! seq-remote" uuid)
        ((get state {uuid :seq})))
      (seq-remote [_ uuid depth-opts] ((get state {uuid :seq})))

      (entry-remote [_ uuid k]
        ;;(println "! entry-remote" uuid k)
        ((get state [uuid k])))
      (entry-remote [_ uuid k depth-opts] ((get state [uuid k]))))))

(defn seq-reader
  [{:keys [head rest]}]
  (concat head rest))

(defn kv-reader
  [[k v]]
  (clojure.lang.MapEntry. k v))

(declare remote-vector)

(defn vec-reader
  [{:keys [id count]}]
  (remote-vector (id-reader id) count {:r/id id}))

(comment
  (let [val (read-fancy "#r/id 1")]
    (println (map inc @val)))
  )

;;; VECTOR

(defn remote-vector
  [relay count m]
  (reify
    clojure.lang.Associative
    (containsKey [this k] (boolean (.entryAt ^Associative relay k)))
    (entryAt [this k] (.entryAt ^Associative relay k))

    clojure.lang.Seqable
    (seq [this] (seq relay))

    clojure.lang.IPersistentCollection
    (count [this] count)
    (empty [this] [])

    clojure.lang.ILookup
    (valAt [this k] (val (.entryAt ^Associative relay k)))

    clojure.lang.Sequential

    clojure.lang.IPersistentStack
    (peek [this]
      (when (pos? count)
        (val (.entryAt ^Associative relay (dec count)))))

    clojure.lang.Indexed
    (nth [this n] (val (.entryAt ^Associative relay n)))

    clojure.lang.Counted

    ;clojure.lang.IMeta
    ;(meta [this] m)

    ;;clojure.lang.IFn
    ;;java.util.Iterable
    ))

(comment
  (def v (read-fancy "#r/id 4"))
  @v
  (contains? @v 0)
  (into [] @v)
  (seq @v)
  (peek @v)
  (get @v 0)
  (count @v)
  )

;;; MAP

(defn remote-map
  [relay count m]
  (reify
    clojure.lang.Associative
    (containsKey [this k] (boolean (.entryAt ^Associative relay k)))
    (entryAt [this k] (.entryAt ^Associative relay k))

    clojure.lang.Seqable
    (seq [this] (seq relay))

    clojure.lang.IPersistentCollection
    (count [this] count)
    (empty [this] [])

    clojure.lang.ILookup
    (valAt [this k] (val (.entryAt ^Associative relay k)))

    clojure.lang.Indexed
    (nth [this n] (val (.entryAt ^Associative relay n)))

    clojure.lang.Counted

    clojure.lang.IPersistentMap

    ;clojure.lang.IMeta
    ;(meta [this] m)

    ;;clojure.lang.IFn
    ;;java.util.Iterable
    ))

(defn map-reader
  [{:keys [id count]}]
  (remote-map (id-reader id) count {:r/id id}))

(comment
  (def m (read-fancy "#r/id 10"))
  @m
  (seq @m)
  (keys @m)
  (vals @m)
  (contains? @m :a)
  )

;;; SET

(defn remote-set
  [relay count m]
  (reify
    clojure.lang.Seqable
    (seq [this] (seq relay))

    clojure.lang.IPersistentCollection
    (count [this] count)
    (empty [this] [])

    clojure.lang.Counted

    clojure.lang.IPersistentSet
    (contains [this k] (boolean (.entryAt ^Associative relay k)))
    (get [this k] (val (.entryAt ^Associative relay k)))

    ;clojure.lang.IMeta
    ;(meta [this] m)

    ;;clojure.lang.IFn
    ;;java.util.Iterable
    ))

(defn set-reader
  [{:keys [id count]}]
  (remote-set (id-reader id) count {:r/id id}))

(comment
  (def s (read-fancy "#r/id 20"))
  @s
  (seq @s)
  (contains? @s :a)
  )

