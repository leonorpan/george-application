;; TODO:
; - Quit-dialog needs to come to forefront of *all* applications.


;; https://github.com/technomancy/leiningen/blob/stable/doc/MIXED_PROJECTS.md


(defproject no.andante.george/george-application "0.7.1-SNAPSHOT"

  :description "George - the desktop application - JVM version"
  :url "https://bitbucket.org/andante-george/george-application"
    :license {:name "Eclipse Public License"
              :url "http://www.eclipse.org/legal/epl-v10.html"}

  :plugins [
            [lein-tar "3.2.0"]]
            ;[lein-exec "0.3.6"] ;; https://github.com/kumarshantanu/lein-exec


  :dependencies [
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.374"]
                 [org.clojure/tools.reader "1.0.0-alpha1"]
                 [leiningen "2.7.1" :exclusions [org.clojure/clojure]]
                 ;[org.fxmisc.wellbehaved/wellbehavedfx "0.2"]
                 [org.fxmisc.richtext/richtextfx "0.6.10"]
                  ;:exclusions [org.fxmisc.wellbehaved/wellbehavedfx]]
                 [org.apache.directory.studio/org.apache.commons.io "2.4"]

                 ;; https://github.com/clojure/tools.namespace
                 [org.clojure/tools.namespace "0.3.0-alpha3"]
                 ;; https://github.com/clojure/java.classpath
                 [org.clojure/java.classpath "0.2.3"]
                 ;[org.kovas/paredit.clj "0.20.1-SNAPSHOT" :exclusions [org.clojure/clojure]]  ;; https://github.com/kovasb/paredit-widget
                 [org.lpetit/paredit.clj "0.19.3" :exclusions [org.clojure/clojure]]
                 [no.andante.george/george-javafx "0.1.2"]]


  :repositories [
                 ["jcenter" "https://jcenter.bintray.com"]] ;; apache.commons.io


  :deploy-repositories [
                          ["snapshots" :clojars]
                        ["releases" :clojars]]


  :source-paths      ["src/main/clojure"]
  :java-source-paths ["src/main/java"]
  :javac-options     ["-target" "1.8" "-source" "1.8"]
                      ;"-Xlint:unchecked"]


  :test-paths ["src/test/clojure"]
  :resource-paths ["src/main/resources"]

  :main no.andante.george.Main
  :aot [no.andante.george.Main]

  :target-path "target/%s"

  ;; http://www.flyingmachinestudios.com/programming/how-clojure-babies-are-made-lein-run/
  ;; https://clojure.github.io/clojure/branch-master/clojure.main-api.html#clojure.main/main

  :aliases {
            ;; starts turtle environement directly
            "turtle" ["run" "-m" "george.app.applet.turtle"]
            ;; starts general environment directly
            "general" ["run" "-m" "george.app.applet.general"]

            ;; Simple george.example of staring Clojure from Java
            "example" ["run" "-m" "george.example.app" "4 5 6"]
            "examplej" ["run" "-m" "george.example.App" "1 2 3"]

            ;; Test of Clojure and JavaFX performance. See source.
            "stars" ["run" "-m" "george.example.stars"]
            ;; And here is the original Java-version - for (visual) comparison
            "starsj" ["run" "-m" "george.example.Stars"]

            ;; Something cool
            "clocks" ["run" "-m" "george.example.arcclocks"]

            "graph" ["run" "-m" "george.sandbox.graph"]}

  :profiles {
             :uberjar {
                       :aot :all
                       ;:main george.app.Loader}})
                       :manifest {"Main-Class" "no.andante.george.Main"}}})

;; TODO next:
;; - consolidate environments.
;;   - look at conections between input and output.
;;   - ensure correct *ns* for turtle vs general.
;; - Fix Launcher and Environment layouts - choose best layout and placement of turtle/general and use that.
;; - avoid ":reload" of namespaces  (multiple reloads seems to slow down loading, and causes multiple calls to other parts)