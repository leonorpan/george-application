package example;

import clojure.lang.RT;

public class App {
    private static final String MAINCLJ = "example/app.clj";

    public static void main(String[] args){
        System.out.println("Java: example.app/main: Hello World!" );
        try {
            RT.loadResourceScript(MAINCLJ);
            RT.var("example.app", "-main").invoke(args);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
