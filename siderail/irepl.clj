(ns irepl)

(defn inspect [val]
  (println "adding inspect message " {:op :inspect :val val})
  val)

(defn- irepl-eval [form]
  ;; todo: hook into eval path to inspect every eval
  (clojure.core/eval
   `(let [~'inspect ~inspect]
      ~form)))

(defn irepl []
  (clojure.main/repl
   :init   clojure.core.server/repl-init
   :prompt #(print "i> ")
   :read   clojure.core.server/repl-read
   :eval   irepl-eval))
