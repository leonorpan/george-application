
(defproject no.andante.george/george-application  "2018.0-b1-SNAPSHOT"

  :description "George - Application"
  :url "https://bitbucket.org/andante-george/george-application"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}


  :dependencies [[org.clojure/clojure "1.8.0"]
                 ;; https://github.com/clojure/core.async
                 [org.clojure/core.async "0.3.465"]
                 ;; https://github.com/clojure/tools.reader
                 [org.clojure/tools.reader "1.0.0-alpha1"]
                 ;; https://github.com/mmcgrana/clj-stacktrace
                 [clj-stacktrace "0.2.8"]
                 ;[leiningen "2.8.1" :exclusions [org.clojure/clojure clj-stacktrace]]
                 [org.apache.directory.studio/org.apache.commons.io "2.4"]
                 ;; https://github.com/clojure/tools.namespace
                 [org.clojure/tools.namespace "0.3.0-alpha3"]
                 ;; https://github.com/clojure/java.classpath
                 [org.clojure/java.classpath "0.2.3"]
                 ;; https://github.com/ccw-ide/ccw/tree/master/paredit.clj
                 [org.lpetit/paredit.clj "0.19.3" :exclusions [org.clojure/clojure]]
                 ;; https://github.com/clojure/tools.nrepl
                 [org.clojure/tools.nrepl "0.2.13"]
                 ;; https://github.com/clojure-emacs/cider-nrepl
                 [cider/cider-nrepl "0.14.0"]
                 ;; https://github.com/FXMisc/RichTextFX
                 [org.fxmisc.richtext/richtextfx "0.7-M5"]
                 ;; https://github.com/TomasMikula/Flowless
                 [org.fxmisc.flowless/flowless  "0.6"]
                 ;; https://github.com/brentonashworth/clj-diff
                 [clj-diff "1.0.0-SNAPSHOT"]
                 ;; https://github.com/clojure/core.rrb-vector
                 [org.clojure/core.rrb-vector "0.0.11"]
                 ;; https://github.com/clojure/data.json
                 [org.clojure/data.json "0.2.6"]
                 ;; https://github.com/weavejester/environ
                 [environ "1.1.0"]
                 ;; https://github.com/ztellman/potemkin
                 [potemkin "0.4.4"]]
                 ;; https://github.com/alexander-yakushev/defprecated]
                 ; [defprecated "0.1.3"] :exclusions [org.clojure/clojure]]

  :plugins [
            ;; https://github.com/weavejester/environ
            [lein-environ "1.1.0"]
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
            "preloader"
            ^{:doc "
  Triggers the JavaFX preloader mechanism to run 'no.andante.george.MainPreloader'.
  All args are passed through to main application.
  Note: The preloader won't appear as fast as when triggered by a normal JAR launch."}
            ["run" "-m" "no.andante.george.Main" "--with-preloader"]

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
          :namespaces [george.application.turtle.turtle]
          :source-uri
          ;"https://github.com/weavejester/codox/blob/{version}/codox.example/{filepath}#L{basename}-{line}"
          "https://bitbucket.org/andante-george/george-application/src/default/{filepath}?at=default#{basename}-{line}"
          :html {:namespace-list :flat}}

  :profiles {:repl {:env {:repl? "true"}}
             :uberjar {:aot :all
                       :manifest {"Main-Class" "no.andante.george.Main"
                                  "JavaFX-Preloader-Class" "no.andante.george.MainPreloader"
                                  "JavaFX-Application-Class" "no.andante.george.Main"}}})
