;; https://github.com/technomancy/leiningen/blob/stable/doc/MIXED_PROJECTS.md


(defproject
  no.andante.george/george-client-jvm
  "0.5.0-SNAPSHOT"

  :description "George Client (source/jvm)"
  :url "http://george.andante.no"
  :license "Copyright 2016 Terje Dahl"

  :plugins [
            [lein-tar "3.2.0"]
            [lein-exec "0.3.6"] ;; https://github.com/kumarshantanu/lein-exec
            ]

:dependencies [
               [org.clojure/clojure "1.8.0"]
               [org.clojure/tools.reader "1.0.0-alpha1"]
               [org.fxmisc.richtext/richtextfx "0.6.10"]
               [org.clojure/core.async "0.2.374"]
               [org.apache.directory.studio/org.apache.commons.io "2.4"]
               ]

    :repositories [
               ["jcenter" "https://jcenter.bintray.com"] ;; apache.commons.io
               ]

        :source-paths ["src/main/clojure"]

        :java-source-paths ["src/main/java"]
        :javac-options     ["-target" "1.8" "-source" "1.8"]

        :test-paths ["src/test/clojure"]
        :resource-paths ["src/main/resources"]

        :target-path "target/%s"

        ;:main example.App
        ;:aot [example.app]

        :aliases {
                  ;; http://www.flyingmachinestudios.com/programming/how-clojure-babies-are-made-lein-run/
                  ;; https://clojure.github.io/clojure/branch-master/clojure.main-api.html#clojure.main/main
                  "example"  ["run" "-m" "example.app" "1 2 3"]
                  "examplej" ["run" "-m" "example.App" "4 5 6"]

                  }

        :profiles {
                   :dev {}
                   }
  )