/* Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
 * The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by the terms of this license.
 * You must not remove this notice, or any other, from this software.
 */

package no.andante.george;


import javafx.application.Preloader;
import javafx.scene.Scene;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;


public class MainPreloader extends Preloader {

    private Stage stage;
    private ProgressBar bar;
    private boolean loadingProgress = false;


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

    private void handleProgress(double prog) {
        if (prog == 0.0 || prog == 1.0)
            bar.setProgress(-1.0);
        else
            bar.setProgress(prog);
    }


    @Override
    public void handleProgressNotification(ProgressNotification pn) {
        double prog = pn.getProgress();
//        System.out.println("  ## ProgressNotification: " + prog);
        handleProgress(prog);
    }


    @Override
    public void handleStateChangeNotification(StateChangeNotification notification) {
//        System.out.println("  ## StateChangeNotification: "+notification.getType());

        if (notification.getType() == StateChangeNotification.Type.BEFORE_START) {
            ((IStageSharing) notification.getApplication()).handover(stage);
        }}


    @Override
    public void handleApplicationNotification(PreloaderNotification pn) {
        if (pn instanceof ProgressNotification) {
            double prog = ((ProgressNotification) pn).getProgress();
//            System.out.println("  ## PreloaderNotification: " + prog);
            handleProgress(prog);
        }}
}
