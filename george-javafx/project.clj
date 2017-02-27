(defproject no.andante.george/george-javafx "0.1.3-SNAPSHOT"

  :description "A JavaFX API"
  :url "https://bitbucket.org/andante-george/george-application"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :deploy-repositories [
                        ["snapshots" :clojars :sign-releases false]
                        ["releases" :clojars :sign-releases false]]


  :source-paths      ["src/main/clojure" "src/sample/clojure"]
  :java-source-paths ["src/main/java"]
  :javac-options     ["-target" "1.8" "-source" "1.8"]

  :test-paths ["src/test/clojure"]
  :resource-paths ["src/main/resources"]

  :target-path "target/%s"

  :aliases {
            "sample1" ["run" "-m" "george-javafx-samples" "sample1"]}

  :profiles {:dev {
                   :dependencies [
                                  [org.clojure/clojure "1.8.0"]]}})

