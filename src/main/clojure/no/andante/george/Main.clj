;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns no.andante.george.Main
  (:require
    [george.javafx :as fx]
    [george.javafx.java :as fxj]
    [george.application.launcher :as launcher])
  (:import
    [javafx.application Preloader$ProgressNotification])

  (:gen-class
    :main true
    :name no.andante.george.Main
    :extends javafx.application.Application
    :implements [no.andante.george.IStageSharing]))


(def WITH_PRELOADER_ARG "--with-preloader")

(def state_ (atom {}))


(defn -handover
  "Implements interface IStageSharing (via gen-class).
  Is called by the preloader.
  The preloader hands over a stage which is then passed on to launcher/start,
  and where it is morphed into the main application window."
  [this stage]
  ;(println "/-handover")
  (swap! state_ assoc :handover-done? true)
  (fxj/thread (launcher/start stage (:root @state_))))


(defn -init [this]
  ;(println "no.andante.george.Main/-init")
  (fxj/thread
    (dotimes [i 50]
      (.notifyPreloader this (Preloader$ProgressNotification. (+ 0.0 (* 0.02 i))));
      (Thread/sleep 50))
    (.notifyPreloader this (Preloader$ProgressNotification. 1.0)));

  (let [root (launcher/application-root)]
    (swap! state_ assoc :root root)))


(defn -start [this ^javafx.stage.Stage stage]
  ;(println "no.andante.george.Main/-start args:" (-> this .getParameters .getRaw seq))
  ;(println "  ## @state_:" @state_)

  (when-not (:handover-done? @state_)
    (swap! state_ assoc :root (launcher/application-root))
    (-handover this (launcher/starting-stage stage))))


(defn -stop [this])
  ;(println "no.andante.george.Main/-stop"))


(defn- main [& args]
  (println "::main args:" args)
  (javafx.application.Application/launch  ;; DON'T IMPORT! IT WILL BREAK.
    no.andante.george.Main
    (into-array String args)))


(defn- main-with-preloader [& args]
  ;(println "::main-with-preloader args:" args)
  (try
    ;; Calling this class directly isn't safe, as it might change in future Java versions.
    ;; But since George is distributed as a native install with Java RT included, we have control.
    (com.sun.javafx.application.LauncherImpl/launchApplication ;; DON'T IMPORT! IT MAY BREAK.
      no.andante.george.Main
      no.andante.george.MainPreloader
      (into-array String args))

    (catch Exception e
           (.printStackTrace e))))
           ;(apply main args))))


(defn -main
  "Launches gen-class 'no.andante.george.Main' as JavaFX Application.

  If passed argument --with-preloader (in Clojure), then triggers JavaFX mechanism and first loads no.andante.george.MainPreloader. This is a way of testing the preloader.

  When running JAR, preloader will be run irrespective based on Manifest,
  and so '--with-preloader' has no effect either way."
  [& args]
  (if (some #(= % WITH_PRELOADER_ARG) args)
    (apply main-with-preloader args)
    (apply main args)))
