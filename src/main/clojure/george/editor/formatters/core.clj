;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns
  ^{:doc "Imports implementations. Import and call multimethods from this NS."}
  george.editor.formatters.core
  (:require
    [george.editor.formatters.defs]
    [george.editor.state :as st]
    [potemkin :refer [import-vars]]))

(import-vars
  [george.editor.formatters.defs
     formatter tabber])


(require
  '[george.editor.formatters.parinfer :refer :all]
  '[george.editor.formatters.plain :refer :all])



(defn set-formatters_ [state]
  (let [typ (st/content-type_ state)]
    (-> state
        (st/set-formatter_ (formatter typ))
        (st/set-tabber_ (tabber typ)))))