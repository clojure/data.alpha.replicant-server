(ns data.replicator.client.reader
  (:require
    [data.replicator.client.impl.rds :as rds])
  (:import
    [clojure.lang MapEntry]))

(def ^:dynamic *remote-client* nil)

(defn rid-reader
  "Read '#r/id id' and return a relay"
  [rid]
  (rds/relay rid *remote-client*))

(defn seq-reader
  "Read '#r/seq {:head [h e a d] :rest rid} and return a seq"
  [{:keys [head rest] :as m}]
  (rds/remote-seq head rest))

(defn kv-reader
  "Read '#r/kv [k v] and return a map entry"
  [entry]
  (MapEntry. (nth entry 0) (nth entry 1)))

(defn vector-reader
  "Read '#r/vec {:id rid :count N}' and return a vector"
  [{:keys [id count meta] :as m}]
  (rds/remote-vector id count meta))

(defn map-reader
  "Read '#r/map {:id rid :count N}' and return a vector"
  [{:keys [id count meta] :as m}]
  (rds/remote-map id count meta))

(defn set-reader
  "Read '#r/set {:id rid :count N}' and return a vector"
  [{:keys [id count meta] :as m}]
  (rds/remote-set id count meta))

(defn install-readers
  "Install reader set via the *default-data-reader-fn*"
  []
  (set! *default-data-reader-fn*
    (fn [tag val]
      (let [rfn (get {'r/id #'rid-reader
                      'r/seq #'seq-reader
                      'r/kv #'kv-reader
                      'r/vec #'vector-reader
                      'r/map #'map-reader
                      'r/set #'set-reader} tag)]
        (when rfn
          (rfn val))))))

;; TESTING

(comment
  (require '[clojure.edn :as edn] '[data.replicator.client.spi :as spi])

  (defn read-fancy
    [s]
    (edn/read-string
      {:readers {'r/id rid-reader
                 'r/seq seq-reader
                 'r/kv kv-reader
                 'r/vec vector-reader
                 'r/map map-reader
                 'r/set set-reader}}
      s))

  (def state
    {1 #(read-fancy "#r/seq {:head [1 2 3] :rest #r/id 2}")
     {2 :seq} #(read-fancy "#r/seq {:head [4 5 6] :rest #r/id 3}")
     {3 :seq} #(list 7 8 9)

     ;; a vector with seq support
     4 #(read-fancy "#r/vec {:id #r/id 4 :count 3}") ;; [100 200 300]
     [4 0] #(read-fancy "#r/kv [0 100]")
     [4 1] #(read-fancy "#r/kv [1 200]")
     [4 2] #(read-fancy "#r/kv [2 300]")
     {4 :seq} #(read-fancy "#r/seq {:head [100 200] :rest #r/id 5}")
     5 #(list 300)
     {5 :seq} #(list 300)

     ;; a map with seq support
     10 #(read-fancy "#r/map {:id #r/id 10 :count 2}") ;; {:a 1 :b 2}
     [10 :a] #(read-fancy "#r/kv [:a 1]")
     [10 :b] #(read-fancy "#r/kv [:b 2]")
     {10 :seq} #(list (read-fancy "#r/kv [:a 1]") (read-fancy "#r/kv [:b 2]"))

     ;; a set with seq support
     20 #(read-fancy "#r/set {:id #r/id 20 :count 2}") ;; #{:a :b}
     [20 :a] #(read-fancy "#r/kv [:a :a]")
     [20 :b] #(read-fancy "#r/kv [:b :b]")
     {20 :seq} #(list :a :b)
     })

  (alter-var-root #'*remote-client*
    (constantly
      (reify spi/IRemote
        (remote-fetch [_ id] (println "!remote-fetch" id) ((get state id)))
        (remote-seq [_ id] (println "!remote-seq" id) ((get state {id :seq})))
        (remote-entry [_ id k] (println "!remote-entry" id k) ((get state [id k]))))))

  (def rs (read-fancy "#r/id 1"))
  (map inc @rs)

  (def rv (read-fancy "#r/id 4"))
  (get @rv 1)
  (map inc @rv)

  (def rm (read-fancy "#r/id 10"))
  (get @rm :a)
  (:a @rm)

  (def rset (read-fancy "#r/id 20"))
  (contains? @rset :a)
  (seq @rset)
  )