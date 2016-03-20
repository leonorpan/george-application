package example;

// import clojure.lang.RT;
import clojure.java.api.Clojure;
import clojure.lang.IFn;
import clojure.lang.ISeq;

public class App {

    public static void main(String[] args) {
        System.out.println("   Java: example.App.main(...): Hello World!");

/*
        // "historical" version
        try {
            RT.loadResourceScript("example/app.clj");
            RT.var("example.app", "-main").invoke(args);
        } catch(Exception e) {
            e.printStackTrace();
        }
*/
        // http://clojure.github.io/clojure/javadoc/
        IFn seqVar = Clojure.var("clojure.core", "seq");
        IFn mainVar = Clojure.var("example.app", "-main");
        mainVar.applyTo((ISeq) seqVar.invoke(args));
    }
}