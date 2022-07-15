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

;; called with *in* and *out* bound to a client
(defn morse-conn
  [{:keys [rds-cache inspect-queue]}]
  (.println System/out (str "CONNECT: " rds-cache inspect-queue))
  (let [out *out*]
    ;; Thread to read inspect events and push to client
    (.start (Thread.
             #(binding [*out* out
                        server.reader/*server* rds-cache
                        *data-readers* (assoc *data-readers* 'l/id #'server.reader/lid-reader)]
                (loop []
                  (let [obj (.take ^BlockingQueue inspect-queue)]  ;; .poll with timeout later
                    (when obj
                      (prn {:tag :inspect
                            :val (server.spi/remotify obj rds-cache)}))
                    (recur)))))))
  ;; Request/response loop
  (binding [*read-eval* false
            server.reader/*server* rds-cache
            *data-readers* (assoc *data-readers* 'l/id #'server.reader/lid-reader)]
    (loop []
      (try
        (let [o (read *in*)
              _ (.println System/out (str "READ: " o))
              {:keys [op rid txn] :as r} o]
          (case op
            :fetch (prn {:tag :rds :txn txn :val (server.spi/remotify rid rds-cache)})
            :seq (prn {:tag :rds :txn txn :val (server.spi/remotify (seq rid) rds-cache)})
            :entry (let [k (:k r)] (prn {:tag :rds :txn txn
                                         :val (when (contains? rid k)
                                                (server.spi/remotify
                                                 (MapEntry/create k (get rid k))
                                                 rds-cache))}))
            :eval (prn {:tag :ret :val (eval (:form r))})))
        (catch Throwable _
          ;; TODO
          ))
      (recur))))

(defn morse-server
  [& {:keys [port] :or {port 5555}}]
  (let [rds-cache (setup-rds)
        inspect-queue (ArrayBlockingQueue. 1024)
        config {:rds-cache rds-cache
                :inspect-queue inspect-queue}
        server (server/start-server {:port port,
                                     :name "morse",
                                     :accept `morse-conn,
                                     :args [config]})]
    (assoc config :server server)))

(defn inspect
  [server val]
  (.offer ^BlockingQueue (:inspect-queue server) val))

(comment
  (def s (morse-server))
  ;; connect with: nc localhost 5555

  (inspect s (vec (range 1000)))

  (.close ^ServerSocket (:server s))
  )