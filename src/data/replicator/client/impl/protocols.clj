(ns data.replicator.client.impl.protocols)

;; depth-opts?

(defprotocol IRelay
  (relay-seq [this] "Get seq of remote object")
  (relay-entry [this k] "Get entry of remote object"))