(ns data.replicant.server.impl.protocols)

(defprotocol Cache
  (-object->rid [server obj] "Ensure obj in cache, returns rid.")
  (-rid->object [server key] "Given rid, return obj or nil if rid not found."))
