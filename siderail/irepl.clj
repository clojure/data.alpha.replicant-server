(ns irepl
  (:require [morse-server :as server]))

(def morse-server nil)

(defn inspect [val]
  (and morse-server
       (server/inspect morse-server val))
  val)

(defn- irepl-eval [form]
  ;; todo: hook into eval path to inspect every eval
  (clojure.core/eval
   `(let [~'inspect ~inspect]
      ~form)))

(defn irepl [& {:keys [auto-inspect?]}]
  (with-redefs [morse-server (server/morse-server)]
    (let [post-phase (if auto-inspect? inspect identity)]
      (clojure.main/repl
       :init   clojure.core.server/repl-init
       :prompt #(print "i> ")
       :read   clojure.core.server/repl-read
       :eval   (comp post-phase irepl-eval)))

    (and morse-server
         (.close ^ServerSocket (:server morse-server)))))

(comment
  (irepl)
  (irepl :auto-inspect? true)
)
