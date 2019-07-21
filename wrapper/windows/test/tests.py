#! /usr/bin/env python3

import http.server
import os
import shutil
import subprocess
import sys
import tempfile
import threading
import unittest


@unittest.skipUnless(sys.platform.startswith("win"), "requires Windows")
class WrapperScriptTests(unittest.TestCase):
    http_port = 8080
    default_download_url = "http://localhost:" + str(http_port) + "/test/testapp.jar"

    def setUp(self):
        self.start_server()
        self.cache_dir = tempfile.mkdtemp()

    def tearDown(self):
        self.stop_server()
        shutil.rmtree(self.cache_dir)

    def test_first_run(self):
        result = self.run_script(["arg1", "arg 2"])
        output = result.stdout

        self.assertIn("Downloading batect", output)
        self.assertIn("BATECT_WRAPPER_SCRIPT_DIR is: {}\\\n".format(self.get_script_dir()), output)
        self.assertIn("HOSTNAME is: {}\n".format(os.environ['COMPUTERNAME']), output)
        self.assertIn("I received 2 arguments.\narg1\narg 2\n", output)
        self.assertNotIn("WARNING: you should never see this", output)
        self.assertEqual(result.returncode, 0)

    def test_second_run(self):
        first_result = self.run_script(["arg 1", "arg 2"])
        first_output = first_result.stdout
        self.assertIn("Downloading batect", first_output)
        self.assertIn("BATECT_WRAPPER_SCRIPT_DIR is: {}\\\n".format(self.get_script_dir()), first_output)
        self.assertIn("HOSTNAME is: {}\n".format(os.environ['COMPUTERNAME']), first_output)
        self.assertIn("I received 2 arguments.\narg 1\narg 2\n", first_output)
        self.assertEqual(first_result.returncode, 0)

        second_result = self.run_script(["arg 3", "arg 4"])
        second_output = second_result.stdout
        self.assertNotIn("Downloading batect", second_output)
        self.assertIn("BATECT_WRAPPER_SCRIPT_DIR is: {}\\\n".format(self.get_script_dir()), second_output)
        self.assertIn("HOSTNAME is: {}\n".format(os.environ['COMPUTERNAME']), second_output)
        self.assertIn("I received 2 arguments.\narg 3\narg 4\n", second_output)
        self.assertEqual(first_result.returncode, 0)

    def test_download_fails(self):
        result = self.run_script(["arg 1", "arg 2"], download_url=self.default_download_url + "-does-not-exist")

        self.assertIn("Downloading batect", result.stdout)
        self.assertIn("(404) Not Found", result.stdout)
        self.assertNotIn("WARNING: you should never see this", result.stdout)
        self.assertNotEqual(result.returncode, 0)

    def test_no_java(self):
        path_dir = self.create_limited_path()

        result = self.run_script([], path=path_dir)

        self.assertIn("Java is not installed or not on your PATH. Please install it and try again.", result.stdout)
        self.assertNotEqual(result.returncode, 0)

    def test_unsupported_java(self):
        path_dir = self.create_limited_path_for_specific_java_version("7")

        result = self.run_script([], path=path_dir)

        self.assertIn("The version of Java that is available on your PATH is version 1.7, but version 1.8 or greater is required.\n" +
                      "If you have a newer version of Java installed, please make sure your PATH is set correctly.", result.stdout)

        self.assertNotIn("The application has started.", result.stdout)
        self.assertNotEqual(result.returncode, 0)

    def test_supported_java(self):
        opens_args = "Args are: --add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED"

        for version in [8, 9, 10, 11]:
            with self.subTest(java_version=version):
                path_dir = self.create_limited_path_for_specific_java_version(version)

                result = self.run_script([], path=path_dir)

                self.assertIn("The application has started.", result.stdout)

                if version >= 9:
                    self.assertIn(opens_args, result.stdout)
                else:
                    self.assertNotIn(opens_args, result.stdout)

                self.assertEqual(result.returncode, 0)

    def create_limited_path(self):
        powershellDir = os.path.join(os.environ["SYSTEMROOT"], "System32", "WindowsPowerShell", "v1.0")

        return powershellDir

    def create_limited_path_for_specific_java_version(self, version):
        javaDir = os.path.join(self.get_tests_dir(), "fakes", "java" + str(version))

        return ";".join([
            self.create_limited_path(),
            javaDir
        ])

    def run_script(self, args, download_url=default_download_url, path=os.environ["PATH"]):
        env = {
            **os.environ,
            "BATECT_CACHE_DIR": self.cache_dir,
            "BATECT_DOWNLOAD_URL": download_url,
            "PATH": path
        }

        path = self.get_script_path()
        command = [path] + args

        return subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, env=env, text=True, encoding='utf-8') # utf-16le

    def get_tests_dir(self):
        return os.path.dirname(os.path.realpath(__file__))

    def get_script_dir(self):
        return os.path.abspath(os.path.join(self.get_tests_dir(), "..", "build", "scripts"))

    def get_script_path(self):
        return os.path.join(self.get_script_dir(), "batect.cmd")

    def start_server(self):
        self.server = http.server.HTTPServer(("", self.http_port), QuietHTTPHandler)
        threading.Thread(target=self.server.serve_forever, daemon=True).start()

    def stop_server(self):
        self.server.shutdown()
        self.server.server_close()


class QuietHTTPHandler(http.server.SimpleHTTPRequestHandler):
    def log_message(self, format, *args):
        pass


if __name__ == '__main__':
    unittest.main()
