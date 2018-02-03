;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns
  ^{:doc "A deliberately short namespace. Loaded at startup. Making it easy to make calls (to George) from anywhere."}
  g
  (:require
    [clojure.java.io :as cio]
    [george.application.input :as input]
    [george.javafx :as fx]
    [george.javafx.java :as fxj]
    [george.application.output :as output]))



(defmacro turtle-ns
  "Like Clojure's `ns`, but loads basic turtle stuff before loading any of your stuff.
  Intended for use in any code which will be run in/by George.

*Examples:*
```
(ns-turtle my.new.namespace
  (:require [some.namespace :as x])
  (:import [something.special Class1 Class2]))

**Warning!** Cannot be used stead of `ns` in George itself as during AOT compilation `g` is not loaded.
(Also, IDEs may not recognize or parse this construct, and thereby report problems in correct code.)
```"
  [sym & body]
  (concat
    (list 'ns `~sym)
    (concat
      (list
        (list :require 
          ['george.turtle :refer :all] 
          ['clojure.repl :refer :all] 
          ['clojure.pprint :refer ['pprint]]
          ['george.turtle.aux :as 'aux]
          ['george.turtle.tom :as 'tom]
          ['george.turtle.samples :as 'samples]
          ['george.turtle.adhoc.jf4k :as 'jf4k]))
      (list 
        (list :import 
              ['javafx.scene.paint 'Color])) 
      
      body)))

;(user/pprint (macroexpand-1 '(ns-turtle hello.world (:require [user] [g]) (:use [a]) (:import [something Else]))))
;(ns-turtle hello.world (:require [user]))


(defn create-turtle-ns 
  "Similar to Clojure's 'create-ns', but it also set up the namespace as a basic turtle environment.
  The difference from between this function and the 'turtle-ns' macro, is that this function takes a namespace-symbol, and returns the namespace without shifting you into it. Also, while the macro lets you add additional requires and imports, this does not.  
  
  This function is therefore mainly intended for use in other code, for preparing specific namespaces for turtle code, while the macro is intended used at the repl or at the head of turtle code directly.
 
 *Example:*
```
(create-turtle-ns 'my.turtle.namespace)
``` 
  "
  [sym]
  (let [this-ns *ns*] ;; hold on to the current namespace
    (binding [*ns* nil]  ;; allow for switching namespace
      (in-ns sym)
      (refer 'clojure.core)
      (require '[george.turtle :refer :all])
      (require '[clojure.repl :refer :all])
      (require '[clojure.pprint :refer [pprint]])
      (require '[george.turtle.aux :as aux])
      (require '[george.turtle.tom :as tom])
      (require '[george.turtle.samples :as samples])
      (require '[george.turtle.adhoc.jf4k :as jf4k])
      (import '[javafx.scene.paint Color])
      (in-ns (ns-name this-ns)))  ;; return to the current namespace
    (find-ns sym)))  ;; test and return the newly created namespace


(defn fullscreen 
  "Do `(g/fullscreen)` to make George fullscreen."
  [& [fullscreen?]]
  (eval (read-string (format "(george.javafx/later (.setFullScreen (george.application.launcher/current-application-stage)  %s))" (if (nil? fullscreen?) true fullscreen?)))))  


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
              (output/oprint :err (format "Resource not found for 'shared-key':  %s\n" shared-key))))))
      (catch Exception e
        (binding [*out* *err*]
          (output/oprint :err (format "Exception: 'shared-key' not string or keyword:  %s\n" (if (nil? shared-key) "nil" shared-key))))))))

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
      (let [r   (slurp-shared shared-key throw-exception?)
            is  (input/new-input-stage ns)])))
        ;(fx/later
        ;  (george.editor.core/set-text code-area (if r r (format shared-error-message-f shared-key)))))))
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


;(println "Namespace 'g' gives you access to \"global\" commands to George.  For more info, do:  (g/help)")
