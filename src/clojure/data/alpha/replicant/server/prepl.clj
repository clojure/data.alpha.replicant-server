;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.data.alpha.replicant.server.prepl
  (:require
   [clojure.data.alpha.replicant.server.spi :as server.spi]
   [clojure.data.alpha.replicant.server.reader :as server.reader]
   [clojure.data.alpha.replicant.server.impl.cache :as server.cache]
   [clojure.core.server :as server]
   [clojure.datafy :as d])
  (:import
   [clojure.lang MapEntry])
  (:refer-clojure :exclude [seq]))

(set! *warn-on-reflection* true)

(defn- create-default-cache
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

(defn- outfn-proc [out rds-cache]
  (let [lock (Object.)]
    (fn [m]
      (binding [*out* out, *flush-on-newline* true, *print-readably* true
                server.spi/*rds-cache* rds-cache]
        (locking lock          
          (prn (if (#{:ret :tap} (:tag m))
                 (let [{:keys [rds/lengths rds/level] :or {lengths server.spi/*remote-lengths*, level server.spi/*remote-depth*}} (:depth-opts (meta (:val m)))]
                   (binding [server.spi/*remote-lengths* lengths
                             server.spi/*remote-depth* level]
                     (try
                       (assoc m :val (remotify-proc (:val m)))
                       (catch Throwable ex
                         (assoc m :val (remotify-proc (Throwable->map ex))
                                :exception true)))))
                 m)))))))

(defn rds-prepl
  "Uses *in* and *out* streams to serve RDS data given an RDS cache."
  [rds-cache]
  (binding [server.spi/*rds-cache* rds-cache
            *data-readers* (assoc *data-readers* 'l/id server.reader/lid-reader)]
    (server/prepl *in* (outfn-proc *out* rds-cache))))

(defn start-replicant
  "Local API: Starts a named Replicant server in the current process on a connected socket client thread.
  By default this function starts a server named \"rds\" listening on localhost:5555 and initializes a
  default Replicant cache. Callers may pass an options map with keys :server-name, :port, and :cache to
  override those defaults."
  ([]
   (start-replicant nil))
  ([& {:keys [port cache server-name] :or {port 5555, server-name "rds"}}]
   (println "Replicant server listening on" port "...")
   (let [server-socket (server/start-server 
                        {:port   port,
                         :name   server-name,
                         :accept 'clojure.data.alpha.replicant.server.prepl/rds-prepl
                         :args   [(or cache (create-default-cache))]
                         :server-daemon false})]
     server-socket)))

(defn stop-replicant
  "Local API: Stops a named Replicant server in the current process. Stopping an active Replicant server
  closes all clients connected to it and clears its remote data cache."
  [server-name]
  (clojure.core.server/stop-server server-name))

(defn- annotate [val & {:as opts}]
  (if (instance? clojure.lang.IObj val)
    (with-meta val {:depth-opts opts})
    val))

(defn fetch
  "Remote API: Called by a replicant client to retrieve an object from the cache. Takes an
  object v and remotifies it if the :rds/lengths and :rds/level values cause a
  depth options threshold crossings. Expects a bound server.spi/*rds-cache* value."
  ([v] v)
  ([v {:keys [rds/lengths rds/level] :as depth-opts :or {lengths server.spi/*remote-lengths*, level server.spi/*remote-depth*}}]
   (if (counted? v)
     (if (and lengths (> (count v) (first lengths))) ;; level needed in spi
       (binding [server.spi/*remote-lengths* lengths
                 server.spi/*remote-depth* level]
         (let [rds (server.spi/remotify v server.spi/*rds-cache*)]
           (if (contains? rds :id)
             (assoc rds :id (-> rds meta :r/id))
             (annotate rds depth-opts))))
       (annotate v depth-opts))
     (annotate v depth-opts))))

(defn seq
  "Remote API: Called by a replicant client to retrieve a seq for a collection. Takes an
  object v and remotifies it if the :rds/lengths and :rds/level values cause a
  depth options threshold crossings. Expects a bound server.spi/*rds-cache* value."
  ([v] (clojure.core/seq v))
  ([v {:keys [rds/lengths rds/level] :as depth-opts :or {lengths server.spi/*remote-lengths*, level server.spi/*remote-depth*}}]
   (if (counted? v)
     (if (and lengths (> (count v) lengths)) ;; level needed in spi
       (binding [server.spi/*remote-lengths* lengths
                 server.spi/*remote-depth* level]
         (annotate (server.spi/remotify (clojure.core/seq v) server.spi/*rds-cache*) depth-opts))
       (annotate (clojure.core/seq v) depth-opts))
     (annotate (clojure.core/seq v) depth-opts))))

(defn entry
  "Remote API: Called by a replicant client to retrieve a value mapped at key k for a collection m. Takes an
  object v and remotifies it if the :rds/lengths and :rds/level values cause a
  depth options threshold crossings. Expects a bound server.spi/*rds-cache* value."
  ([m k]
   (when (contains? m k) (MapEntry/create k (get m k))))
  ([m k {:keys [rds/lengths rds/level] :as depth-opts :or {lengths server.spi/*remote-lengths*, level server.spi/*remote-depth*}}]
   (when (contains? m k)
     (let [v (get m k)
           retv (if (counted? v)
                  (if (and lengths (> (count v) lengths))
                    (binding [server.spi/*remote-lengths* lengths
                              server.spi/*remote-depth*  level]
                      (let [rds (server.spi/remotify (clojure.core/seq v) server.spi/*rds-cache*)]
                        (if (contains? rds :id)
                          (assoc rds :id (-> rds meta :r/id))
                          (annotate rds depth-opts))))
                    (annotate v depth-opts))
                  (annotate v depth-opts))]
       (annotate (MapEntry/create k retv) depth-opts)))))

;; remote api
;; expects bound: server.spi/*rds-cache*
(defn string
  "Remote API: Called by a replicant client to retrieve a string representation for an object v."
  [v]
  (str v))

(defn datafy
  "Remote API: Called by a replicant client to retrieve a datafy representation for an object v."
  [v]
  (d/datafy v))

(comment
  (def svr (start-replicant))
  (def svr (start-replicant {:port 5556}))
  (stop-replicant "rds")
)
