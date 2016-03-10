(ns boot.user
    (:require
        [boot.util :as util]
        [boot.core :refer :all]
        [clojure.java.io :as io]))

;(def project 'andante.no/george-client-jvm)
(def +version+ "0.0.1")
(def main-class "example.App")

;; what does this actually do?  (other than supress warning)
(System/setProperty "BOOT_EMIT_TARGET" "no")
(System/setProperty "BOOT_CLOJURE_VERSION" "1.8.0")

(set-env!
    :source-paths   #{"src/main/java" "src/main/clojure" }
    :resource-paths #{"src/main/java" "src/main/clojure" "src/main/resources"}

    :dependencies '[
;        [org.clojure/clojure "1.8.0"]
;        [jline/jline "0.9.94"]
;        [org.fxmisc.richtext/richtextfx "0.6.10"]
;        [org.clojure/tools.reader "1.0.0-alpha1"]
;        [org.apache.directory.studio/org.apache.commons.io "2.4"]
;        [org.clojure/core.async "0.2.374"]
    ;    [cpmcdaniel/boot-copy "1.0" :scope "provided"]
        [cpmcdaniel/boot-with-pom "1.0" :scope "provided"]

    ]
)

(require '[cpmcdaniel.boot-with-pom :refer :all])


(task-options!
;    pom {
;
;            :project       'no.andante.george/george-client-jvm
;            :version        +version+
;            :description   "George Client (source/jvm)"
;            :url           "http://george.andante.no"
;            ; :scm           {kw str}     The project scm map (KEY is one of url, tag, connection, developerConnection).
;            :license       {"Eclipse Public License 1.0 ?" "http://www.eclipse.org/legal/epl-v10.html"}
;            :developers    {"Terje Dahl" "terje@andante.no"}
;            ; :dependencies   dependencies ;; uses environment dependencies
;         }

    target {:dir #{"target"}}
    jar {
            :manifest
            {
                "Implementation-Title" "George Client - jvm"
                "Implementation-Version" +version+
                "Main-Class" main-class
                }

            :file "some-jar-name.jar"
    }
    uber {:exclude-scope #{"provided"}}
)


;(defn- copy-pom []
;    (with-pre-wrap fileset
;       ; (doseq [f fileset] (println "f:" f))
;    (let [
;             in-dir (io/file "META-INF" "maven" "no.andante.george" "george-client-jvm")
;             out-dir (System/getProperty "user.dir")
;             files ["pom.xml" "pom.properties"]
;
;             ]
;        (doseq [f files]
;            (let [
;                    in-file (io/file in-dir f)
;                    out-file (doto (io/file out-dir f) io/make-parents)
;                ]
;                (util/info "Copying %s to %s ...\n" in-file out-dir)
;                (io/copy in-file out-file))))
;
;        fileset))


;(deftask update-pom
;    "Emits an updated pom-file to the target directory - mainly for easy IDE sync"
;    []
;    (comp
;        (pom)
;        (target)
;        ;(copy :output-dir (System/getProperty "user.dir") :matching #{ #".*pom\..+$" })
;        ;(doto (io/file out-dir path) io/make-parents)
;        ;(io/copy in-file out-file)
;        (copy-pom)
;        ))


(deftask example
    "Compiles (.java only) and runs a simple Java/Clojure sample program. (Text output only)"
    []


        ;(javac :options  ["-d" "src/main/java"])
    (comp
        (javac)
        (uber
            :include #{#"clojure.*" #"app.clj$" #"App.java$"}
            :exclude #{#"dev/.*" #"george/.*" #".*fonts/.*" #"/style.*"}
            )
        (jar :file "example.jar"  :main 'example.App)
        (target :dir #{"build/example"} :no-clean false)
        ;(example.App/main (into-array Object [4 5 "six"]))
        )



    )


(deftask build
    "Build my project"
    []
    (comp (javac) (with-pom) (uber) (jar) (target)))