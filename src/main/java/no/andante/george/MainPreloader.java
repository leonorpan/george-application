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
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.util.Duration;


public class MainPreloader extends Preloader {


    private Stage stage;
    private ProgressBar bar;
    private boolean noLoadingProgress = true;
    private Group sceneRoot;
    private Parent preloaderRoot;


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
    public void handleProgressNotification(ProgressNotification pn) {
        System.out.println("  ## ProgressNotification  received!");
        //application loading progress is rescaled to be first 50%
        //Even if there is nothing to load 0% and 100% events can be
        // delivered
        if (pn.getProgress() != 1.0 || !noLoadingProgress) {
            bar.setProgress(pn.getProgress()/2);
            if (pn.getProgress() > 0) {
                noLoadingProgress = false;
            }
        }
    }

    @Override
    public void handleStateChangeNotification(StateChangeNotification notification) {
        System.out.println("  ## StateChangeNotification  received!");
        if (notification.getType() == StateChangeNotification.Type.BEFORE_START) {

            IStageSharing mainApplication = (IStageSharing) notification.getApplication();
            System.out.println("  ## got mainApplication: " + mainApplication);

            //fadeToMain(mainApplication.getRootNode());

            mainApplication.handover(stage);
        }}


    private void fadeToMain(Parent p) {
        //add application scene to the preloader group
        // (visualized "behind" preloader at this point)
        //Note: list is back to front
        sceneRoot.getChildren().add(0, p);

        //setup fade transition for preloader part of scene
        // fade out over 5s
        FadeTransition ft = new FadeTransition(Duration.millis(5000), preloaderRoot);
        ft.setFromValue(1.0);
        ft.setToValue(0.0);
        ft.setOnFinished(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent t) {
                //After fade is done, remove preloader content
                System.out.println("  ## Fade out done");
                sceneRoot.getChildren().remove(preloaderRoot);
            }
        });
        ft.play();
    }




    @Override
    public void handleApplicationNotification(PreloaderNotification pn) {
        System.out.println("  ## PreloaderNotification  received!");
        if (pn instanceof ProgressNotification) {
            //expect application to send us progress notifications
            //with progress ranging from 0 to 1.0
            double v = ((ProgressNotification) pn).getProgress();
            if (!noLoadingProgress) {
                //if we were receiving loading progress notifications
                //then progress is already at 50%.
                //Rescale application progress to start from 50%
                v = 0.5 + v/2;
            }
            bar.setProgress(v);
        } else if (pn instanceof StateChangeNotification) {
            //hide after get any state update from application
            stage.hide();
        }
    }
}
