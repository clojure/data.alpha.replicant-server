(ns replicant
  (:require
   [data.replicator.server.spi :as server.spi]
   [data.replicator.server.reader :as server.reader]
   [data.replicator.server.impl.cache :as server.cache]
   [clojure.core.server :as server])
  (:refer-clojure :exclude [seq]))

(defn fetch [v] {:fetched v})
(defn seq [v] {:seqed v})
(defn entry [v] {:entryed v})

(defn setup-rds []
  (let [;; create server
        cache-builder (-> (com.google.common.cache.CacheBuilder/newBuilder)
                          (.maximumSize 100000))
        cache (server.cache/create-remote-cache cache-builder)]
    ;; install server and readers
    (alter-var-root #'server.reader/*server* (constantly cache))
    (server.reader/install-readers)
    cache))

(def rds-server (setup-rds))

(defn remotify-proc [val]
  (-> val
      (server.spi/remotify rds-server)
      pr-str))

(defn replicant-outfn [out]
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

(defn rid-hydrator [rid]
  (let [val (#'server.reader/lid-reader rid)]
    `(quote ~(with-meta val {:r/id rid}))))

(defn rds-prepl
  "RDS prepl, uses *in* and *out* streams to serve RDS data to a remote repl."
  {:added "1.10"}
  [& {:keys []}]
  (binding [*data-readers* (assoc *data-readers* 'r/id rid-hydrator)
            server.reader/*server* rds-server]
    (server/prepl *in* (replicant-outfn *out*))))

(defn output-proc [val]
  (pr-str (server.spi/remotify val rds-server)))

(defn start-remote-replicant
  ([]
   (start-remote-replicant nil))
  ([{:keys [port] :or {port 5555}}]
   (println "Starting...")
   (let [server-socket (server/start-server 
                        {:port   port,
                         :name   "rds",
                         :accept 'replicant/rds-prepl
                         :args   [{:valf output-proc}]})]
     server-socket)))

(comment
  (def svr (start-remote-replicant))

  (clojure.core.server/stop-server "rds")

  (def rds (setup-rds))

  (read-string (pr-str (server.spi/remotify (range 0 50) rds)))
  )
