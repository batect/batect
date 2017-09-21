#! /usr/bin/env python3

import http.server
import os
import shutil
import subprocess
import tempfile
import threading
import unittest


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
        result = self.run_script(["arg 1", "arg 2"])

        self.assertIn("Downloading batect", result.stdout.decode())
        self.assertIn("I received 2 arguments.\narg 1\narg 2\n", result.stdout.decode())
        self.assertEqual(result.returncode, 0)

    def test_second_run(self):
        first_result = self.run_script(["arg 1", "arg 2"])
        self.assertIn("Downloading batect", first_result.stdout.decode())
        self.assertIn("I received 2 arguments.\narg 1\narg 2\n", first_result.stdout.decode())
        self.assertEqual(first_result.returncode, 0)

        second_result = self.run_script(["arg 3", "arg 4"])
        self.assertNotIn("Downloading batect", second_result.stdout.decode())
        self.assertIn("I received 2 arguments.\narg 3\narg 4\n", second_result.stdout.decode())
        self.assertEqual(first_result.returncode, 0)

    def test_download_fails(self):
        result = self.run_script(["arg 1", "arg 2"], download_url=self.default_download_url + "-does-not-exist")

        self.assertIn("Downloading batect", result.stdout.decode())
        self.assertIn("404 File not found", result.stdout.decode())
        self.assertNotEqual(result.returncode, 0)

    def test_no_curl(self):
        result = self.run_script([], path="/bin")

        self.assertIn("curl is not installed or not on your PATH. Please install it and try again.", result.stdout.decode())
        self.assertNotEqual(result.returncode, 0)

    def test_no_java(self):
        path_dir = tempfile.mkdtemp()
        self.addCleanup(lambda: shutil.rmtree(path_dir))
        os.symlink("/usr/bin/curl", os.path.join(path_dir, "curl"))

        result = self.run_script([], path=path_dir + ":/bin")

        self.assertIn("Java is not installed or not on your PATH. Please install it and try again.", result.stdout.decode())
        self.assertNotEqual(result.returncode, 0)

    def run_script(self, args, download_url=default_download_url, path=os.environ["PATH"]):
        env = {
            "BATECT_CACHE_DIR": self.cache_dir,
            "BATECT_DOWNLOAD_URL": download_url,
            "PATH": path
        }

        path = os.path.join(os.path.dirname(os.path.realpath(__file__)), "..", "src", "template.sh")
        command = [path] + args

        return subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, env=env)

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
