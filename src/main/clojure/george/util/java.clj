;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.util.java
  (:import (java.util.function Function Consumer)))


(defn ^Function function
  "takes a 1-arg function and wraps it in a java.function.Function"
  [f]
  (reify Function
    (apply [_ arg]
      (f arg))))

(defn ^Consumer consumer
  "takes a 1-arg function and wraps it in a java.function.Consumer"
  [f]
  (reify Consumer
    (accept [_ arg]
      (f arg))))
