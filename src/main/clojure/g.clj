;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns
  ^{:doc "A deliberately short namespace. Loaded at startup. Making it easy to make calls (to George) from anywhere."}
  g
  (:require
    [environ.core :refer [env]]))

(defn hi []
  (println "Hello, yourself."))

