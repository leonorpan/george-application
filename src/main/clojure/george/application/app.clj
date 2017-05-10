;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.


(ns george.application.app
  (:import (clojure.lang Symbol)))


(defrecord AppInfo [^String name
                    ;; The app name. Will be desplayed on launcher.
                    ^String description
                    ;; Short description - for tooltip/mouseover on launcher.
                    ^Symbol icon-fn
                    ;; Symbol for a 2-arg function which returns a ^javafx.scene.Node - to be placed on the launcher button. 'icon ->  (defn icon [width height ] ...
                    ^Symbol main-fn])
                    ;; Symbol for a 0-arg function which starts the app. Called when launcher button is pressed.