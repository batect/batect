PLACEHOLDER_REGISTER_AS() {
    local word="${COMP_WORDS[COMP_CWORD]}"
    local word_type="option_or_task_name"
    local args_seen=()
    COMPREPLY=()

    for ((i = 1; i <= COMP_CWORD; i++)); do
        local current_word="${COMP_WORDS[i]}"
        local previous_word=""

        if [[ $i -gt 1 ]]; then
          previous_word="${COMP_WORDS[$((i - 1))]}"
        fi

        if [[ "$word_type" == "option_name" && "$previous_word" != "" ]] && PLACEHOLDER_REGISTER_AS_option_requires_value "$previous_word"; then
            word_type="option_value"
        elif [[ "$word_type" == "option_or_task_name" && "$previous_word" != "" ]]; then
            word_type="extra_args_separator"
        elif [[ "$word_type" == "extra_args_separator" && "$previous_word" == "--" ]]; then
            # This function is provided by bash-completion.
            _command_offset "$i"
            return 0
        else
           case "$current_word" in
           -*=*)
               word_type="option_with_value"
               args_seen+=("${current_word%%=*}")
               ;;
           -*)
               word_type="option_name"
               args_seen+=("$current_word")
               ;;
           *)
               word_type="option_or_task_name"
               ;;
           esac
        fi
    done

    case "$word_type" in
        option_or_task_name)
            PLACEHOLDER_REGISTER_AS_add_task_name_suggestions
            PLACEHOLDER_REGISTER_AS_add_option_suggestions
            ;;
        option_name)
            PLACEHOLDER_REGISTER_AS_add_option_suggestions
            ;;
        option_value)
            local option_name="${COMP_WORDS[$((COMP_CWORD - 1))]}"
            local option_value="$word"
            PLACEHOLDER_REGISTER_AS_add_option_value_suggestions "$option_name" "$option_value"
            ;;
        option_with_value)
            local option_name="${word%%=*}"
            local option_value="${word#*=}"
            PLACEHOLDER_REGISTER_AS_add_option_value_suggestions "$option_name" "$option_value"
            ;;
        extra_args_separator)
            COMPREPLY+=($(compgen -W "--" -- "$word"))
            ;;
        *)
            echo "Unknown word type: $word_type">/dev/stderr
            return 1
            ;;
    esac

    return 0
}

PLACEHOLDER_REGISTER_AS_add_task_name_suggestions() {
    local config_file_path
    config_file_path="$(PLACEHOLDER_REGISTER_AS_find_config_file_path)"

    local task_names
    task_names="$(PLACEHOLDER_REGISTER_AS_task_names "$config_file_path")"

    COMPREPLY+=($(compgen -W "$task_names" -- "$word"))
}

PLACEHOLDER_REGISTER_AS_add_option_suggestions() {
    PLACEHOLDER_ADD_SINGLE_USE_OPTIONS

    COMPREPLY+=($(compgen -W "PLACEHOLDER_MULTIPLE_VALUE_OPTION_NAMES" -- "$word"))
}

PLACEHOLDER_REGISTER_AS_add_single_use_option_suggestion() {
    local long_name="$1"
    local short_name="$2"

    for seen in "${args_seen[@]}" ; do
        if [[ "$seen" == "$long_name" ]]; then
            return
        fi

        if [[ "$seen" == "$short_name" && "$short_name" != "" ]]; then
            return
        fi
    done

    if [[ "$short_name" != "" ]]; then
        COMPREPLY+=($(compgen -W "$long_name $short_name" -- "$word"))
    else
        COMPREPLY+=($(compgen -W "$long_name" -- "$word"))
    fi
}

PLACEHOLDER_REGISTER_AS_option_requires_value() {
    local option_to_check="$1"
    local options_that_require_values=(PLACEHOLDER_OPTIONS_THAT_REQUIRE_VALUES)

    for option_that_requires_value in "${options_that_require_values[@]}" ; do
        if [[ "$option_to_check" == "$option_that_requires_value" ]]; then
            return 0
        fi
    done

    return 1
}

PLACEHOLDER_REGISTER_AS_add_option_value_suggestions() {
    local option_name="$1"
    local option_value="$2"

    case "$option_name" in
    # PLACEHOLDER_ADD_ENUM_VALUES
    *)
        PLACEHOLDER_REGISTER_AS_fallback_to_file_completion "$option_value"
        ;;
    esac
}

PLACEHOLDER_REGISTER_AS_fallback_to_file_completion() {
    local word="$1"
    COMPREPLY+=($(compgen -f -- "$word"))
}

PLACEHOLDER_REGISTER_AS_find_config_file_path() {
    local config_file_path="batect.yml"

    for ((i = 1; i < ${#COMP_WORDS[@]}; i++)); do
        local word="${COMP_WORDS[i]}"
        local next_word_index=$((i + 1))
        local next_word

        if [[ $next_word_index -le $((${#COMP_WORDS[@]} - 1)) ]]; then
            next_word="${COMP_WORDS[$next_word_index]}"
        fi

        case "$word" in
        -f=*)
            config_file_path="${word#-f=}"
            ;;
        --config-file=*)
            config_file_path="${word#--config-file=}"
            ;;
        --config-file | -f)
            if [[ -n "$next_word" ]]; then
                config_file_path="$next_word"
            fi
            ;;
        esac
    done

    echo "$config_file_path"
}

PLACEHOLDER_REGISTER_AS_task_names() {
    local config_file_path="$1"

    if [[ ! -f "$config_file_path" ]]; then
        return 1
    fi

    local cache_path
    cache_path="$(PLACEHOLDER_REGISTER_AS_cache_path "$config_file_path")"
    PLACEHOLDER_REGISTER_AS_refresh_cache "$config_file_path" "$cache_path"

    if [[ ! -f "$cache_path" ]]; then
        # We couldn't generate completion task information. Give up.
        return 1
    fi

    PLACEHOLDER_REGISTER_AS_get_tasks_from_cache "$cache_path"
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
    config_file_path_hash="$(echo "$config_file_path" | PLACEHOLDER_REGISTER_AS_sha256 - | cut -d' ' -f1)"

    echo "$HOME/.batect/completion/bash-v1/$config_file_path_hash"
}

PLACEHOLDER_REGISTER_AS_refresh_cache() {
    local config_file_path="$1"
    local cache_path="$2"

    if ! PLACEHOLDER_REGISTER_AS_need_to_refresh_cache "$config_file_path" "$cache_path"; then
        return 0
    fi

    mkdir -p "$(dirname "$cache_path")"

    if ! $BATECT_COMPLETION_PROXY_WRAPPER_PATH --generate-completion-task-info=bash --config-file="$config_file_path" >"$cache_path" 2>/dev/null; then
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
    hashes="$(PLACEHOLDER_REGISTER_AS_get_hashes_from_cache "$cache_path")"

    local files
    files="$(echo "$hashes" | cut -d' ' -f3)"

    while read -r file; do
        if [[ ! -f "$file" ]]; then
            return 0
        fi
    done < <(echo "$files")

    if echo "$hashes" | PLACEHOLDER_REGISTER_AS_sha256 -c --status -; then
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
