(ns george.main)



;(def LAUNCHER_NS "george.launcher")


(defn -main [& args]
  (println "george.main/-main" (if (empty? args)
                                 ""
                                 (str " args: " (apply str (interpose " " args)))))

    (printf "loading  %s ...\n" LAUNCHER_NS)

    (apply
        (ns-resolve
            (doto
                (symbol LAUNCHER_NS)
                (require
                    :verbose
                    :reload
                    )
                )
            '-main)
        args)
    )


;;; DEV ;;;

;(println "  ## WARNING: running george.main/-main from george.main") (-main 1 2 3)