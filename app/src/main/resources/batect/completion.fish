function __batect_completion_PLACEHOLDER_REGISTER_AS_config_file_path
    set -l config_file_path batect.yml
    set -l tokens (commandline -opc) (commandline -ct)

    argparse --ignore-unknown 'f/config-file=' -- $tokens

    if test -n "$_flag_f"
        if test (string sub --length 1 "$_flag_f") = "="
            set _flag_f (string sub --start 2 "$_flag_f")
        end

        set config_file_path $_flag_f
    end

    if test -f $config_file_path
        realpath $config_file_path
    else
        echo $config_file_path
    end
end

function __batect_completion_PLACEHOLDER_REGISTER_AS_task_names
    set -l config_file_path (__batect_completion_PLACEHOLDER_REGISTER_AS_config_file_path)

    if test ! -f $config_file_path
        return
    end

    set -l error_output_path (mktemp)
    set -l output ($BATECT_COMPLETION_PROXY_WRAPPER_PATH --generate-completion-task-info=fish --config-file=$config_file_path 2>$error_output_path)
    set -l command_status $status
    rm -f $error_output_path

    if test $command_status -ne 0
        return
    end

    set -l task_delimiter_index (math (contains --index '### TASKS ###' $output) + 1)
    set -l tasks $output[$task_delimiter_index..-1]

    for task in $tasks
        echo $task
    end
end

complete -c PLACEHOLDER_REGISTER_AS --condition "not contains -- -- (commandline -opc)" --no-files -a "(__batect_completion_PLACEHOLDER_REGISTER_AS_task_names)"

function __batect_completion_PLACEHOLDER_REGISTER_AS_post_task_argument_handler
    set -l tokens (commandline -opc) (commandline -ct)
    set -l index (contains -i -- -- (commandline -opc))
    set -e tokens[1..$index]
    complete -C"$tokens"
end

complete -c PLACEHOLDER_REGISTER_AS --condition "contains -- -- (commandline -opc)" -a "(__batect_completion_PLACEHOLDER_REGISTER_AS_post_task_argument_handler)"
