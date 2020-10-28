function __batect_completion_PLACEHOLDER_REGISTER_AS_sha256
    if test (uname) = "Darwin"
        shasum -a 256 $argv
    else
        sha256sum $argv
    end
end

function __batect_completion_PLACEHOLDER_REGISTER_AS_cache_path --argument-names config_file_path
    set -l config_file_path_hash (echo "$config_file_path" | __batect_completion_PLACEHOLDER_REGISTER_AS_sha256 - | cut -d' ' -f1)
    echo "$HOME/.batect/completion/fish-v1/$config_file_path_hash"
end

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

function __batect_completion_PLACEHOLDER_REGISTER_AS_need_to_refresh_cache --argument-names config_file_path cache_path
    if test ! -f $cache_path
        return 0
    end

    set -l cache (cat $cache_path)
    set -l files_delimiter_index (math (contains --index '### FILES ###' $cache) + 1)
    set -l task_delimiter_index (math (contains --index '### TASKS ###' $cache) - 1)
    set -l files_with_hashes $cache[$files_delimiter_index..$task_delimiter_index]

    if string collect $files_with_hashes | __batect_completion_PLACEHOLDER_REGISTER_AS_sha256 -c -s -
        return 1
    else
        return 0
    end
end

function __batect_completion_PLACEHOLDER_REGISTER_AS_refresh_cache --argument-names config_file_path cache_path
    if ! __batect_completion_PLACEHOLDER_REGISTER_AS_need_to_refresh_cache $config_file_path $cache_path
        return
    end

    mkdir -p (dirname $cache_path)
    $BATECT_COMPLETION_PROXY_WRAPPER_PATH --generate-completion-task-info=fish --config-file=$config_file_path >"$cache_path" 2>/dev/null
end

function __batect_completion_PLACEHOLDER_REGISTER_AS_task_names
    set -l config_file_path (__batect_completion_PLACEHOLDER_REGISTER_AS_config_file_path)

    if test ! -f $config_file_path
        return
    end

    set -l cache_path (__batect_completion_PLACEHOLDER_REGISTER_AS_cache_path $config_file_path)
    __batect_completion_PLACEHOLDER_REGISTER_AS_refresh_cache $config_file_path $cache_path

    set -l output (cat $cache_path)
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
