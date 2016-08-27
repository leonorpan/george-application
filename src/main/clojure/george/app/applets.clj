(ns george.app.applets
  (:require [clojure.tools.namespace.find :refer [find-sources-in-dir find-namespaces]]
            [clojure.java.io :as cio]
            [clojure.java.classpath :as cp]))


(defn- verify-applet
  "returns a map with [ns name descrition main-fn] if verfified, else nil"
  [applet-ns]
  (println "Verifying applet:" applet-ns)
  (try
    (require applet-ns :reload)
    (if-let [info-fn (ns-resolve applet-ns 'info)]
      (try
        (let [info (info-fn)]
          (if-let [name (:george.app.applet/name info)]
            (if-let [description (:george.app.applet/description info)]
              (if-let [main (:george.app.applet/main info)]
                (if-let [main-fn (ns-resolve applet-ns main)]
                  {:ns applet-ns
                   :name name
                   :description description
                   :main-fn main-fn}

                  (println "  ERROR: The applet's 'main' function could not be resolved!"))
                (println "  ERROR: The applet info has no :george.app.applet/main"))
              (println "  ERROR: The applet info has no :george.app.applet/description"))
            (println "  ERROR: The applet info has no :george.app.applet/name")))
        (catch Exception e (println (format "  ERROR: Calling %s/info failed!" applet-ns)) (.printStackTrace e)))
      (println "  ERROR: The applet's 'info' function could not be resolved!"))
    (catch Exception e (println "  ERROR: Loading applet namespace failed!") (.printStackTrace e))))



(defn load-applets []
  (let [applet-ns-seq (filter #(re-find #"george.app.applet..+" (str %)) (find-namespaces (cp/classpath)))]
    (filter some? (for [applet-ns applet-ns-seq] (verify-applet applet-ns)))))


;; DEV
;(doseq [a (doall (load-applets))] (println a))