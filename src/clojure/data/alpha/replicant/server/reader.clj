;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.data.alpha.replicant.server.reader
  (:require
    [clojure.data.alpha.replicant.server.spi :as spi]))

(defn lid-reader
  "Read '#l/id id' and return the cached object"
  [rid]
  `(let [val#  (spi/rid->object spi/*rds-cache* ~rid)
         mval# (if (not (nil? val#))
                 (if (instance? clojure.lang.IObj val#)
                   (with-meta val# {:r/id ~rid})
                   val#)
                 (throw (ex-info (str "Remote data structure not found in cache for rid " ~rid)
                                 {:id ~rid})))]
     mval#))
