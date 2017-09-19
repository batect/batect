package testapp;

import java.util.Arrays;

public class Application {
    public static void main(String[] args) {
        System.out.println("I received " + args.length + " arguments.");

        Arrays.stream(args).forEach(System.out::println);
    }
}
