

/* This class is possibly depreciated! */

package george;

import clojure.java.api.Clojure;
import clojure.lang.IFn;
import clojure.lang.RT;
import javafx.application.Application;
import javafx.stage.Stage;

import com.sun.javafx.application.LauncherImpl;

public class Main extends Application {

    @Override
    public void init() throws Exception {
        RT.loadResourceScript("george/main.clj");
        IFn mainVar = Clojure.var("george.main", "-main");
        mainVar.applyTo(RT.seq(getParameters().getRaw()));

        System.out.println("Loading finished");
    }

    @Override
    public void start(Stage primaryStage) throws Exception {

}


    public static void main(String[] args) {
//        launch(args);
        LauncherImpl.launchApplication(Main.class, MainPreloader.class, args);
    }

}





