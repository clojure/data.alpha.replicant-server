(ns morse-server
  (:require
   [data.replicator.server.spi :as server.spi]
   [data.replicator.server.reader :as server.reader]
   [data.replicator.server.impl.cache :as server.cache]
   [clojure.core.server :as server])
  (:import
   [clojure.lang MapEntry]
   [java.net ServerSocket]
   [java.util.concurrent BlockingQueue ArrayBlockingQueue]))

(defn- setup-rds []
  (let [;; create server
        cache-builder (-> (com.google.common.cache.CacheBuilder/newBuilder)
                          (.maximumSize 100000))
        cache (server.cache/create-remote-cache cache-builder)]
    cache))

(defmacro ^:private thread
  [^String name daemon & body]
  `(doto (Thread. (fn [] ~@body) ~name)
     (.setDaemon ~daemon)
     (.start)))

;; called with *in* and *out* bound to a client
(defn morse-conn
  [{:keys [rds-cache inspect-queue out-fn]}]
  (.println System/out (str "CONNECT"))
  (let [out *out*]
    ;; Thread to read inspect events and push to client
    (thread "morse-server" true
            (binding [*out* out
                      server.reader/*server* rds-cache
                      *data-readers* (assoc *data-readers* 'l/id #'server.reader/lid-reader)]
              (loop []
                (let [obj (.take ^BlockingQueue inspect-queue)]  ;; .poll with timeout later
                  (when obj
                    (out-fn {:tag :inspect
                             :val (server.spi/remotify obj rds-cache)}))
                  (recur))))))
  ;; Request/response loop
  (binding [*read-eval* false
            server.reader/*server* rds-cache
            *data-readers* (assoc *data-readers* 'l/id #'server.reader/lid-reader)]
    (loop []
      (try
        (let [[o s] (read+string *in*) 
              {:keys [op rid txn] :as r} o]
          (.println System/out (str "READ: " s))
          (case op
            :fetch (out-fn {:tag :rds :txn txn :val (server.spi/remotify rid rds-cache)})
            :seq (out-fn {:tag :rds :txn txn :val (server.spi/remotify (seq rid) rds-cache)})
            :entry (let [k (:k r)] (out-fn {:tag :rds
                                            :txn txn
                                            :val (when (contains? rid k)
                                                   (server.spi/remotify
                                                    (MapEntry/create k (get rid k))
                                                    rds-cache))}))
            :eval (out-fn {:tag :ret :val (eval (:form r))})))
        (catch Throwable _
          ;; TODO
          ))
      (recur))))

(defn morse-server
  [& {:keys [port] :or {port 5555}}]
  (let [rds-cache (setup-rds)
        inspect-queue (ArrayBlockingQueue. 1024)
        out-lock (Object.)
        out-fn (fn [val]
                 (.println System/out (str "WRITE: " (pr-str val)))
                 (locking out-lock
                   (prn val)))
        config {:rds-cache rds-cache
                :inspect-queue inspect-queue
                :out-fn out-fn}
        server (server/start-server {:port port
                                     :name "morse"
                                     :accept `morse-conn
                                     :server-daemon false
                                     :args [config]})]
    (.println System/out "WAITING")
    (assoc config :server server)))

(defn inspect
  [server val]
  (.offer ^BlockingQueue (:inspect-queue server) val))

(defn start
  [_]
  (let [s (morse-server)]
    (inspect s (vec (range 55)))))

(comment
  (def s (morse-server))
  ;; connect with: nc localhost 5555

  (inspect s (vec (range 1000)))

  (.close ^ServerSocket (:server s))
  )