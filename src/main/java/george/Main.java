package george;

import clojure.java.api.Clojure;
import clojure.lang.IFn;
import clojure.lang.RT;


public class Main {

    public static void main(String[] args) {

        System.out.println("george.Main: Loading george.main ...");

        try {
            RT.loadResourceScript("george/main.clj");
            IFn mainVar = Clojure.var("george.main", "-main");
            mainVar.applyTo(RT.seq(args)); }
        catch(Exception e) {
            e.printStackTrace(); }}}





