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

