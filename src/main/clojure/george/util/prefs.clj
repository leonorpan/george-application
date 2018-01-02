;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns
  ^{:author "Terje Dahl"
    :doc "A simple library for setting/getting preferences for user or system"}
  george.util.prefs
  (:refer-clojure :exclude [get])  ;; https://gist.github.com/ghoseb/287710
  (:import [java.util.prefs Preferences]))


;; http://docs.oracle.com/javase/8/docs/api/java/util/prefs/Preferences.html


;; node-path:  tld.xxx.yyy.etc
(defn system-node [^String node-path]
  (.node (Preferences/systemRoot) node-path))

(defn user-node [^String node-path]
  (.node (Preferences/userRoot) node-path))

(defn put [node k v]
  (.put node (str k) (str v)))

(defn get
  ([node k]
   (get node k "nil"))
  ([node k default-v]
   (.get node (str k) (str default-v))))

