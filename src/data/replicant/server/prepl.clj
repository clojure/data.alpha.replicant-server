(ns data.replicant.server.prepl
  (:require
   [data.replicator.server.spi :as server.spi]
   [data.replicator.server.reader :as server.reader]
   [data.replicator.server.impl.cache :as server.cache]
   [clojure.core.server :as server])
  (:import
   [clojure.lang MapEntry])
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
          (prn (if (#{:ret :tap} (:tag m))
                 (let [{:keys [rds/length rds/level] :or {length server.spi/*remotify-length*, level server.spi/*remotify-level*}} (:depth-opts (meta (:val m)))]
                   (binding [server.spi/*remotify-length* length
                             server.spi/*remotify-level* level]
                     (try
                       (assoc m :val (remotify-proc (:val m)))
                       (catch Throwable ex
                         (assoc m :val (remotify-proc (Throwable->map ex))
                                :exception true)))))
                 m)))))))

(defn rds-prepl
  "RDS prepl, uses *in* and *out* streams to serve RDS data to a remote repl."
  {:added "1.10"}
  [& {:keys []}]
  (binding [*data-readers* (assoc *data-readers* 'l/id server.reader/lid-reader)          
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
                         :accept 'data.replicant.server.prepl/rds-prepl
                         :args   [{:valf valf-proc}]})]
     server-socket)))

(defn annotate [val & {:as opts}]
  (if (instance? clojure.lang.IObj val)
    (with-meta val {:depth-opts opts})
    val))

(defn fetch
  ([v] v)
  ([v {:keys [rds/length rds/level] :as depth-opts :or {length server.spi/*remotify-length*, level server.spi/*remotify-level*}}]
   (if (counted? v)
     (if (and length (> (count v) length)) ;; level needed in spi
       (binding [server.spi/*remotify-length* length
                 server.spi/*remotify-level* level]
         (let [rds (server.spi/remotify v *rds-server*)]
           (if (contains? rds :id)
             (assoc rds :id (-> rds meta :r/id))
             (annotate rds depth-opts))))
       (annotate v depth-opts))
     (annotate v depth-opts))))

(defn seq
  ([v] (clojure.core/seq v))
  ([v {:keys [rds/length rds/level] :as depth-opts :or {length server.spi/*remotify-length*, level server.spi/*remotify-level*}}]
   (if (counted? v)
     (if (and length (> (count v) length)) ;; level needed in spi
       (binding [server.spi/*remotify-length* length
                 server.spi/*remotify-level* level]
         (annotate (server.spi/remotify (seq v) *rds-server*) depth-opts))
       (annotate (clojure.core/seq v) depth-opts))
     (annotate (clojure.core/seq v) depth-opts))))

(defn entry
  ([m k] (MapEntry/create k (get m k)))
  ([m k {:keys [rds/length rds/level] :as depth-opts :or {length server.spi/*remotify-length*, level server.spi/*remotify-level*}}]
   (let [v (get m k)
         retv (if (counted? v)
                (if (and length (> (count v) length)) ;; level needed in spi
                  (binding [server.spi/*remotify-length* length
                            server.spi/*remotify-level*  level]
                    (let [rds (server.spi/remotify (seq v) *rds-server*)]
                      (if (contains? rds :id)
                        (assoc rds :id (-> rds meta :r/id))
                        (annotate rds depth-opts))))
                  (annotate v depth-opts))
                (annotate v depth-opts))]
     (annotate (MapEntry/create k retv) depth-opts))))

(comment
  (def svr (start-remote-replicant))

  (clojure.core.server/stop-server "rds")

  (tap> (vec (range 400)))
  (tap> (zipmap (range 400) (range 400)))
  (->> (range 0 100) (apply hash-map))

  (binding [server.spi/*remotify-length* nil]
    (server.spi/remotify [1 2 3] *rds-server*))
    
  (def e (.getIfPresent (.rid->obj *rds-server*) #uuid "f58ca48c-a318-4593-821d-8dfa5bb21f30"))

  (first e)

  (read-string (pr-str (server.spi/remotify (range 0 50) rds)))
)
