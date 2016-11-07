(ns
    ^{:author "Terje Dahl"}
    george.app.turtle.environment
    (:require
        [clojure.java.io :refer [file] :as cio]
        [george.javafx.core :as fx]
        [george.app.turtle.turtle :as tr]
        [george.core.core :as gcc]
        [george.util.singleton :as singleton]
        [george.util.prefs :as prf]
        [george.app.code :as code] :reload)
    (:import (java.util.prefs Preferences)
             (java.io File)))


(defonce ^Preferences USER_PREFS (prf/user-node "no.andante.george.turtle"))
(defonce ^String USER_HOME_STR (System/getProperty "user.home"))
(defonce ^File DEFAULT_LIBRARY (file USER_HOME_STR "George" "Turtle" "Library"))


(defn- get-library []
    (file (prf/get USER_PREFS ::turtle-library-path DEFAULT_LIBRARY)))

(defn- put-library [p]
    (prf/put USER_PREFS ::turtle-library-path p))



(defn- poll-library [lib-dir listview]
    (println "poll library ...")
    (let [
          files (file-seq lib-dir)
          files (.listFiles (file (System/getProperty "user.home")))
          files (.listFiles lib-dir)]


;        (doseq [f files] (println "f:"f))
        (.setItems listview (apply fx/observablearraylist files))))



(defn- library-pane []
    (let [
          lib-dir (get-library)

          listview
          (doto (fx/listview)
              (.setPlaceholder (fx/text "(no files found)")))

          refresh-button
          (fx/button "R"
                     :onaction #(poll-library lib-dir listview)
                     :tooltip "Refresh list of files")


          top(fx/hbox
                   (fx/label
                       (str lib-dir))
                   (fx/region :hgrow :always)
                   refresh-button
                   :spacing 3
                   :alignment fx/Pos_TOP_RIGHT
                   :insets [0 0 5 0])


          pane (fx/borderpane :top top :center listview :insets 5)]

        (.fire refresh-button)

        pane))



(defn- create-library-stage []
    (let []

        (fx/now
            (fx/stage
                :style :utility
                :location [70 80]
                :title "Turtle Geometry - library"
                :scene (fx/scene (library-pane) :size [300 300])
                :sizetoscene true
                :onhidden #(singleton/remove ::library-stage)))))




(defn library-stage []
    (singleton/put-or-create ::library-stage create-library-stage))


(defn- prep-user-ns []
  (let [current-ns (:ns (meta #'prep-user-ns))]
    (binding [*ns* nil]
      ;; prep a user namespace
      (ns user.turtle (:require [george.app.turtle.turtle :refer :all]))
      ;; switch back to this namespace
      (ns current-ns))))




(defn- toolbar-pane []
    (prep-user-ns)
    (let [button-width
          150

          pane (fx/hbox

                   (fx/button "Screen"
                              :width button-width
                              :onaction #(tr/screen)
                              :tooltip "Open/show the Turtle screen")

                   (fx/button "Code"
                              :width button-width
                              :onaction #(code/new-code-stage :namespace "user.turtle")
                              :tooltip "Open a new code editor")

                   (fx/button "Output"
                              :width button-width
                              :onaction gcc/show-or-create-output-stage
                              :tooltip "Open/show output-window")

                 ;(fx/button "Library"
                 ;           :width button-width
                 ;           :onaction #(library-stage)
                 ;           :tooltip "Open/show the library navigator (your files)")

                 ;  (fx/button "Input"
                 ;             :width button-width
                 ;             :onaction #(gcc/new-input-stage "user.turtle")
                 ;             :tooltip "Open a new input window / REPL")


                 ;(fx/button "Editor"
                 ;           :width button-width
                 ;           :onaction #(editor/new-code-stage :namespace "user.turtle")
                 ;           :tooltip "Open a new code editor"


                 ;(fx/button "Commands"
                 ;           :width button-width
                 ;           :onaction #(println "missing IMPL (Commands)")
                 ;           :tooltip "Open/show a panel with useful turtle commands")

                 :spacing 10
                 :padding 10)]

      pane))


(defn- create-toolbar-stage []
  (let []

    (fx/now
      (fx/stage
        :style :utility
        :ontop true
        :location [300 17]
        :title "George - Turtle Geometry"
        :scene (fx/scene (toolbar-pane))
        :sizetoscene true
        :onhidden #(singleton/remove ::toolbar-stage)))))



(defn toolbar-stage []
    (singleton/put-or-create ::toolbar-stage create-toolbar-stage))


;;;; main ;;;;

(defn -main
  "Launches an input-stage as a stand-alone app."
  [& args]
  (fx/later (toolbar-stage)))


;;; DEV ;;;

;(println "WARNING: Running george.app.turtle.environment/-main" (-main))
