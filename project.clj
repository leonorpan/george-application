(defproject no.andante.george/george-application "0.7.2"

  :description "George - The desktop application (JVM)"
  :url "https://bitbucket.org/andante-george/george-application"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}


  :dependencies [
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.374"]
                 [org.clojure/tools.reader "1.0.0-alpha1"]
                 [leiningen "2.7.1" :exclusions [org.clojure/clojure]]
                 [org.fxmisc.wellbehaved/wellbehavedfx "0.1.1"]
                 [org.fxmisc.richtext/richtextfx "0.6.10" :exclusions [org.fxmisc.wellbehaved/wellbehavedfx]]
                 [org.apache.directory.studio/org.apache.commons.io "2.4"]
                 ;; https://github.com/clojure/tools.namespace
                 [org.clojure/tools.namespace "0.3.0-alpha3"]
                 ;; https://github.com/clojure/java.classpath
                 [org.clojure/java.classpath "0.2.3"]
                 [org.lpetit/paredit.clj "0.19.3" :exclusions [org.clojure/clojure]]]

  :plugins [
            ;; https://github.com/kumarshantanu/lein-sub
            [lein-sub "0.3.0"]
            ;; https://github.com/weavejester/codox
            [lein-codox "0.10.3"]]

  :repositories [
                 ["jcenter" "https://jcenter.bintray.com"]] ;; apache.commons.io


  :sub ["george-javafx"]

  :deploy-repositories [
                        ["snapshots" :clojars]
                        ["releases" :clojars]]

  :source-paths      ["src/main/clojure" "george-javafx/src/main/clojure"]
  :java-source-paths ["src/main/java"]
  :javac-options     ["-target" "1.8" "-source" "1.8"]
                      ;"-Xlint:unchecked"]

  :test-paths ["src/test/clojure"]
  :resource-paths ["src/main/resources" "george-javafx/src/main/resources"]

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
