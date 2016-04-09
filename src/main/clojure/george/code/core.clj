(ns
  george.code.core
  (:require
    [george.code.highlight :as highlight]
    :reload
    [george.code.paredit :as paredit]
    :reload
    [george.javafx :as fx])
  (:import [org.fxmisc.richtext StyledTextArea]))


(defn ^StyledTextArea codearea []
    (paredit/set-handlers (highlight/->codearea)))



(defn -main [& args]
  (fx/later
    (let [
          ca
          (doto
              (codearea)
              (highlight/set-text "(foo (bar 1))"))

          scene
          (doto
              (fx/scene (fx/borderpane :center ca :insets 1))
              (fx/add-stylesheets  "styles/codearea.css"))

              stage
          (fx/stage
              :title "george.code.core/-main (test)"
              :scene scene
              :size [600 400]
              )
          ])))



;;; DEV ;;;

;(println "WARNING: Running george.code.core/-main" (-main))