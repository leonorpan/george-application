/* Copyright (c) 2017 Terje Dahl. All rights reserved.
 * The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by the terms of this license.
 * You must not remove this notice, or any other, from this software.
 */

package no.andante.george;


import javafx.animation.FadeTransition;
import javafx.application.Preloader;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.util.Duration;


public class MainPreloader extends Preloader {

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
        System.out.println("MainPreloader.start()...");
        this.stage = stage;
        stage.setTitle("Loading George ...");
        stage.setScene(createPreloaderScene());
        stage.show();
    }



    @Override
    public void handleStateChangeNotification(StateChangeNotification stateChangeNotification) {
        if (stateChangeNotification.getType() == StateChangeNotification.Type.BEFORE_START) {

            //fade out, hide stage at the end of animation
            FadeTransition ft = new FadeTransition(
                    Duration.millis(1000), stage.getScene().getRoot());
            ft.setFromValue(1.0);
            ft.setToValue(0.0);
            final Stage s = stage;
            EventHandler<ActionEvent> eh = t -> s.hide();
            ft.setOnFinished(eh);
            ft.play();
        }
    }
}
