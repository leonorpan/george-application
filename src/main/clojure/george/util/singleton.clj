;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns
  ^{:author "Terje Dahl"}
  george.util.singleton
  (:refer-clojure :exclude [get remove]))  ;; https://gist.github.com/ghoseb/287710





;;; Singleton patterns ;;;


;; this should be private!
(defonce singletons-atom (atom {}))

(defn get
  "returns value for given key  if exists, else nil"
  [k]
  (@singletons-atom k))

(defn put
  "sets value for given key, then returns value"
  [k v]
  (swap! singletons-atom assoc k v)

  v)

(defn put-or-create
  "returns value for given key if exists,
  else calls provided function, setting its return-value to the key, and retruning the value."
  [k f]
  (if-let [v (get k)]
    v
    (let [v (f)]
      (println "singleton/get-or-create: singleton created")
      (put k v))))


(defn remove
  "removes singleton from singelton-map"
  [k]
  (println "singleton/remove: singleton removed")
  (swap! singletons-atom dissoc k))


(defn clear-all
  "removes all singletons (by reseting it to an empty map."
  []
  (reset! singletons-atom {}))


