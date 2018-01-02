;; Copyright (c) 2017-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.util.time
  (:require
    [clj-time.core :as t]))


(defn now []
  (t/now))


;; https://en.wikipedia.org/wiki/ISO_8601

(defn now-iso ;; -extended
  "Returns a str version of 'now' (UTC) - yyyy-mm-ddThh:mm:ss[Z|[+-]hh:mm].
  Adherent to JS standards.  (seconds in time, minutes in offset)"
  []
  (let [n (t/now)]
    ;; Not beautifully implemented, but it works for now.
    (format "%s-%02d-%02dT%s:%02d:%02dZ" (t/year n) (t/month n) (t/day n) (t/hour n) (t/minute n) (t/second n))))

;(prn (now-iso))


(defn now-iso-basic
  "Similar to 'now-iso', but without problematic colons, making it file-safe, but less legible."
  []
  (let [n (t/now)]
    (format "%s%02d%02dT%s%02d%02dZ" (t/year n) (t/month n) (t/day n) (t/hour n) (t/minute n) (t/second n))))

;(prn (now-iso-basic))
