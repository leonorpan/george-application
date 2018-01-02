/*
 *  Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
 * The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
 *  By using this software in any fashion, you are agreeing to be bound by the terms of this license.
 *  You must not remove this notice, or any other, from this software.
 */

package george.application;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

/** Created by Terje Dahl. */


// TODO: split out certain aspects to seperate "platform" class

public class Versions {

    public final static boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("windows");
    public final static boolean IS_MAC      = System.getProperty("os.name").toLowerCase().contains("mac");

    public final static String SEP = File.separator;
    public final static String PSEP = File.pathSeparator;


    public final static String APP_ID = "george";
    public final static String APP_NAME = "George";
    public final static String JAR_BASE_NAME = APP_ID;


    // user-home "~/AppData/George" or "~/Library/Application Support/George" or "~/.george/George"

    public final static File APPDATA_DIR =
            new File(
                    System.getProperty("user.home")
                            + SEP +
                            (IS_WINDOWS ?
                                    "AppData" + SEP + "Local" :  // Windows-version
                                    IS_MAC ?
                                            "Library" + SEP + "Application Support" :  //Mac-version
                                            "." + APP_NAME)  // Linux-version
                            +  SEP +
                            APP_NAME);

    public static File APPJARS_DIR = new File( APPDATA_DIR + SEP + "appjars");



    static {
        APPJARS_DIR.mkdirs();  // make sure the directories are created!
        System.out.println("APPJARS_DIR exists: " + APPJARS_DIR.exists());

    }



    public static int compareVersions (String x, String y) {
        //System.out.println("compareVersions"+"\n    x: "+x+", y: "+y);
        if(x == null || x.equals( "null") || y == null || y.equals("null"))
            return 0;
        String [] s1 = x.split("\\."), s2 = y.split("\\.");
        int max_len = s1.length > s2.length ? s2.length : s1.length;
        for (int i = 0; i< max_len; i++) {
            String [] ss1 = s1[i].split("b"), ss2 = s2[i].split("b");
            int xx = Integer.parseInt(ss1[ss1.length-1]), yy = Integer.parseInt(ss2[ss2.length-1]);
            //int r = xx.compareToIgnoreCase(yy);
            int r =  yy - xx;
            //System.out.println("    yy:"+yy+" - xx: "+xx+" = r: "+r);
            if (r != 0) return r * -1;  } // '* -1' ensures ascending order
        return 0; }


    public static String getVersionFromFile(File f, String ending) {
        String [] ss = f.getName().split("-");
        return ss[ss.length-1].split("\\."+ending)[0]; }


    public static String getVersionFromJar(File f) {
        return getVersionFromFile(f, "jar"); }


    public static File [] listJarsInDir (File dir) {
        return dir.listFiles(
                file -> file.getPath().toLowerCase().endsWith(".jar"));

    }

    public static File getLatestJarInDir(File dir) {
        File [] files = listJarsInDir(dir);
        if (files.length != 0) {
            Arrays.sort(files, new Comparator<File>() {
                public int compare(File j1, File j2) {
                    return compareVersions(getVersionFromJar(j1), getVersionFromJar(j2)); }});
            return files[files.length-1]; }
        return null; }


}

