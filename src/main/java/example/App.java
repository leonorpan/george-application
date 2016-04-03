package example;

import clojure.lang.RT;
import clojure.java.api.Clojure;
import clojure.lang.IFn;

public class App {

    public static void main(String[] args) {
        System.out.println("   Java: example.App.main(...): Hello World!");

        try {
            RT.loadResourceScript("example/app.clj");
            IFn mainVar = Clojure.var("example.app", "-main");
            mainVar.applyTo(RT.seq(args));
        } catch(Exception e) {
            e.printStackTrace();
        }

    }
}