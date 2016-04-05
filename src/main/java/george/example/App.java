package george.example;

import clojure.lang.RT;
import clojure.java.api.Clojure;
import clojure.lang.IFn;

public class App {

    public static void main(String[] args) {
        System.out.println("   Java: george.example.App.main(...): Hello World!");

        try {
            RT.loadResourceScript("george/example/app.clj");
            IFn mainVar = Clojure.var("george.example.app", "-main");
            mainVar.applyTo(RT.seq(args));
        } catch(Exception e) {
            e.printStackTrace();
        }

    }
}