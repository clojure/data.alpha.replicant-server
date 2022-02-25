(ns data.remote.impl.protocols)

(defprotocol HasRemote
  (-has-remotes? [x] "Returns true if the -remotify of x will contain any remote refs."))

(defprotocol Remotify
  (-remotify [_ server] "If the object is remotable, returns its ref, else self."))

(defprotocol Server
  (-cache-remote-ref [server obj] "Cache obj on server, returning object uuid."))


