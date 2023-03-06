;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.data.alpha.replicant.server.impl.protocols)

(defprotocol HasRemote
  (-has-remotes? [x] "Returns true if the -remotify of x will contain any remote refs."))

(defprotocol Remotify
  (-remotify [_ server] "If the object is remotable, returns its ref, else self."))

(defprotocol Cache
  (-put [cache key object] "Given a cache instant, key, and object; puts object at key.")
  (-get [cache key]        "Given key, return mapped value or nil if key not found."))

