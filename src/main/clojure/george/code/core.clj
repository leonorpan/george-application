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
    (paredit/set-handlers! (highlight/codearea)))



(defn -main [& args]
  (fx/later
    (let [
          ca
          (doto
              (codearea)
              (paredit/set-handlers!)
              (highlight/set-text "(foo (bar 1))")
              )

          scene
          (fx/scene(fx/borderpane :center ca :insets 1))
          stage
          (fx/stage
              :title "george.code.core/-main (test)"
              :scene scene
              :size [600 400]
              )
          ])))



;;; DEV ;;;

;(-main)