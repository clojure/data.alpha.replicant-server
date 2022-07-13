(ns transmitter
  (:require
   [data.replicator.server.spi :as server.spi]
   [data.replicator.server.reader :as server.reader]
   [data.replicator.server.impl.cache :as server.cache]
   [clojure.core.server :as server]))

(defn setup-rds []
  (let [;; create server
        cache-builder (-> (com.google.common.cache.CacheBuilder/newBuilder)
                          (.maximumSize 100000))
        cache (server.cache/create-remote-cache cache-builder)]
    ;; install server and readers
    (alter-var-root #'server.reader/*server* (constantly cache))
    (server.reader/install-readers)
    cache))

(defn rds-prepl
  "RDS prepl, uses *in* and *out* streams to serve RDS data to a remote repl."
  {:added "1.10"}
  [& {:keys []}]
  (let [valf pr-str
        out *out*
        lock (Object.)]
    (server/prepl *in*
           (fn [m]
             (binding [*out* out, *flush-on-newline* true, *print-readably* true]
               (locking lock
                 (prn (if (#{:ret :tap :browse} (:tag m))
                        (try
                          (assoc m :val (valf (:val m)))
                          (catch Throwable ex
                            (assoc m :val (valf (Throwable->map ex))
                                   :exception true)))
                        m))))))))

(defn start-transmitter
  ([]
   (start-transmitter nil))
  ([{:keys [port] :or {port 5555}}]
   (let [rds-server (setup-rds)
         server-socket (server/start-server 
                        {:port   port,
                         :name   "rds",
                         :accept 'server/rds-prepl
                         :args   [{:valf #(pr-str (server.spi/remotify % rds-server))}]})]
     )))

