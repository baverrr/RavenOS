#!/usr/bin/env bash
# Applies the RavenOS patches to a synced LineageOS 18.1 tree, after the amazon-oss ones
# (patches/apply.sh at the top of the tree). Each folder here mirrors the project it patches:
#   patches/frameworks/base/0009-....patch  ->  git am in <tree>/frameworks/base
#   bash vendor/ravenos/patches/apply.sh
# Safe to run again: patches whose subject is already among the project's recent commits are
# skipped, so after a failure you can fix the cause and re-run.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TREE="$(cd "$HERE/../../.." && pwd)"

recent_subjects() {
    git -C "$1" log --format=%s -n 300
}

applied=0
skipped=0
current=""
subjects=""
while IFS= read -r patch; do
    project="$(dirname "${patch#"$HERE"/}")"
    dir="$TREE/$project"
    [ -d "$dir" ] || { echo "Missing project: $dir" >&2; exit 1; }
    if [ "$project" != "$current" ]; then
        current="$project"
        subjects="$(recent_subjects "$dir")"
    fi
    # Long subjects are wrapped in the patch file, so match its first line as a prefix.
    subject="$(sed -n 's/^Subject: \[PATCH[^]]*\] //p' "$patch" | head -1)"
    if [ -n "$subject" ] && grep -qF -- "$subject" <<< "$subjects"; then
        skipped=$((skipped + 1))
        continue
    fi
    echo "Applying $(basename "$patch") in $project"
    if ! git -C "$dir" am -q --3way "$patch"; then
        git -C "$dir" am --abort || true
        echo "Failed to apply $(basename "$patch") in $project; the project is back where it" >&2
        echo "was before this patch. Fix the conflict, then run this script again." >&2
        exit 1
    fi
    applied=$((applied + 1))
done < <(find "$HERE" -type f -name "*.patch" | sort)
echo "Done: $applied applied, $skipped already in place."
