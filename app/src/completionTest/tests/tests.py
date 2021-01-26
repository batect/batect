#! /usr/bin/env python3

import os.path
import shutil
import subprocess
import tempfile
import unittest
import uuid

from abc import ABC


class CompletionTestBase(ABC):
    def setUp(self):
        cache_directory = os.path.expanduser(os.path.join("~", ".batect"))

        if os.path.exists(cache_directory):
            shutil.rmtree(cache_directory)

    def test_option_completion(self):
        results = self.run_completions_for("./batect -", "/app/bin")
        self.assertEqual([
            "--cache-type",
            "--clean",
            "--config-file",
            "--config-var",
            "--config-vars-file",
            "--disable-ports",
            "--docker-cert-path",
            "--docker-config",
            "--docker-host",
            "--docker-tls",
            "--docker-tls-ca-cert",
            "--docker-tls-cert",
            "--docker-tls-key",
            "--docker-tls-verify",
            "--enable-buildkit",
            "--help",
            "--linux-cache-init-image",
            "--list-tasks",
            "--log-file",
            "--max-parallelism",
            "--no-cleanup",
            "--no-cleanup-after-failure",
            "--no-cleanup-after-success",
            "--no-color",
            "--no-proxy-vars",
            "--no-telemetry",
            "--no-update-notification",
            "--no-wrapper-cache-cleanup",
            "--output",
            "--override-image",
            "--permanently-disable-telemetry",
            "--permanently-enable-telemetry",
            "--skip-prerequisites",
            "--upgrade",
            "--use-network",
            "--version",
            "-T",
            "-f",
            "-o",
        ], results)

    def test_single_use_flag_completion(self):
        results = self.run_completions_for("./batect --list-tasks -", "/app/bin")
        self.assertNotIn('--list-tasks', results)
        self.assertNotIn('-T', results)

    def test_multi_value_option_completion(self):
        results = self.run_completions_for("./batect --config-var=SOME_OPTION=some-value -", "/app/bin")
        self.assertIn("--config-var", results)

    def test_partial_option_completion(self):
        results = self.run_completions_for("./batect --conf", "/app/bin")
        self.assertIn("--config-file", results)

    def test_enum_option_completion(self):
        results = self.run_completions_for("./batect --output=", "/app/bin")
        self.assertEqual(["--output=all", "--output=fancy", "--output=quiet", "--output=simple"], results)

    def test_task_name_completion_standard_file_location(self):
        results = self.run_completions_for("/app/bin/batect tas", self.directory_for_test_case("simple-config"))
        self.assertEqual(["task-1", "task-2"], results)

    def test_task_name_completion_custom_file_location(self):
        flags = ["--config-file=", "--config-file ", "-f ", "-f="]

        for flag in flags:
            with self.subTest(flag=flag):
                command_line = "/app/bin/batect {}../simple-config/batect.yml tas".format(flag)
                results = self.run_completions_for(command_line, self.directory_for_test_case("invalid-config"))

                self.assertEqual(["task-1", "task-2"], results)

    def test_task_name_completion_custom_file_location_with_spaces(self):
        flags = ["--config-file=", "--config-file ", "-f ", "-f="]

        for flag in flags:
            with self.subTest(flag=flag):
                command_line = "/app/bin/batect {}\\\"../directory with spaces/batect.yml\\\" tas".format(flag)
                results = self.run_completions_for(command_line, self.directory_for_test_case("invalid-config"))

                self.assertEqual(["task-1", "task-2"], results)

    def test_task_name_completion_invalid_project(self):
        results = self.run_completions_for("/app/bin/batect tas", self.directory_for_test_case("invalid-config"))
        self.assertEqual([], results)

    def test_task_name_completion_invalid_inferred_project_name(self):
        results = self.run_completions_for("/app/bin/batect tas", self.set_up_test_directory_with_invalid_inferred_project_name())
        self.assertEqual([], results)

    def test_completion_after_task_name(self):
        batect_results = self.run_completions_for("./batect my-task -- ls -", "/app/bin")
        system_results = self.run_completions_for("ls -", "/app/bin")
        self.assertEqual(batect_results, system_results)

    def test_completion_twice_no_modifications(self):
        output = self.run_two_commands(
            self.completion_command_for("/app/bin/batect tas"),
            self.completion_command_for("/app/bin/batect tas"),
            self.directory_for_test_case("simple-config"),
        )

        self.assertEqual(output["first"], ["task-1", "task-2"])
        self.assertEqual(output["second"], ["task-1", "task-2"])

    def test_completion_twice_project_file_modified(self):
        test_directory = self.generate_test_directory()
        original_config_directory = self.directory_for_test_case("simple-config")
        test_config_file = os.path.join(test_directory, "batect.yml")
        shutil.copy(os.path.join(original_config_directory, "batect.yml"), test_config_file)

        output = self.run_two_commands(
            self.completion_command_for("/app/bin/batect tas"),
            'sed -i"" "s/task-1/task-3/g" batect.yml && ' + self.completion_command_for("/app/bin/batect tas"),
            test_directory,
        )

        self.assertEqual(output["first"], ["task-1", "task-2"])
        self.assertEqual(output["second"], ["task-2", "task-3"])

    def test_completion_twice_include_file_modified(self):
        test_directory = self.set_up_test_directory_with_include()

        output = self.run_two_commands(
            self.completion_command_for("/app/bin/batect tas"),
            'sed -i"" "s/task-2/task-3/g" other-file.yml && ' + self.completion_command_for("/app/bin/batect tas"),
            test_directory,
        )

        self.assertEqual(output["first"], ["task-1", "task-2"])
        self.assertEqual(output["second"], ["task-1", "task-3"])

    def test_completion_twice_include_file_deleted(self):
        test_directory = self.set_up_test_directory_with_include()

        output = self.run_two_commands(
            self.completion_command_for("/app/bin/batect tas"),
            'rm other-file.yml && mv without-include.yml batect.yml && ' + self.completion_command_for("/app/bin/batect tas"),
            test_directory,
        )

        self.assertEqual(output["first"], ["task-1", "task-2"])
        self.assertEqual(output["second"], ["task-1"])

    def set_up_test_directory_with_include(self):
        test_directory = self.generate_test_directory()
        original_config_directory = self.directory_for_test_case("with-include")
        shutil.copy(os.path.join(original_config_directory, "batect.yml"), os.path.join(test_directory, "batect.yml"))
        shutil.copy(os.path.join(original_config_directory, "other-file.yml"), os.path.join(test_directory, "other-file.yml"))
        shutil.copy(os.path.join(original_config_directory, "without-include.yml"), os.path.join(test_directory, "without-include.yml"))

        return test_directory

    def set_up_test_directory_with_invalid_inferred_project_name(self):
        test_directory = self.generate_test_directory("_")
        original_config_directory = self.directory_for_test_case("simple-config")
        shutil.copy(os.path.join(original_config_directory, "batect.yml"), os.path.join(test_directory, "batect.yml"))

        return test_directory

    # Why not just use tempfile.mkdtemp()? mkdtemp generates paths with a random assortment of characters, and these could include
    # directory names that start or end with dashes or underscores, which aren't valid inferred project names.
    def generate_test_directory(self, suffix=""):
        test_directory = os.path.join(tempfile.gettempdir(), str(uuid.uuid4()) + suffix)
        os.makedirs(test_directory)

        return test_directory

    def run_completions_for(self, input, working_directory):
        stdout = self.run_command_in_shell(self.completion_command_for(input), working_directory)

        return sorted(stdout.splitlines())

    def completion_command_for(self, input) -> str:
        pass

    def run_two_commands(self, first_command, second_command, working_directory):
        stdout = self.run_command_in_shell('{} && echo "---DIVIDER---" && {}'.format(first_command, second_command), working_directory).splitlines()
        divider_line = stdout.index("---DIVIDER---")
        first_output = sorted(stdout[0:divider_line])
        second_output =(stdout[divider_line + 1:])

        return {"first": first_output, "second": second_output}

    def directory_for_test_case(self, test_case):
        tests_dir = os.path.dirname(os.path.realpath(__file__))

        return os.path.abspath(os.path.join(tests_dir, "test-cases", test_case))

    def run_command_in_shell(self, command, working_directory):
        command_line = self.shell_command_for(command)

        result = subprocess.run(
            command_line,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            cwd=working_directory,
            text=True,
            encoding='utf-8'
        )

        self.assertEqual(result.stderr, '')
        self.assertEqual(result.returncode, 0)

        return result.stdout

    def shell_command_for(self, command):
        pass


class FishCompletionTests(CompletionTestBase, unittest.TestCase):
    def __init__(self, methodName):
        super().__init__(methodName)
        self.maxDiff = None

    def completion_command_for(self, input) -> str:
        return 'complete -C"{}"'.format(input)

    def shell_command_for(self, command):
        return ["fish", "--private", "--command", command]


if __name__ == '__main__':
    unittest.main()
