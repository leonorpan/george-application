;; Copyright (c) 2017-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.util.system)


(defn windows? []
  ;(println "defn windows? os.name:" (System/getProperty "os.name"))
  (-> (System/getProperty "os.name") .toLowerCase (->> (re-find #"windows"))  boolean))

(def WINDOWS? (windows?))


(defn mac? []
  (-> (System/getProperty "os.name") .toLowerCase (->> (re-find #"mac"))  boolean))

(def MAC? (mac?))



