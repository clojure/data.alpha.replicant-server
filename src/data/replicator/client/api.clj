(ns data.replicator.client.api
  (:require
   [data.replicator.client.reader :as client.reader]
   [data.replicator.client.spi :as client.spi])
  (:import
   [clojure.lang LineNumberingPushbackReader]
   [java.net Socket]
   [java.io BufferedReader InputStreamReader OutputStreamWriter]))

(defmacro ^:private thread
  [^String name daemon & body]
  `(doto (Thread. (fn [] ~@body) ~name)
     (.setDaemon ~daemon)
     (.start)))

(defonce ^:private tid (atom 0))

(defn- rds-call
  [msg out-fn pending-reqs]
  (let [txn (swap! tid inc)
        p (promise)
        req (assoc msg :txn txn)]
    (.println System/out (str "WRITE: " (pr-str req)))
    (dosync (alter pending-reqs assoc txn #(deliver p %2)))
    (out-fn req)
    @p))

(defn morse-client 
  [^String host port event-fn]
  (let [^long port (if (string? port) (Integer/valueOf ^String port) port)
        socket (Socket. host port)
        rd (-> socket .getInputStream InputStreamReader. BufferedReader. LineNumberingPushbackReader.)
        wr (-> socket .getOutputStream OutputStreamWriter.) 
        out-fn (let [out-lock (Object.)] 
                 (fn [val]
                   (locking out-lock
                     (.write wr (pr-str val))
                     (.flush wr))))
        EOF (Object.)
        pending-reqs (ref {})
        callback-agent (agent nil)
        rds-client (reify client.spi/IRemote
                     (remote-fetch [_ rid]
                       (rds-call {:op :fetch :rid rid} out-fn pending-reqs))
                     (remote-seq [_ rid]
                       (rds-call {:op :seq :rid rid} out-fn pending-reqs))
                     (remote-entry [_ rid k]
                       (rds-call {:op :seq :rid rid :k k} out-fn pending-reqs)))]
    ;; read loop
    (thread "morse-client" false
            (binding [client.reader/*remote-client* rds-client
                      *data-readers* (merge *data-readers*
                                            {'r/id #'client.reader/rid-reader
                                             'r/seq #'client.reader/seq-reader
                                             'r/kv #'client.reader/kv-reader
                                             'r/vec #'client.reader/vector-reader
                                             'r/map #'client.reader/map-reader
                                             'r/set #'client.reader/set-reader})]
              (try
                (loop []
                  (let [[o s] (read+string rd false EOF)
                        _ (.println System/out (str "READ: " s))
                        {:keys [tag val txn] :as m} o]
                    (when-not (identical? m EOF)
                      (case tag
                        :inspect (event-fn val) ;; TODO - submit event
                        :tap (event-fn val) ;; TODO - submit event
                        :rds (dosync
                              (alter pending-reqs
                                     (fn [pr]
                                       (let [cb (get pr txn)]
                                         (when cb (send callback-agent cb val))
                                         (dissoc pr txn))))))
                      (recur))))
                (finally
                  (.close wr)))))))

(defn start
  [_]
  (morse-client "localhost" 5555 
                ;; instead of submitting to browser, doall the collection
                (fn [v]
                  (.println System/out (str "EVENT: " v)) 
                  (future (let [s (doall v)]
                            (println "reified inspected value to: " s))))))

(comment
  (morse-client "localhost" 5555 
                (fn [v] (.println System/out (str "EVENT: " v))))
) 
