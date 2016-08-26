

/* This class is possibly depreciated! */

package george.app;

import clojure.java.api.Clojure;
import clojure.lang.IFn;
import clojure.lang.RT;
import com.sun.javafx.application.LauncherImpl;
import javafx.application.Application;
import javafx.stage.Stage;

public class Starter extends Application {

    @Override
    public void init() throws Exception {
        System.out.println("george.app.Starter.init");

        RT.loadResourceScript("george/app/main.clj");
        IFn mainVar = Clojure.var("george.app.main", "-main");
        mainVar.applyTo(RT.seq(getParameters().getRaw()));
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        System.out.println("george.app.Starter.start");
}


    public static void main(String[] args) {
        System.out.println("george.app.Starter.main");
//        launch(args);
        LauncherImpl.launchApplication(Starter.class, StarterPreloader.class, args);
    }

}





