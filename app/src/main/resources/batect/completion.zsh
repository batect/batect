PLACEHOLDER_REGISTER_AS() {
    typeset -A opt_args
    local context state line

    local config_file_path
    config_file_path="batect.yml"

    local -a args
    args=(
        PLACEHOLDER_OPTION_DEFINITIONS
        '1:task_name:->task_name'
        '2:separator:(--)'
        '*::: :_normal'
    )

    _arguments "${args[@]}" && return 0

    (( $+opt_args[-f] )) && config_file_path=${opt_args[-f]}
    (( $+opt_args[--config-file] )) && config_file_path=${opt_args[--config-file]}

    # The value in opt_args is exactly as the user typed it (including quotes and escapes),
    # so we need to unquote and unescape it.
    # See https://github.com/zsh-users/zsh/blob/57a735f/Etc/completion-style-guide#L560.
    config_file_path="${(Q)${(Q)config_file_path}}"

    if [[ -f "$config_file_path" ]]; then
        config_file_path=$(realpath "$config_file_path")
    fi

    case $state in
        (task_name)
            PLACEHOLDER_REGISTER_AS_task_names "$config_file_path" && return 0
        ;;
    esac

    return 1
}

PLACEHOLDER_REGISTER_AS_task_names() {
    local config_file_path=$1

    if [[ ! -f "$config_file_path" ]]; then
        return 1
    fi

    local cache_path
    cache_path=$(PLACEHOLDER_REGISTER_AS_cache_path "$config_file_path")
    PLACEHOLDER_REGISTER_AS_refresh_cache "$config_file_path" "$cache_path"

    if [[ ! -f "$cache_path" ]]; then
        # We couldn't generate completion task information. Give up.
        return 1
    fi

    local tasks
    tasks=("${(@f)$(PLACEHOLDER_REGISTER_AS_get_tasks_from_cache "$cache_path")}")

    _describe -t tasks 'task' tasks && return 0

    return 1
}

PLACEHOLDER_REGISTER_AS_sha256() {
    if [[ "$(uname)" == "Darwin" ]]; then
        shasum -a 256 "$@"
    else
        sha256sum "$@"
    fi
}

PLACEHOLDER_REGISTER_AS_cache_path() {
    local config_file_path="$1"
    local config_file_path_hash
    config_file_path_hash=$(echo "$config_file_path" | PLACEHOLDER_REGISTER_AS_sha256 - | cut -d' ' -f1)

    echo "$HOME/.batect/completion/zsh-v1/$config_file_path_hash"
}

PLACEHOLDER_REGISTER_AS_refresh_cache() {
    local config_file_path="$1"
    local cache_path="$2"

    if ! PLACEHOLDER_REGISTER_AS_need_to_refresh_cache "$config_file_path" "$cache_path"; then
        return 0
    fi

    mkdir -p "$(dirname "$cache_path")"

    if ! $BATECT_COMPLETION_PROXY_WRAPPER_PATH --generate-completion-task-info=zsh --config-file="$config_file_path" >"$cache_path" 2>/dev/null; then
        rm -f "$cache_path"
    fi
}

PLACEHOLDER_REGISTER_AS_need_to_refresh_cache() {
    local config_file_path="$1"
    local cache_path="$2"

    if [[ ! -f "$cache_path" ]]; then
        return 0
    fi

    local hashes
    hashes=$(PLACEHOLDER_REGISTER_AS_get_hashes_from_cache "$cache_path")

    local files
    files=$(echo "$hashes" | cut -d' ' -f3)

    echo "$files" | while read -r file; do
        if [[ ! -f "$file" ]]; then
            return 0
        fi
    done

    if echo "$hashes" | PLACEHOLDER_REGISTER_AS_sha256 -c -s -; then
        return 1
    else
        return 0
    fi
}

PLACEHOLDER_REGISTER_AS_get_tasks_from_cache() {
    local cache_path="$1"

    sed -e '1,/### TASKS ###/d' "$cache_path"
}

PLACEHOLDER_REGISTER_AS_get_hashes_from_cache() {
    local cache_path="$1"

    sed -n -e '/### FILES ###/,$p' "$cache_path" | tail -n +2 | sed -e '/### TASKS ###/,$d'
}
