;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns
  ^{:author "Terje Dahl"}
  george.util.singleton
  (:refer-clojure :exclude [get remove])  ;; https://gist.github.com/ghoseb/287710
  (:require
    [clojure.pprint :refer [pprint]]))


(def ^:dynamic *debug* false)


;;; Singleton patterns ;;;


;; this should be private!
(defonce singletons_ (atom {}))

(defn get
  "returns value for given key  if exists, else nil"
  [k]
  (@singletons_ k))

(defn put
  "sets value for given key, then returns value"
  [k v]
  (swap! singletons_ assoc k v)

  v)

(defn get-or-create
  "returns value for given key if exists,
  else calls provided function, setting its return-value to the key, and returning the value."
  [k f]
  (when *debug* (printf "singleton/get-or-create '%s' ... " k))
  (if-let [v (get k)]
    (do (when *debug* (println "found"))
        v)
    (do
      (when *debug* (println "created"))
      (let [v (f)]
        (put k v)))))


(defn remove
  "removes singleton from singelton-map"
  [k]
  (when *debug* (printf "singleton/remove '%s' ... " k))
  (if-let [f (get k)]
        (do
          (swap! singletons_ dissoc k)
          (when *debug* (println "done")))
        (when *debug* (println "not found"))))


(defn clear-all
  "removes all singletons (by reseting it to an empty map."
  []
  (reset! singletons_ {}))


(defn all-keys []
  (keys @singletons_))

(defn print-all-keys []
  (pprint (all-keys)))

