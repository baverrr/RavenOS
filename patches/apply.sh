#!/usr/bin/env bash
# Applies the RavenOS patches to a synced LineageOS 18.1 tree, after the amazon-oss ones
# (patches/apply.sh at the top of the tree). Each folder here mirrors the project it patches:
#   patches/frameworks/base/0009-....patch  ->  git am in <tree>/frameworks/base
#   bash vendor/ravenos/patches/apply.sh
set -e
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TREE="$(cd "$HERE/../../.." && pwd)"

find "$HERE" -type f -name "*.patch" | sort | while IFS= read -r patch; do
    project="$(dirname "${patch#$HERE/}")"
    echo "Applying $(basename "$patch") in $project"
    [ -d "$TREE/$project" ] || { echo "Missing project: $TREE/$project"; exit 1; }
    (cd "$TREE/$project" && git am -q --3way "$patch")
done
echo "Done."
