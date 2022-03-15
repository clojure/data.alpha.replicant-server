(ns data.replicator.server.impl.protocols)

(defprotocol HasRemote
  (-has-remotes? [x] "Returns true if the -remotify of x will contain any remote refs."))

(defprotocol Remotify
  (-remotify [_ server] "If the object is remotable, returns its ref, else self."))

(defprotocol Cache
  (-object->rid [server obj] "Ensure obj in cache, returns rid.")
  (-rid->object [server key] "Given rid, return obj or nil."))
