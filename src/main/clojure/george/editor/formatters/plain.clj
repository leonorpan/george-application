;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.editor.formatters.plain
  (:require
    [george.editor.formatters.defs :as defs]
    [george.editor.state :as st]))


(defmethod defs/formatter :default [_]
  (fn [state & _]
    state))


(defmethod defs/tabber :default [_]
  (fn [state & _]
    [(st/keytyped_ state \tab) nil]))
