;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns
    ^{:author "Terje Dahl"}
    george.application.environment
    (:require
        [clojure.java.io :refer [file] :as cio]
        [george.javafx :as fx]
        [george.application.turtle.turtle :as tr]
        [george.core.core :as gcc]
        [george.util.singleton :as singleton]
        [george.application.code :as code]))



(defn- prep-user-turtle-ns []
  (let [current-ns (:ns (meta #'prep-user-turtle-ns))]
    (binding [*ns* nil]
      ;; prep a user namespace
      (ns user.turtle (:require [george.application.turtle.turtle :refer :all]))
      ;; switch back to this namespace
      (ns current-ns))))




(defn- toolbar-pane [is-turtle]

   (when is-turtle
     (prep-user-turtle-ns))

   (let [button-width
         150

         user-ns-str
         (if is-turtle "user.turtle" "user")

         pane
         (apply
           fx/hbox
           (filter
             some?
             (list
                 (when is-turtle
                      (fx/button "Screen"
                                 :width button-width
                                 :onaction #(tr/screen)
                                 :tooltip "Open/show the Turtle screen"))

                 (fx/button "Input"
                            :width button-width
                            :onaction
                            #(gcc/new-input-stage user-ns-str)
                            :tooltip "Open a new input window / REPL")

                 (fx/button "Output"
                            :width button-width
                            :onaction gcc/show-or-create-output-stage
                            :tooltip "Open/show output-window")

                 (fx/button "Code"
                            :width button-width
                            :onaction #(code/new-code-stage :namespace user-ns-str)
                            :tooltip "Open a new code editor")

                 :spacing 10
                 :padding 10)))]
     pane))


(defn- create-toolbar-stage [ide-type]
  (println "  #!")
  (let [is-turtle (= ide-type :turtle)]
    (fx/now
      (fx/stage
        :location [520 17]
        :title (if is-turtle "Turtle Geometry" "IDE")
        :scene (fx/scene (toolbar-pane is-turtle))
        :sizetoscene true
        :resizable false
        :onhidden #(singleton/remove [::toolbar-stage ide-type])))))



(defn toolbar-stage [ide-type]
    (singleton/put-or-create
      [::toolbar-stage ide-type] #(create-toolbar-stage ide-type)))


;;;; main ;;;;

(defn -main
  "Launches an input-stage as a stand-alone application."
  [& args]
  (let [ide-type (#{:ide :turtle} (first args))]
    (fx/later (toolbar-stage ide-type))))


;;; DEV ;;;

;(println "WARNING: Running george.application.turtle.environment/-main" (-main))
