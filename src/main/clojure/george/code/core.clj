;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns
  george.code.core
  (:require
     [george.code.highlight :as highlight]
     [george.code.codearea :as ca]
     [george.code.paredit :as paredit]
     [george.javafx :as fx]
     [george.util.css :as css]
     [george.util :as u])
  (:import [org.fxmisc.richtext StyledTextArea CodeArea]
           (javafx.scene.input KeyEvent)))







