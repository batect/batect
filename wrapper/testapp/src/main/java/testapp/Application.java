package testapp;

import java.util.Arrays;

public class Application {
    public static void main(String[] args) {
        System.out.println("BATECT_WRAPPER_SCRIPT_PATH is: " + System.getenv("BATECT_WRAPPER_SCRIPT_PATH"));
        System.out.println("HOSTNAME is: " + System.getenv("HOSTNAME"));
        System.out.println("I received " + args.length + " arguments.");

        Arrays.stream(args).forEach(System.out::println);
    }
}
