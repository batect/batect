/*
    Copyright 2017-2021 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package testapp;

import java.util.Arrays;

public class Application {
    public static void main(String[] args) {
        System.out.println("The Java application has started.");
        System.out.println("BATECT_WRAPPER_SCRIPT_DIR is: " + System.getenv("BATECT_WRAPPER_SCRIPT_DIR"));
        System.out.println("BATECT_WRAPPER_CACHE_DIR is: " + System.getenv("BATECT_WRAPPER_CACHE_DIR"));
        System.out.println("BATECT_WRAPPER_DID_DOWNLOAD is: " + System.getenv("BATECT_WRAPPER_DID_DOWNLOAD"));
        System.out.println("HOSTNAME is: " + System.getenv("HOSTNAME"));
        System.out.println("I received " + args.length + " arguments.");

        Arrays.stream(args).forEach(System.out::println);

        if (args.length >= 1 && args[0].equals("exit-non-zero")) {
            System.exit(123);
        }
    }
}
