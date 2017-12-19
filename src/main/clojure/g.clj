;  Copyright (c) 2017 Terje Dahl. All rights reserved.
; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;  You must not remove this notice, or any other, from this software.

(ns
  ^{:doc "A deliberately short namespace. Loaded at startup. Making it easy to make calls (to George) from anywhere."}
  g
  (:require
    [clojure.java.io :as cio]
    [george.application.input :as input]
    [george.javafx :as fx]
    [george.javafx.java :as fxj]
    [george.application.output :as output]))



(defn hi []
  (println "Hello, yourself."))


(defn slurp-shared
  "Fetches and returns a resource (file or folder) that has been shared (on George Server).
  'shared-key' is a string or keyword uniquely identifying the given resource.
  If the resource is not found or id is not a string or keyword,
  then a message is printed to *err* and 'nil' is returned.
  If optional 'throw-exceptions?' is true, then exceptions will not be handled.
  Ex.:  (g/slurp-shared :uX3)  or (g/slurp-shared \"uX3\")
  "

  [shared-key & [throw-exceptions?]]
  (if throw-exceptions?
    (let [k (name shared-key)
          pth (str "shared/" k)]
        (slurp (cio/resource pth)))
    (try
      (let [k (name shared-key)]
        (try
          (let [pth (str "shared/" k)]
            (slurp (cio/resource pth)))
          (catch IllegalArgumentException e
            (binding [*out* *err*]
              (output/print-output :err (format "Resource not found for 'shared-key':  %s\n" shared-key))))))
      (catch Exception e
        (binding [*out* *err*]
          (output/print-output :err (format "Exception: 'shared-key' not string or keyword:  %s\n" (if (nil? shared-key) "nil" shared-key))))))))

;(println (slurp-shared :tree2))
;(println (slurp-shared :tree3))
;(println (slurp-shared :tree3 true))
;(println (slurp-shared 42))
;(println (slurp-shared 42 true))
;(println (slurp-shared nil true))
;(println (slurp-shared nil))

(def ^:private shared-error-message-f
  ";; Nothing loaded!
;; Is 'shared-key' correct?:  %s
;; Open \"Output\" and try again.")


(defn shared
  "Same as 'slurp-shared', but opens the resource in a file-browser."
  [shared-key & [throw-exception?]]
  (let [ns (str *ns*)]
    (fxj/thread
      (let [r (slurp-shared shared-key throw-exception?)
            [_ code-area] (input/new-input-stage ns)]
        (fx/later
          (george.editor.core/set-text code-area (if r r (format shared-error-message-f shared-key)))))))
  nil)


(defn help
  "Prints some basic help for this namespace."
  []
  (println "
# Help for namespace: g

This namespace has the following items pre-loaded:
  hi       Say \"hi\" to George.
  shared   Loads content for a 'shared-key' into a new Input window.

To list all available commands, do:  (user/dir g)

To view documentation, do:  (user/doc <function-or-macro>)
Ex.: (user/doc g/shared)
"))


(println "Namespace 'g' gives you access to \"global\" commands to George.  For more info, do:  (g/help)")
