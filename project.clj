;; https://github.com/technomancy/leiningen/blob/stable/doc/MIXED_PROJECTS.md


(defproject no.andante.george/george-app-jvm "0.6.0-SNAPSHOT"

  :description "George Client (source/jvm)"
  :url "http://george.andante.no"
  :license "Copyright 2016 Terje Dahl"

  :plugins [
            [lein-tar "3.2.0"]]
            ;[lein-exec "0.3.6"] ;; https://github.com/kumarshantanu/lein-exec


  :dependencies [
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.reader "1.0.0-alpha1"]
                 [org.fxmisc.wellbehaved/wellbehavedfx "0.2"]
                 [org.fxmisc.richtext/richtextfx "0.6.10"  :exclusions [org.fxmisc.wellbehaved/wellbehavedfx]]
                 [org.clojure/core.async "0.2.374"]
                 [org.apache.directory.studio/org.apache.commons.io "2.4"]

                 ;; https://github.com/clojure/tools.namespace
                 [org.clojure/tools.namespace "0.3.0-alpha3"]
                 ;; https://github.com/clojure/java.classpath
                 [org.clojure/java.classpath "0.2.3"]
                 ;[org.kovas/paredit.clj "0.20.1-SNAPSHOT" :exclusions [org.clojure/clojure]]  ;; https://github.com/kovasb/paredit-widget
                 [org.lpetit/paredit.clj "0.19.3" :exclusions [org.clojure/clojure]]
                 [no.andante.george/george-javafx "0.1.0-SNAPSHOT"]]


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

  :target-path "target/%s"

  ;; http://www.flyingmachinestudios.com/programming/how-clojure-babies-are-made-lein-run/
  ;; https://clojure.github.io/clojure/branch-master/clojure.main-api.html#clojure.main/main

  :aliases {
            ;; starts the Loader, which in turn starts Starter, which in turn starts main.
            "loader" ["run" "-m" "george.app.Loader"]
            ;; starts Starter, which in turn starts main.
            "starter" ["run" "-m" "george.app.Starter"]
            ;; starts main directly
            "main" ["run" "-m" "george.app.main"]


            ;; Simple george.example of staring Clojure from Java
            "example" ["run" "-m" "george.example.app" "4 5 6"]
            "examplej" ["run" "-m" "george.example.App" "1 2 3"]

            ;; Test of Clojure and JavaFX performance. See source.
            "stars" ["run" "-m" "george.example.stars"]
            ;; And here is the original Java-version - for (visual) comparison
            "starsj" ["run" "-m" "george.example.Stars"]

            "graph" ["run" "-m" "george.sandbox.graph"]}

  :profiles {
             :uberjar {
                       ;:aot :all
                       ;:main george.app.Loader}})
                       :manifest {"Main-Class" "george.app.Loader"}}})
