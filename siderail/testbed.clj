(ns testbed
  (:require
    [data.replicator.client.reader :as client.reader]
    [data.replicator.client.spi :as client.spi]
    [data.replicator.server.reader :as server.reader]
    [data.replicator.server.impl.cache :as cache]
    [data.replicator.server.spi :as server.spi]))

(def ^:dynamic *log* true)

(defn wire-fn
  "Simulate value going over a wire"
  [val]
  (-> val pr-str read-string))

(defn over-wire
  "Invoke f (on server) with args (from client). Apply print/read to the args
  and to the return to simulate both sides."
  [f & args]
  (let [wire-args (map wire-fn args)]
    (when *log*
      (println "  wire-args in " (map class args))
      (println "  wire value   " (map pr-str args))
      (println "  wire-args out" (map class wire-args)))
    (let [wire-ret (apply f wire-args)
          _ (when *log*
              (println "  ---")
              (println "  wire-ret in " (class wire-ret))
              (println "  wire value" (pr-str wire-ret)))
          wire-out (wire-fn wire-ret)]
      (when *log* (println "  wire-ret out" (class wire-out)))
      wire-out)))

(defn setup []
  (let [ ;; create server
        cache-builder (-> (com.google.common.cache.CacheBuilder/newBuilder)
                        (.maximumSize 100000))
        cache (cache/create-remote-cache cache-builder)

        ;; create client with a fake wire to the server
        ;; rids are sent "over the wire" to the server
        ;; responses are sent "over the wire" back to the client
        client (reify client.spi/IRemote
                 (remote-fetch [_ rid]
                   (when *log* (println "\nrfetch" rid))
                   (over-wire (fn [robj] (server.spi/remotify robj cache)) rid))
                 (remote-seq [_ rid]
                   (when *log* (println "\nrseq" rid))
                   (over-wire (fn [robj] (server.spi/remotify (seq robj) cache)) rid))
                 (remote-entry [_ rid k]
                   (when *log* (println "\nrentry" rid k))
                   (over-wire (fn [robj k] (server.spi/remotify (find robj k) cache)) rid k)))]
    ;; install server readers
    (alter-var-root #'server.reader/*server* (constantly cache))
    (server.reader/install-readers)

    ;; install client readers
    (alter-var-root #'client.reader/*remote-client* (constantly client))
    (client.reader/install-readers)

    ;; return server
    cache))

(comment
  (def server (setup))
  (def o (vec (range 55)))
  (def vref (server.spi/register server o))
  (def vid (wire-fn vref))
  (def v @vid)
  v
  (class v)
  (seq v)
  (into [] v)
  (->> v (map inc) (reduce +))
  (nth v 54)
  (vals v)

  (def sref (server.spi/register server (set (range 30))))
  (def sid (wire-fn sref))
  (def s @sid)
  s
  (contains? s 29)

  (server.spi/rid->object server (.-rid ^data.replicator.client.impl.rds.Relay vid))
  )
