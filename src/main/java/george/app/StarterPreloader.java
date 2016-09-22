// http://stackoverflow.com/questions/15126210/how-to-use-javafx-preloader-with-stand-alone-application-in-eclipse
// https://docs.oracle.com/javase/8/docs/technotes/guides/deploy/preloaders.htm

package george.app;

import javafx.animation.FadeTransition;
import javafx.application.Preloader;
import javafx.application.Preloader.StateChangeNotification.Type;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.util.Duration;


public class StarterPreloader extends Preloader {

    private Stage stage;
    private ProgressBar bar;





    private Scene createPreloaderScene() {
        bar = new ProgressBar();
        BorderPane p = new BorderPane();
        p.setCenter(bar);
        return new Scene(p, 300, 150);
    }


    @Override
    public void start(Stage stage) throws Exception {
        System.out.println("george.app.StarterPreloader.start");
        this.stage = stage;
        stage.setTitle("Loading George ...");
        stage.setScene(createPreloaderScene());
        stage.show();
    }


    @Override
    public void handleStateChangeNotification(StateChangeNotification stateChangeNotification) {
        if (stateChangeNotification.getType() == Type.BEFORE_START) {

                //fade out, hide stage at the end of animation
                FadeTransition ft = new FadeTransition(
                        Duration.millis(1000), stage.getScene().getRoot());
                ft.setFromValue(1.0);
                ft.setToValue(0.0);
                final Stage s = stage;
                EventHandler<ActionEvent> eh = new EventHandler<ActionEvent>() {
                    public void handle(ActionEvent t) {
                        s.hide();
                    } };
                ft.setOnFinished(eh);
                ft.play();
            }
    }
}