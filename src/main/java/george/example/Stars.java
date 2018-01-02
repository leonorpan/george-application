/*
 *  Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
 * The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
 *  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
 *  You must not remove this notice, or any other, from this software.
 */

// http://blog.netopyr.com/2012/06/14/using-the-javafx-animationtimer/

package george.example;

import java.util.Random;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

public class Stars extends Application {

    private static final int STAR_COUNT = 20000;

    private final Rectangle[] nodes = new Rectangle[STAR_COUNT];
    private final double[] angles = new double[STAR_COUNT];
    private final long[] start = new long[STAR_COUNT];

    private final Random random = new Random();

    @Override
    public void start(final Stage primaryStage) {
        for (int i=0; i<STAR_COUNT; i++) {
            nodes[i] = new Rectangle(1, 1, Color.WHITE);
            angles[i] = 2.0 * Math.PI * random.nextDouble();
            start[i] = random.nextInt(2000000000);
        }
        final Scene scene = new Scene(new Group(nodes), 800, 600, Color.BLACK);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Stars.java");
        primaryStage.show();

        new AnimationTimer() {
            @Override
            public void handle(long now) {
                final double width = 0.5 * primaryStage.getWidth();
                final double height = 0.5 * primaryStage.getHeight();
                final double radius = Math.sqrt(2) * Math.max(width, height);
                for (int i=0; i<STAR_COUNT; i++) {
                    final Node node = nodes[i];
                    final double angle = angles[i];
                    final long t = (now - start[i]) % 2000000000;
                    final double d = t * radius / 2000000000.0;
                    node.setTranslateX(Math.cos(angle) * d + width);
                    node.setTranslateY(Math.sin(angle) * d + height);
                }
            }
        }.start();
    }

    public static void main(String[] args) {
        launch(args);
    }

}