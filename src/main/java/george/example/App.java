/*
 *  Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
 * The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
 *  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
 *  You must not remove this notice, or any other, from this software.
 */

package george.example;

import clojure.lang.RT;
import clojure.java.api.Clojure;
import clojure.lang.IFn;

public class App {

    public static void main(String[] args) {
        System.out.println("   Java: george.example.App.main(...): Hello World!");

        try {
            RT.loadResourceScript("george/example/app.clj");
            IFn mainVar = Clojure.var("george.example.app", "-main");
            mainVar.applyTo(RT.seq(args));
        } catch(Exception e) {
            e.printStackTrace();
        }

    }
}