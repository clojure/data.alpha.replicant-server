(require :reload
         '[data.remote.server :as server])

(def objects
  [1
   :a
   "hello"
   nil
   false
   (range 10)
   (range 100)
   (into [] (range 10))
   (into [] (range 1000))
   [:a :b (Object.) :d :e]
   (list :a :b (Object.) :d :e)
   [[[[1]]]]]
  )

(map server/has-remotes? objects)

(def server (java.util.concurrent.ConcurrentHashMap.))

(map #(server/remotify % server) objects)
(map meta *1)


