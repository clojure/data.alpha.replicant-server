(require :reload
         '[data.remote.server :as server])

(set! *print-length* (* server/*remotify-length* 2))

(def objects
  [1
   :a
   "hello"
   nil
   false
   (range 10)
   (range 100)
   (range 10000)
   (into [] (range 10))
   (into [] (range 1000))
   {:a 1 :b 2}
   (first {:a 1 :b 2})
   {:a 1 :b (Object.)}
   [:a :b (Object.) :d :e]
   (list :a :b (Object.) :d :e)
   [[[[1]]]]]
  )

(map server/has-remotes? objects)

(def server (java.util.concurrent.ConcurrentHashMap.))

(doseq [obj objects]
  (prn obj)
  (print "=> " )
  (prn (server/remotify obj server))
  (prn))

(server/remotify {:A 1 :B (Object.)} server)



