;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns no.andante.george.Main

  (:gen-class
    :main true
    :name no.andante.george.Main
    :extends javafx.application.Application)

  (:require [george.application.launcher :as launcher]))


(def WITH_PRELOADER_ARG "--with-preloader")



(defn -start [this ^javafx.stage.Stage stage]
  (println "no.andante.george.Main/-start")
  (println "params are:" (-> this .getParameters .getRaw seq))

  (launcher/start stage))


(defn -stop [this]
  (println "no.andante.george.Main/-stop"))


(defn- main [& args]
  (println "::main args:" args)
  (javafx.application.Application/launch no.andante.george.Main (into-array String args)))


(defn- main-with-preloader [& args]
  ;(println "::main-with-preloader args:" args)
  (try
    (com.sun.javafx.application.LauncherImpl/launchApplication
      no.andante.george.Main
      no.andante.george.MainPreloader
      (into-array String args))

    (catch Exception e
           (.printStackTrace e)
           (apply main args))))


(defn -main
  "Launches gen-class 'no.andante.george.Main' as JavaFX Application.

  If passed argument --with-preloader (in Clojure), then triggers JavaFX mechanism and first loads no.andante.george.MainPreloader. This is a way of testing the preloader.

  When running JAR, preloader will be run irrespective based on Manifest,
  and so '--with-preloader' has no effect either way."
  [& args]
  (if (some #(= % WITH_PRELOADER_ARG) args)
    (apply main-with-preloader args)
    (apply main args)))


;;;; DEV

;(do (println "WARNING! Running no.andante.george.Main/-main")(-main))
;(do (println "WARNING! Running no.andante.george.Main/-main")(-main WITH_PRELOADER_ARG))
;(do (println "WARNING! Running no.andante.george.Main/main-with-preloader")(main-with-preloader))