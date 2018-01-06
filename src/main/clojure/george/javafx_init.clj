;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns
  ^{:docs "This namespace has one purpose: To initialize the JavaFX runtime/toolkit.
  This must be done in order to access certain class information, which may be an issue if you have done any typing in your namespace, i.e. `(let [stage ^Stage ....`
  All that is required is then to 'require' this namespace to ensure that the JavaFX Toolkit is initialized."}

  george.javafx-init)


(defn init-toolkit
  "An easy way to 'initialize [JavaFX] Toolkit'
Needs only be called once in the applications life-cycle.
Has to be called before the first call to/on FxApplicationThread (javafx/later)"
  []
  (println (str *ns*"/init-toolkit"))
  (javafx.embed.swing.JFXPanel.))


(defonce init-done_ (atom false))

(when-not @init-done_
  (init-toolkit)
  (reset! init-done_ true))