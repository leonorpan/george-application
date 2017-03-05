(ns george.application.applet-loader
  (:require [clojure.tools.namespace.find :refer [find-sources-in-dir find-namespaces]]
            [clojure.java.io :as cio]
            [clojure.string :as cs]
            [clojure.java.classpath :as cp]))


(defn- verify-applet
  "returns a map with [ns name descrition main-fn] if verfified, else nil"
  [applet-ns]
  (println "Verifying applet:" applet-ns)
;(try
  (require applet-ns)
  (if-let [info-fn (ns-resolve applet-ns 'info)]
    (try
      (let [info (info-fn)]
        (if-let [name (:george.application.applet/name info)]
          (if-let [description (:george.application.applet/description info)]
            (if-let [main (:george.application.applet/main info)]
              (if-let [main-fn (ns-resolve applet-ns main)]
                (do
                  ;(println "  ## main:" main (type main))
                  ;(println "  ## main-fn:" main-fn (type main-fn))
                  {:ns applet-ns
                   :name name
                   :description description
                   :main-fn main-fn})

                (println "  ERROR: The applet's 'main' function could not be resolved!"))
              (println "  ERROR: The applet info has no :george.application.applet/main"))
            (println "  ERROR: The applet info has no :george.application.applet/description"))
          (println "  ERROR: The applet info has no :george.application.applet/name")))
      (catch Exception e (println (format "  ERROR: Calling %s/info failed!  %s" applet-ns  e)) (set! *e e)))
    (println "  ERROR: The applet's 'info' function could not be resolved!")))
  ;(catch Exception e (println "  ERROR: Loading applet namespace failed!") (.printStackTrace e))))


(defn find-applets
  "returns lazy seq of all namespaces matching 'george.application.applet.xyz'"
  []
  (filter
    #(re-find #"george.application.applet\..+" (str %))
    (find-namespaces (cp/classpath))))


(defn load-applets []
  (let [applet-ns-list (vec (find-applets))
        verified-info-list (vec (map verify-applet applet-ns-list))]
    (filter some? verified-info-list)))


;; DEV
;(println (str "  ## find-applets: " (vec (find-applets))))
;(doseq [a (load-applets)] (println (str "  -a: " a)))
;(println (str "  ## applets:\n"  (cs/join "\n" (load-applets))))