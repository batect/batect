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


    def run_script(self, args):
        env = {
            "BATECT_CACHE_DIR": self.cache_dir,
            "BATECT_DOWNLOAD_URL": "http://localhost:" + str(self.http_port) + "/test/testapp.jar"
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
