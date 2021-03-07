#! /usr/bin/env python3

import hashlib
import http.server
import os
import shutil
import socket
import subprocess
import tempfile
import threading
import unittest


class WrapperScriptTests(unittest.TestCase):
    http_port = 8080
    default_bash = "/usr/bin/bash"

    minimum_script_dependencies = [
        "/usr/bin/basename",
        "/usr/bin/cut",
        "/usr/bin/dirname",
        "/usr/bin/grep",
        "/usr/bin/head",
        "/usr/bin/mkdir",
        "/usr/bin/mktemp",
        "/usr/bin/mv",
        "/usr/bin/sed",
        "/usr/bin/sha256sum",
        "/usr/bin/uname",
    ]

    minimum_script_dependencies_with_default_bash = minimum_script_dependencies + [default_bash]

    def setUp(self):
        self.start_server()
        self.cache_dir = tempfile.mkdtemp()

    def tearDown(self):
        self.stop_server()
        shutil.rmtree(self.cache_dir)

    def download_url(self, path):
        return "http://localhost:" + str(self.http_port) + "/test/" + path

    def default_download_url(self):
        return self.download_url("testapp.jar")

    def test_first_run(self):
        result = self.run_script(["arg 1", "arg 2"])
        output = result.stdout.decode()

        self.assertIn("Downloading Batect", output)
        self.assertIn("BATECT_WRAPPER_SCRIPT_DIR is: {}\n".format(self.get_script_dir()), output)
        self.assertIn("BATECT_WRAPPER_CACHE_DIR is: {}\n".format(self.cache_dir), output)
        self.assertIn("HOSTNAME is: {}\n".format(socket.gethostname()), output)
        self.assertIn("I received 2 arguments.\narg 1\narg 2\n", output)
        self.assertEqual(result.returncode, 0)

    def test_second_run(self):
        first_result = self.run_script(["arg 1", "arg 2"])
        first_output = first_result.stdout.decode()
        self.assertIn("Downloading Batect", first_output)
        self.assertIn("BATECT_WRAPPER_SCRIPT_DIR is: {}\n".format(self.get_script_dir()), first_output)
        self.assertIn("BATECT_WRAPPER_CACHE_DIR is: {}\n".format(self.cache_dir), first_output)
        self.assertIn("BATECT_WRAPPER_DID_DOWNLOAD is: true\n", first_output)
        self.assertIn("HOSTNAME is: {}\n".format(socket.gethostname()), first_output)
        self.assertIn("I received 2 arguments.\narg 1\narg 2\n", first_output)
        self.assertEqual(first_result.returncode, 0)

        second_result = self.run_script(["arg 3", "arg 4"])
        second_output = second_result.stdout.decode()
        self.assertNotIn("Downloading Batect", second_output)
        self.assertIn("BATECT_WRAPPER_SCRIPT_DIR is: {}\n".format(self.get_script_dir()), second_output)
        self.assertIn("BATECT_WRAPPER_CACHE_DIR is: {}\n".format(self.cache_dir), second_output)
        self.assertIn("BATECT_WRAPPER_DID_DOWNLOAD is: false\n", second_output)
        self.assertIn("HOSTNAME is: {}\n".format(socket.gethostname()), second_output)
        self.assertIn("I received 2 arguments.\narg 3\narg 4\n", second_output)
        self.assertEqual(first_result.returncode, 0)

    def test_download_fails(self):
        result = self.run_script(["arg 1", "arg 2"], download_url=self.download_url("does-not-exist"))

        self.assertIn("Downloading Batect", result.stdout.decode())
        self.assertIn("404 File not found", result.stdout.decode())
        self.assertNotEqual(result.returncode, 0)

    def test_download_is_not_quiet(self):
        result = self.run_script([], quiet_download="false")
        result_output = result.stdout.decode()

        self.assertIn("Downloading Batect", result_output)
        self.assertIn("BATECT_WRAPPER_SCRIPT_DIR is: {}\n".format(self.get_script_dir()), result_output)
        self.assertIn("BATECT_WRAPPER_CACHE_DIR is: {}\n".format(self.cache_dir), result_output)
        self.assertIn("HOSTNAME is: {}\n".format(socket.gethostname()), result_output)
        self.assertIn("#", result_output)
        self.assertEqual(result.returncode, 0)

    def test_download_is_quiet(self):
        result = self.run_script([], quiet_download="true")
        result_output = result.stdout.decode()

        self.assertNotIn("Downloading Batect", result_output)
        self.assertIn("BATECT_WRAPPER_SCRIPT_DIR is: {}\n".format(self.get_script_dir()), result_output)
        self.assertIn("BATECT_WRAPPER_CACHE_DIR is: {}\n".format(self.cache_dir), result_output)
        self.assertIn("HOSTNAME is: {}\n".format(socket.gethostname()), result_output)
        self.assertNotIn("#", result_output)
        self.assertNotIn("Xferd", result_output)
        self.assertEqual(result.returncode, 0)

    def test_no_curl(self):
        path_dir = self.create_limited_path(self.minimum_script_dependencies_with_default_bash)

        result = self.run_script([], path=path_dir)

        self.assertIn("curl is not installed or not on your PATH. Please install it and try again.", result.stdout.decode())
        self.assertNotEqual(result.returncode, 0)

    def test_no_java(self):
        path_dir = self.create_limited_path(self.minimum_script_dependencies_with_default_bash + ["/usr/bin/curl"])

        result = self.run_script([], path=path_dir)

        self.assertIn("Java is not installed or not on your PATH. Please install it and try again.", result.stdout.decode())
        self.assertNotEqual(result.returncode, 0)

    def test_unsupported_java(self):
        path_dir = self.create_limited_path_for_specific_java_version("7")

        result = self.run_script([], path=path_dir)

        self.assertIn("The version of Java that is available on your PATH is version 1.7, but version 1.8 or greater is required.\n" +
                      "If you have a newer version of Java installed, please make sure your PATH is set correctly.", result.stdout.decode())

        self.assertNotEqual(result.returncode, 0)

    def test_32bit_java(self):
        path_dir = self.create_limited_path_for_specific_java("fake-32-bit")

        result = self.run_script([], path=path_dir)

        self.assertIn("The version of Java that is available on your PATH is a 32-bit version, but Batect requires a 64-bit Java runtime.\n" +
                      "If you have a 64-bit version of Java installed, please make sure your PATH is set correctly.", result.stdout.decode())

        self.assertNotEqual(result.returncode, 0)

    def test_mac_placeholder_java(self):
        path_dir = self.create_limited_path_for_specific_java("fake-mac-placeholder")

        result = self.run_script([], path=path_dir)

        self.assertIn("Java is not installed or not on your PATH. Please install it and try again.", result.stdout.decode())
        self.assertNotEqual(result.returncode, 0)

    def test_supported_java(self):
        for version in ["8", "9", "10", "11"]:
            with self.subTest(java_version=version):
                path_dir = self.create_limited_path_for_specific_java_version(version)

                result = self.run_script([], path=path_dir)

                self.assertIn("The Java application has started.", result.stdout.decode())
                self.assertEqual(result.returncode, 0)

    def test_supported_java_with_tool_options_set(self):
        path_dir = self.create_limited_path_for_specific_java_version("8")

        result = self.run_script([], path=path_dir, with_java_tool_options="true")

        self.assertIn("The Java application has started.", result.stdout.decode())
        self.assertEqual(result.returncode, 0)

    # macOS ships with Bash 3.2, so we need to make sure the wrapper works with that.
    def test_supported_java_with_old_bash(self):
        path_dir = self.create_limited_path_for_specific_java_version("8", bash="/shells/bash-3.2/bin/bash")

        result = self.run_script([], path=path_dir)

        self.assertIn("The Java application has started.", result.stdout.decode())
        self.assertEqual(result.returncode, 0)

    def test_non_zero_exit(self):
        result = self.run_script(["exit-non-zero"])
        output = result.stdout.decode()

        self.assertIn("The Java application has started.", output)
        self.assertNotIn("WARNING: you should never see this", output)
        self.assertEqual(result.returncode, 123)

    def test_corrupt_download(self):
        result = self.run_script([], download_url=self.download_url("brokenapp.txt"))
        output = result.stdout.decode()

        self.assertRegex(output, "The downloaded version of Batect does not have the expected checksum. Delete '.*' and then re-run this script to download it again.")
        self.assertNotIn("The Java application has started.", output)
        self.assertNotEqual(result.returncode, 0)

    def test_corrupt_cached_version(self):
        result_for_initial_download = self.run_script([])
        self.assertEqual(result_for_initial_download.returncode, 0)

        self.corrupt_cached_file()
        result_after_corruption = self.run_script([])
        output = result_after_corruption.stdout.decode()

        self.assertRegex(output, "The downloaded version of Batect does not have the expected checksum. Delete '.*' and then re-run this script to download it again.")
        self.assertNotIn("The Java application has started.", output)
        self.assertNotEqual(result_after_corruption.returncode, 0)

    def corrupt_cached_file(self):
        with open(self.cache_dir + "/VERSION-GOES-HERE/batect-VERSION-GOES-HERE.jar", "a+") as f:
            f.truncate(10)

    def create_limited_path_for_specific_java_version(self, java_version, bash=default_bash):
        return self.create_limited_path_for_specific_java("java-{}-openjdk-amd64".format(java_version), bash)

    def create_limited_path_for_specific_java(self, java_name, bash=default_bash):
        return self.create_limited_path(self.minimum_script_dependencies +
                                        [
                                            bash,
                                            "/usr/bin/curl",
                                            "/usr/lib/jvm/{}/bin/java".format(java_name),
                                        ])

    def create_limited_path(self, executables):
        path_dir = tempfile.mkdtemp()
        self.addCleanup(lambda: shutil.rmtree(path_dir))

        for executable in executables:
            base_name = os.path.basename(executable)
            os.symlink(executable, os.path.join(path_dir, base_name))

        return path_dir

    def run_script(self, args, download_url=None, path=os.environ["PATH"], quiet_download=None, with_java_tool_options=None):
        if download_url is None:
            download_url = self.default_download_url()

        env = {
            "BATECT_CACHE_DIR": self.cache_dir,
            "BATECT_DOWNLOAD_URL": download_url,
            "BATECT_DOWNLOAD_CHECKSUM": self.get_checksum_of_test_app(),
            "PATH": path
        }

        if quiet_download is not None:
            env["BATECT_QUIET_DOWNLOAD"] = quiet_download

        if with_java_tool_options is not None:
            env["JAVA_TOOL_OPTIONS"] = "-XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap"

        path = self.get_script_path()
        command = [path] + args

        return subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, env=env)

    def get_checksum_of_test_app(self):
        with open("test/testapp.jar", "rb") as f:
            bytes = f.read()
            return hashlib.sha256(bytes).hexdigest()

    def get_script_dir(self):
        return os.path.abspath(os.path.join(os.path.dirname(os.path.realpath(__file__)), "..", "src"))

    def get_script_path(self):
        return os.path.join(self.get_script_dir(), "template.sh")

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
