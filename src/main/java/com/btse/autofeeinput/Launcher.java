package com.btse.autofeeinput;

/**
 * Trampoline launcher used when running as a shaded fat jar. Avoids the JavaFX runtime check that
 * fails when Application is the main class.
 */
public class Launcher {
    public static void main(String[] args) {
        Main.main(args);
    }
}
