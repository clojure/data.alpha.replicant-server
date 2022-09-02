(ns data.replicant.client.impl.protocols)

(defprotocol IRelay
  (relay-seq [this] "Get seq of remote object")
  (relay-entry [this k] "Get entry of remote object")
  (relay-apply [this args] "Apply remote object with args"))
