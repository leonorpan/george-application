;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.


(ns george.applet
  (:require
    [clojure.tools.namespace.find :refer [find-sources-in-dir find-namespaces]]
    [clojure.pprint :refer [pprint]]
    [clojure.java.classpath :as cp])
  (:import
    [clojure.lang Symbol]))


;; For further development of this consept:
;; https://www.developer.com/java/article.php/3848881/Service-Provider-Interface-Creating-Extensible-Java-Applications.htm


(defrecord
  ^{:doc "
    AppletInfo should contain symbols for a number of functions.

    'label' - a 0-arg function that returns the applet name. Will be displayed on Launcher.

    'description' - a 0-arg function that returns a short description - for tooltip/mouseover on Launcher.

    'icon' - a 2-arg function which returns a ^javafx.scene.Node - to be placed on the launcher button. 'icon ->  (defn icon [width height ] ...

    'main' - a 0-arg function which starts the applet. Called when launcher button is pressed. If it returns a ^javafx.scene.Node, then that node is inserted into the \"detail\" section of the application window.

    'dispose' - a 0-arg fuction called before an applet is unloaded. This should handle saves, de-referencing singletons, etc.
              As with 'main', if an instance of javafx.scene.Node is returned, then that will be displayed.
   "}
  AppletInfo [^Symbol label ^Symbol description ^Symbol icon ^Symbol main ^Symbol dispose])


(defn- verify-applet
  [applet-ns]
  (println "Verifying applet:" applet-ns)
  ;(try
  (require applet-ns)
  (if-let [info-fn (ns-resolve applet-ns 'applet-info)]
    (try
      (let [info (info-fn)]
        (into {} (map
                     #(if-let [f (ns-resolve applet-ns (% info))]
                              [% f]
                              (println (format "  ERROR: Was not able to resolve AppletInfo's '%': %" (name %)(% info))))
                     [:label :description :icon :main :dispose])))
      (catch Exception e (println (format "  ERROR: Calling %s/info failed!  %s" applet-ns  e)) (set! *e e)))
    (println "  ERROR: The applet's 'info' function could not be resolved!")))
;(catch Exception e (println "  ERROR: Loading applet namespace failed!") (.printStackTrace e))


(defn find-applets
  "returns lazy seq of all namespaces matching 'george.application.applet.xyz'"
  []
  (filter
    #(re-find #"george.applet\..+" (str %))
    (find-namespaces (cp/classpath))))


(defn load-applets []
  (let [applet-ns-list (vec (find-applets))
        verified-info-list (vec (map verify-applet applet-ns-list))]
    (filter some? verified-info-list)))