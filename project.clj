(def GEORGE_APPLICATION_VERSION (slurp "src/main/resources/george-version.txt"))


(defproject no.andante.george/george-application  GEORGE_APPLICATION_VERSION

  :description "George - The desktop application (JVM)"
  :url "https://bitbucket.org/andante-george/george-application"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}


  :dependencies [
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.374"]
                 [org.clojure/tools.reader "1.0.0-alpha1"]
                 ;; https://github.com/mmcgrana/clj-stacktrace
                 [clj-stacktrace "0.2.8"]
                 [leiningen "2.7.1" :exclusions [org.clojure/clojure clj-stacktrace]]
                 [org.fxmisc.wellbehaved/wellbehavedfx "0.1.1"]
                 [org.fxmisc.richtext/richtextfx "0.6.10" :exclusions [org.fxmisc.wellbehaved/wellbehavedfx]]
                 [org.apache.directory.studio/org.apache.commons.io "2.4"]
                 ;; https://github.com/clojure/tools.namespace
                 [org.clojure/tools.namespace "0.3.0-alpha3"]
                 ;; https://github.com/clojure/java.classpath
                 [org.clojure/java.classpath "0.2.3"]
                 [org.lpetit/paredit.clj "0.19.3" :exclusions [org.clojure/clojure]]
                 ;; https://github.com/clojure/tools.nrepl
                 [org.clojure/tools.nrepl "0.2.13"]
                 ;; https://github.com/clojure-emacs/cider-nrepl
                 [cider/cider-nrepl "0.14.0"]]
  :plugins [
            ;; https://github.com/weavejester/codox
            [lein-codox "0.10.3"]]

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
  :jvm-opts ["-Dapple.awt.graphics.UseQuartz=true"]  ;; should give crisper text on Mac
  :target-path "target/%s"

  ;; http://www.flyingmachinestudios.com/programming/how-clojure-babies-are-made-lein-run/
  ;; https://clojure.github.io/clojure/branch-master/clojure.main-api.html#clojure.main/main

  :aliases {
            ;; starts turtle environement directly
            "turtle" ["run" "-m" "george.application.applet.turtle"]
            ;; starts general environment directly
            "general" ["run" "-m" "george.application.applet.general"]

            ;; Simple george.example of staring Clojure from Java
            "example" ["run" "-m" "george.example.application" "4 5 6"]
            "examplej" ["run" "-m" "george.example.App" "1 2 3"]

            ;; Test of Clojure and JavaFX performance. See source.
            "stars" ["run" "-m" "george.example.stars"]
            ;; And here is the original Java-version - for (visual) comparison
            "starsj" ["run" "-m" "george.example.Stars"]

            ;; Something cool
            "clocks" ["run" "-m" "george.example.arcclocks"]
            "graph" ["run" "-m" "george.sandbox.graph"]}

  :codox {
          :doc-paths ["docs"]
          :output-path "target/docs"
          :namespaces [george.app.turtle.turtle]
          :source-uri
          ;"https://github.com/weavejester/codox/blob/{version}/codox.example/{filepath}#L{basename}-{line}"
          "https://bitbucket.org/andante-george/george-application/src/default/{filepath}?at=default#{basename}-{line}"
          :html {:namespace-list :flat}}


  :profiles {
             :uberjar {
                       :aot :all
                       :main no.andante.george.Main
                       :manifest {}}}) ;"Main-Class" "no.andante.george.Main"
