(ns replicant
  (:require
   [data.replicator.server.spi :as server.spi]
   [data.replicator.server.reader :as server.reader]
   [data.replicator.server.impl.cache :as server.cache]
   [clojure.core.server :as server])
  (:refer-clojure :exclude [seq]))

(defn setup-rds []
  (let [;; create server
        cache-builder (-> (com.google.common.cache.CacheBuilder/newBuilder)
                          (.maximumSize 100000))
        cache (server.cache/create-remote-cache cache-builder)]
    ;; install server and readers
    (alter-var-root #'server.reader/*server* (constantly cache))
    (server.reader/install-readers)
    cache))

;; TODO this needs refactoring
(def ^:dynamic *rds-server* (setup-rds))


(defn remotify-proc [val]
  (-> val
      (server.spi/remotify *rds-server*)
      pr-str))

(defn outfn-proc [out]
  (let [lock (Object.)]
    (fn [m]
      (binding [*out* out, *flush-on-newline* true, *print-readably* true]
        (locking lock
          (prn (if (#{:ret :tap :browse} (:tag m))
                 (try
                   (assoc m :val (remotify-proc (:val m)))
                   (catch Throwable ex
                     (assoc m :val (remotify-proc (Throwable->map ex))
                            :exception true)))
                 m)))))))

(defn rds-prepl
  "RDS prepl, uses *in* and *out* streams to serve RDS data to a remote repl."
  {:added "1.10"}
  [& {:keys []}]
  (binding [*data-readers* (assoc *data-readers* 'r/id server.reader/lid-reader)
            
            server.reader/*server* *rds-server*]
    (server/prepl *in* (outfn-proc *out*))))

(defn valf-proc [val]
  (pr-str (server.spi/remotify val *rds-server*)))

(defn start-remote-replicant
  ([]
   (start-remote-replicant nil))
  ([{:keys [port] :or {port 5555}}]
   (println "Starting...")
   (let [server-socket (server/start-server 
                        {:port   port,
                         :name   "rds",
                         :accept 'replicant/rds-prepl
                         :args   [{:valf valf-proc}]})]
     server-socket)))

(defn fetch
  ([v] v)
  ([v {:keys [rds/length rds/depth] :as depth-opts}]
   (if (counted? v)
     (if (and length (> (count v) length)) ;; depth needed in spi
       (binding [server.spi/*remotify-length* length]
         (let [rds (server.spi/remotify v *rds-server*)]
           (if (contains? rds :id)
             (assoc rds :id (-> rds meta :r/id))
             rds)))
       v)
     v)))

(defn seq
  ([v] (clojure.core/seq v))
  ([v {:keys [rds/length rds/depth] :as depth-opts}]
   (if (counted? v)
     (if (and length (> (count v) length)) ;; depth needed in spi
       (binding [server.spi/*remotify-length* length]
         (server.spi/remotify (seq v) *rds-server*))
       (clojure.core/seq v))
     (clojure.core/seq v))))

(defn entry
  ([m k] (get m k))
  ([m k {:keys [rds/length rds/depth] :as depth-opts}]
   (let [v (get m k)]
     (if (counted? v)
       (if (and length (> (count v) length)) ;; depth needed in spi
         (binding [server.spi/*remotify-length* length]
           (let [rds (server.spi/remotify (seq v) *rds-server*)]
             (if (contains? rds :id)
               (assoc rds :id (-> rds meta :r/id))
               rds)))
         v)
       v))))

(comment
  (def svr (start-remote-replicant))

  (clojure.core.server/stop-server "rds")

  (->> (range 0 100) (apply hash-map))
    
  (.getIfPresent (.rid->obj *rds-server*) #uuid "6e5a7b9c-0876-4d7d-b7e4-023734d9d9ec")

  (read-string (pr-str (server.spi/remotify (range 0 50) rds)))
  )
