#! /usr/bin/env python3

import hashlib
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
        result = self.run_script(["arg1", "arg 2"])
        output = result.stdout

        self.assertIn("Downloading Batect", output)
        self.assertIn("BATECT_WRAPPER_SCRIPT_DIR is: {}\\\n".format(self.get_script_dir()), output)
        self.assertIn("BATECT_WRAPPER_CACHE_DIR is: {}\n".format(self.cache_dir), output)
        self.assertIn("HOSTNAME is: {}\n".format(os.environ['COMPUTERNAME']), output)
        self.assertIn("I received 2 arguments.\narg1\narg 2\n", output)
        self.assertNotIn("WARNING: you should never see this", output)
        self.assertEqual(result.returncode, 0)

    def test_second_run(self):
        first_result = self.run_script(["arg 1", "arg 2"])
        first_output = first_result.stdout
        self.assertIn("Downloading Batect", first_output)
        self.assertIn("BATECT_WRAPPER_SCRIPT_DIR is: {}\\\n".format(self.get_script_dir()), first_output)
        self.assertIn("BATECT_WRAPPER_CACHE_DIR is: {}\n".format(self.cache_dir), first_output)
        self.assertIn("BATECT_WRAPPER_DID_DOWNLOAD is: true\n", first_output)
        self.assertIn("HOSTNAME is: {}\n".format(os.environ['COMPUTERNAME']), first_output)
        self.assertIn("I received 2 arguments.\narg 1\narg 2\n", first_output)
        self.assertEqual(first_result.returncode, 0)

        second_result = self.run_script(["arg 3", "arg 4"])
        second_output = second_result.stdout
        self.assertNotIn("Downloading Batect", second_output)
        self.assertIn("BATECT_WRAPPER_SCRIPT_DIR is: {}\\\n".format(self.get_script_dir()), second_output)
        self.assertIn("BATECT_WRAPPER_CACHE_DIR is: {}\n".format(self.cache_dir), second_output)
        self.assertIn("BATECT_WRAPPER_DID_DOWNLOAD is: false\n", second_output)
        self.assertIn("HOSTNAME is: {}\n".format(os.environ['COMPUTERNAME']), second_output)
        self.assertIn("I received 2 arguments.\narg 3\narg 4\n", second_output)
        self.assertEqual(first_result.returncode, 0)

    def test_running_in_cmd(self):
        result = self.run_script([], start_with=["cmd", "/c"])
        output = result.stdout

        self.assertIn("The Java application has started.", output)
        self.assertNotIn("WARNING: you should never see this", output)
        self.assertEqual(result.returncode, 0)

    def test_running_in_powershell(self):
        result = self.run_script([], start_with=["powershell", "-ExecutionPolicy", "Bypass", "-NoLogo", "-NoProfile", "-Command"])
        output = result.stdout

        self.assertIn("The Java application has started.", output)
        self.assertNotIn("WARNING: you should never see this", output)
        self.assertEqual(result.returncode, 0)

    def test_running_in_pwsh(self):
        result = self.run_script([], start_with=["pwsh", "-ExecutionPolicy", "Bypass", "-NoLogo", "-NoProfile", "-Command"])
        output = result.stdout

        self.assertIn("The Java application has started.", output)
        self.assertNotIn("WARNING: you should never see this", output)
        self.assertEqual(result.returncode, 0)

    def test_download_fails(self):
        result = self.run_script(["arg 1", "arg 2"], download_url=self.download_url("does-not-exist"))

        self.assertIn("Downloading Batect", result.stdout)
        self.assertRegex(result.stdout, "(\(404\) Not Found|404 \(File not found\))")
        self.assertNotIn("WARNING: you should never see this", result.stdout)
        self.assertNotEqual(result.returncode, 0)

    def test_no_java(self):
        path_dir = self.create_limited_path()

        result = self.run_script([], path=path_dir)

        self.assertIn("Java is not installed or not on your PATH. Please install it and try again.", result.stdout)
        self.assertNotEqual(result.returncode, 0)

    def test_no_java_in_java_home(self):
        path_dir = self.create_limited_path()
        java_home = "C:\\nonsense"

        result = self.run_script([], path=path_dir, java_home=java_home)

        self.assertIn("JAVA_HOME is set to 'C:\\nonsense', but there is no Java executable at 'C:\\nonsense\\bin\\java.exe'.", result.stdout)
        self.assertNotEqual(result.returncode, 0)

    def test_java_not_on_path_but_java_home_set(self):
        path_dir = self.create_limited_path()
        java_home = self.java_home_dir("8")

        result = self.run_script([], path=path_dir, java_home=java_home)

        self.assertIn("The application has started.", result.stdout)
        self.assertEqual(result.returncode, 0)

    def test_unsupported_java_on_path(self):
        path_dir = self.create_limited_path_for_specific_java("7")

        result = self.run_script([], path=path_dir)

        self.assertIn("The version of Java that is available on your PATH is version 1.7, but version 1.8 or greater is required.\n" +
                      "If you have a newer version of Java installed, please make sure your PATH is set correctly.", result.stdout)

        self.assertNotEqual(result.returncode, 0)

    def test_unsupported_java_in_java_home(self):
        path_dir = self.create_limited_path_for_specific_java("8")
        java_home = self.java_home_dir("7")

        result = self.run_script([], path=path_dir, java_home=java_home)

        self.assertIn("The version of Java that is available in JAVA_HOME is version 1.7, but version 1.8 or greater is required.\n" +
                      "If you have a newer version of Java installed, please make sure JAVA_HOME is set correctly.\n" +
                      "JAVA_HOME takes precedence over any versions of Java available on your PATH.", result.stdout)

        self.assertNotEqual(result.returncode, 0)

    def test_unsupported_java_on_path_with_supported_java_in_java_home(self):
        path_dir = self.create_limited_path_for_specific_java("7")
        java_home = self.java_home_dir("8")

        result = self.run_script([], path=path_dir, java_home=java_home)

        self.assertIn("The application has started.", result.stdout)
        self.assertEqual(result.returncode, 0)

    def test_32bit_java_on_path(self):
        path_dir = self.create_limited_path_for_specific_java("8-32bit")

        result = self.run_script([], path=path_dir)

        self.assertIn("The version of Java that is available on your PATH is a 32-bit version, but Batect requires a 64-bit Java runtime.\n" +
                      "If you have a 64-bit version of Java installed, please make sure your PATH is set correctly.", result.stdout)

        self.assertNotEqual(result.returncode, 0)

    def test_32bit_java_in_java_home(self):
        path_dir = self.create_limited_path_for_specific_java("8")
        java_home = self.java_home_dir("8-32bit")

        result = self.run_script([], path=path_dir, java_home=java_home)

        self.assertIn("The version of Java that is available in JAVA_HOME is a 32-bit version, but Batect requires a 64-bit Java runtime.\n" +
                      "If you have a 64-bit version of Java installed, please make sure JAVA_HOME is set correctly.\n" +
                      "JAVA_HOME takes precedence over any versions of Java available on your PATH.", result.stdout)

        self.assertNotEqual(result.returncode, 0)

    def test_supported_java(self):
        opens_args = "Args are: \"--add-opens\" \"java.base/sun.nio.ch=ALL-UNNAMED\" \"--add-opens\" \"java.base/java.io=ALL-UNNAMED\""

        for version in [8, 9, 10, 11]:
            with self.subTest(java_version=version):
                path_dir = self.create_limited_path_for_specific_java(version)

                result = self.run_script([], path=path_dir)

                self.assertIn("The application has started.", result.stdout)

                if version >= 9:
                    self.assertIn(opens_args, result.stdout)
                else:
                    self.assertNotIn(opens_args, result.stdout)

                self.assertEqual(result.returncode, 0)

    def test_supported_java_with_tool_options_set(self):
        path_dir = self.create_limited_path_for_specific_java("8")

        result = self.run_script([], path=path_dir, with_java_tool_options="true")

        self.assertIn("The application has started.", result.stdout)
        self.assertEqual(result.returncode, 0)

    def test_non_zero_exit(self):
        result = self.run_script(["exit-non-zero"])
        output = result.stdout

        self.assertIn("The Java application has started.", output)
        self.assertNotIn("WARNING: you should never see this", output)
        self.assertEqual(result.returncode, 123)

    def test_no_args(self):
        result = self.run_script([])
        output = result.stdout

        self.assertIn("Downloading Batect", output)
        self.assertIn("BATECT_WRAPPER_SCRIPT_DIR is: {}\\\n".format(self.get_script_dir()), output)
        self.assertIn("BATECT_WRAPPER_CACHE_DIR is: {}\n".format(self.cache_dir), output)
        self.assertIn("HOSTNAME is: {}\n".format(os.environ['COMPUTERNAME']), output)
        self.assertIn("I received 0 arguments.\n", output)
        self.assertNotIn("WARNING: you should never see this", output)
        self.assertEqual(result.returncode, 0)

    def test_one_arg(self):
        result = self.run_script(["arg1"])
        output = result.stdout

        self.assertIn("Downloading Batect", output)
        self.assertIn("BATECT_WRAPPER_SCRIPT_DIR is: {}\\\n".format(self.get_script_dir()), output)
        self.assertIn("BATECT_WRAPPER_CACHE_DIR is: {}\n".format(self.cache_dir), output)
        self.assertIn("HOSTNAME is: {}\n".format(os.environ['COMPUTERNAME']), output)
        self.assertIn("I received 1 arguments.\narg1\n", output)
        self.assertNotIn("WARNING: you should never see this", output)
        self.assertEqual(result.returncode, 0)

    def test_corrupt_download(self):
        result = self.run_script([], download_url=self.download_url("fakes/brokenapp.txt"))
        output = result.stdout

        self.assertRegex(output, "The downloaded version of Batect does not have the expected checksum. Delete '.*' and then re-run this script to download it again.")
        self.assertNotIn("The Java application has started.", output)
        self.assertNotEqual(result.returncode, 0)

    def test_corrupt_cached_version(self):
        result_for_initial_download = self.run_script([])
        self.assertEqual(result_for_initial_download.returncode, 0)

        self.corrupt_cached_file()
        result_after_corruption = self.run_script([])
        output = result_after_corruption.stdout

        self.assertRegex(output, "The downloaded version of Batect does not have the expected checksum. Delete '.*' and then re-run this script to download it again.")
        self.assertNotIn("The Java application has started.", output)
        self.assertNotEqual(result_after_corruption.returncode, 0)

    def corrupt_cached_file(self):
        version = os.environ["BATECT_VERSION"]
        jar_path = "{cache_dir}\\{version}\\batect-{version}.jar".format(cache_dir = self.cache_dir, version = version)

        with open(jar_path, "a+") as f:
            f.truncate(10)

    def create_limited_path(self):
        powershellDir = os.path.join(os.environ["SYSTEMROOT"], "System32", "WindowsPowerShell", "v1.0")

        return powershellDir

    def create_limited_path_for_specific_java(self, version):
        return ";".join([
            self.create_limited_path(),
            os.path.join(self.java_home_dir(version), "bin")
        ])

    def java_home_dir(self, version):
        return os.path.join(self.get_tests_dir(), "fakes", "java" + str(version))

    def run_script(self, args, download_url=None, path=os.environ["PATH"], java_home=None, with_java_tool_options=None, start_with=None):
        if download_url is None:
            download_url = self.default_download_url()

        inherited_env = os.environ.copy()
        inherited_env.pop("JAVA_HOME", None)

        env = {
            **inherited_env,
            "BATECT_CACHE_DIR": self.cache_dir,
            "BATECT_DOWNLOAD_URL": download_url,
            "BATECT_DOWNLOAD_CHECKSUM": self.get_checksum_of_test_app(),
            "PATH": path
        }

        if java_home is not None:
            env["JAVA_HOME"] = java_home

        if with_java_tool_options is not None:
            env["JAVA_TOOL_OPTIONS"] = "-XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap"

        script_path = self.get_script_path()
        command = [script_path] + args

        if start_with is not None:
            command = start_with + [' '.join(command)]

        return subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, env=env, text=True, encoding='utf-8')

    def get_checksum_of_test_app(self):
        with open("test/testapp.jar", "rb") as f:
            bytes = f.read()
            return hashlib.sha256(bytes).hexdigest()

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
