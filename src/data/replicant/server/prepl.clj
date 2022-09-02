(ns data.replicant.server.prepl
  (:require
   [data.replicant.server.spi :as server.spi]
   [data.replicant.server.reader :as server.reader]
   [data.replicant.server.impl.cache :as server.cache]
   [clojure.core.server :as server])
  (:import
   [clojure.lang MapEntry])
  (:refer-clojure :exclude [seq]))

(set! *warn-on-reflection* true)

(defn- create-cache
  "Establishes an RDS environment by building an RDS cache that LRU evicts values based on
  memory demands and installs the server-side readers. Returns the cache."
  []
  (let [cache-builder (doto (com.github.benmanes.caffeine.cache.Caffeine/newBuilder)
                        (.softValues))]
    (server.cache/create-remote-cache cache-builder)))

;; expects bound: server.spi/*rds-cache*
(defn remotify-proc [val]
  (let [obj (server.spi/remotify val server.spi/*rds-cache*)]
    (binding [*print-meta* true]
      (pr-str obj))))

(defn outfn-proc [out rds-cache]
  (let [lock (Object.)]
    (fn [m]
      (binding [*out* out, *flush-on-newline* true, *print-readably* true
                server.spi/*rds-cache* rds-cache]
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
  "RDS prepl, will be run on a connected socket client thread.
   Uses *in* and *out* streams to serve RDS data to a remote repl."
  {:added "1.10"}
  [rds-cache] ;; could also take default depth opts here
  (binding [server.spi/*rds-cache* rds-cache
            *data-readers* (assoc *data-readers* 'l/id server.reader/lid-reader)]
    (server/prepl *in* (outfn-proc *out* rds-cache))))

;; TODO: parameterize with default depth opts, cache opts
(defn start-remote-replicant
  ([]
   (start-remote-replicant nil))
  ([{:keys [port] :or {port 5555}}]
   (println "Replicant server listening on" port "...")
   (let [server-socket (server/start-server 
                        {:port   port,
                         :name   "rds",
                         :accept 'data.replicant.server.prepl/rds-prepl
                         :args   [(create-cache)]
                         :server-daemon false})]
     server-socket)))

(defn- annotate [val & {:as opts}]
  (if (instance? clojure.lang.IObj val)
    (with-meta val {:depth-opts opts})
    val))

;; remote api
;; expects bound: server.spi/*rds-cache*
(defn fetch
  ([v] v)
  ([v {:keys [rds/length rds/level] :as depth-opts :or {length server.spi/*remotify-length*, level server.spi/*remotify-level*}}]
   (if (counted? v)
     (if (and length (> (count v) length)) ;; level needed in spi
       (binding [server.spi/*remotify-length* length
                 server.spi/*remotify-level* level]
         (let [rds (server.spi/remotify v server.spi/*rds-cache*)]
           (if (contains? rds :id)
             (assoc rds :id (-> rds meta :r/id))
             (annotate rds depth-opts))))
       (annotate v depth-opts))
     (annotate v depth-opts))))

;; remote api
;; expects bound: server.spi/*rds-cache*
(defn seq
  ([v] (clojure.core/seq v))
  ([v {:keys [rds/length rds/level] :as depth-opts :or {length server.spi/*remotify-length*, level server.spi/*remotify-level*}}]
   (if (counted? v)
     (if (and length (> (count v) length)) ;; level needed in spi
       (binding [server.spi/*remotify-length* length
                 server.spi/*remotify-level* level]
         (annotate (server.spi/remotify (clojure.core/seq v) server.spi/*rds-cache*) depth-opts))
       (annotate (clojure.core/seq v) depth-opts))
     (annotate (clojure.core/seq v) depth-opts))))

;; remote api
;; expects bound: server.spi/*rds-cache*
(defn entry
  ([m k] (MapEntry/create k (get m k)))
  ([m k {:keys [rds/length rds/level] :as depth-opts :or {length server.spi/*remotify-length*, level server.spi/*remotify-level*}}]
   (let [v (get m k)
         retv (if (counted? v)
                (if (and length (> (count v) length)) ;; level needed in spi
                  (binding [server.spi/*remotify-length* length
                            server.spi/*remotify-level*  level]
                    (let [rds (server.spi/remotify (clojure.core/seq v) server.spi/*rds-cache*)]
                      (if (contains? rds :id)
                        (assoc rds :id (-> rds meta :r/id))
                        (annotate rds depth-opts))))
                  (annotate v depth-opts))
                (annotate v depth-opts))]
     (annotate (MapEntry/create k retv) depth-opts))))

;; remote api
;; expects bound: server.spi/*rds-cache*
(defn string
  [v]
  (str v))

(comment
  (def svr (start-remote-replicant))

  (clojure.core.server/stop-server "rds")

  (tap> (vec (range 400)))
  (tap> (zipmap (range 400) (range 400)))
  (->> (range 0 100) (apply hash-map))

  (binding [server.spi/*remotify-length* nil]
    (server.spi/remotify [1 2 3] server.spi/*rds-cache*))

  (def e (.getIfPresent (.rid->obj server.spi/*rds-cache*) #uuid "f58ca48c-a318-4593-821d-8dfa5bb21f30"))

  (first e)

  (read-string (pr-str (server.spi/remotify (range 0 50) rds)))

  ;; test server-side printing
  (let [cache-builder (doto (com.github.benmanes.caffeine.cache.Caffeine/newBuilder) (.softValues))
        cache (server.cache/create-remote-cache cache-builder)]
    (alter-var-root #'server.spi/*rds-cache* (constantly cache))

    (binding [*default-data-reader-fn* (fn [tag val]
                                         (if (= tag 'l/id)
                                           (server.reader/lid-reader val)
                                           (throw (RuntimeException. (str "Unknown reader tag:" tag)))))
              *print-meta* true]
      (let [val (with-meta [1 2] (zipmap (range 1000) (range 1000)))]
        (println "val     " val)
        (let [r (server.spi/remotify val cache)]
          (println "remotify" r)
          (println "pr      " (pr-str r))))))
  
  ;; Thread dump
  (.dumpAllThreads (java.lang.management.ManagementFactory/getThreadMXBean) false false)
  )
