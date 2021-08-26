#! /usr/bin/env bash

set -euo pipefail

ISSUE_REPORTERS=$(gh issue list --state all --limit 9999999 --json 'author' --jq '.[].author.login')
PR_AUTHORS=$(gh pr list --state all --limit 9999999 --json 'author' --jq '.[].author.login')
EXTRAS=$(echo "safiranugroho"; echo "Byron-TW") # People that have provided feedback offline or commented on issues etc.
ALL_AUTHORS=$(echo "$ISSUE_REPORTERS"; echo "$PR_AUTHORS"; echo "$EXTRAS")
SORTED_LIST=$(echo "$ALL_AUTHORS" | sort --ignore-case | uniq | grep -vE '^(dependabot|dependabot-preview|renovate|charleskorn)$' | sed '/^$/d')

LINKS=$(echo "$SORTED_LIST" | sed 's#.*#[@&](https://github.com/&),\\#')

TEXT="${LINKS} and everyone else who has used the tool and provided feedback offline"
SED_COMMAND="/<!-- CONTRIBUTOR_LIST_STARTS_HERE -->/,/<!-- CONTRIBUTOR_LIST_ENDS_HERE -->/c\\
<!-- CONTRIBUTOR_LIST_STARTS_HERE -->\\
$TEXT\\
<!-- CONTRIBUTOR_LIST_ENDS_HERE -->\\
"

sed -i '' "$SED_COMMAND" README.md
