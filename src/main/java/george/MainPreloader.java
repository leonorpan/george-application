// http://stackoverflow.com/questions/15126210/how-to-use-javafx-preloader-with-stand-alone-application-in-eclipse
// https://docs.oracle.com/javase/8/docs/technotes/guides/deploy/preloaders.htm

package george;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.application.Preloader;
import javafx.application.Preloader.StateChangeNotification.Type;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.apache.commons.io.output.WriterOutputStream;

import java.io.PrintStream;
import java.io.StringWriter;


public class MainPreloader extends Preloader {

    private Stage stage;
    private ProgressBar bar;

    PrintStream stdout = System.out;
    PrintStream stderr = System.err;


    private class MyOut extends StringWriter {
        boolean is_err;
        int counter = 1;

        MyOut(boolean is_err) {
            this.is_err = is_err;
        }


        @Override
        public void flush(){
            synchronized(this){
                super.flush();
                final String s = toString();

                if (is_err) stderr.print(s);
                else        stdout.print(s);

                counter++;

                Platform.runLater(new Runnable() { @Override public void run() {
                        bar.setProgress(counter/250.); }});

                StringBuffer sb = getBuffer();
                sb.delete(0, sb.length());
            }
        }
    }


    private void wrap_outs() {
        System.setOut(new PrintStream(new WriterOutputStream(new MyOut(false)), true));
        System.setErr(new PrintStream(new WriterOutputStream(new MyOut(true)), true));
    }


    private void unwrap_outs() {
        System.setOut(stdout);
        System.setErr(stderr);
    }


    private Scene createPreloaderScene() {
        wrap_outs();
        bar = new ProgressBar();
        BorderPane p = new BorderPane();
        p.setCenter(bar);
        return new Scene(p, 300, 150);
    }


    @Override
    public void start(Stage stage) throws Exception {
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
                        unwrap_outs();
                        s.hide();
                    } };
                ft.setOnFinished(eh);
                ft.play();
            }
    }
}