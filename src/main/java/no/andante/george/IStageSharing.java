package no.andante.george;

import javafx.scene.Parent;

/* Contact interface between application and preloader */
public interface IStageSharing {
    /* Parent node of the application */
//    Parent getRootNode();
    void handover(javafx.stage.Stage stage);
}


