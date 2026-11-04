#!/usr/bin/env bash
# Builds RavenOS for the Echo Show 8 (crown).
#   bash vendor/ravenos/build-ravenos.sh            # full flashable zip
#   bash vendor/ravenos/build-ravenos.sh RavenHome  # just one module
# Run it niced on a shared machine:  nice -n 18 ionice -c3 bash ...
TREE="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$TREE" || exit 1
export PATH="$HOME/.local/bin:$PATH"
export LC_ALL=C.UTF-8
# ccache is optional: use it when it's installed, build without it otherwise.
CCACHE_EXEC="${CCACHE_EXEC:-$(command -v ccache || true)}"
if [ -n "$CCACHE_EXEC" ] && [ -x "$CCACHE_EXEC" ]; then
    export USE_CCACHE=1 CCACHE_EXEC
    export CCACHE_DIR="${CCACHE_DIR:-$TREE/../.ccache}"
    mkdir -p "$CCACHE_DIR"
    "$CCACHE_EXEC" -M 50G >/dev/null || echo "warning: couldn't set the ccache size" >&2
    "$CCACHE_EXEC" -o compression=true >/dev/null || echo "warning: couldn't turn on ccache compression" >&2
else
    unset USE_CCACHE CCACHE_EXEC
    echo "ccache not found; building without it"
fi
# The prebuilt mke2fs 1.45 rejects a newer host /etc/mke2fs.conf (orphan_file),
# which breaks apexer. Use the in-tree config, and let soong pass it to ninja.
export MKE2FS_CONFIG="$TREE/system/extras/ext4_utils/mke2fs.conf"
export ALLOW_NINJA_ENV=true
# Keep the local user and hostname out of build.prop and the fingerprint.
export BUILD_USERNAME=ravenos BUILD_HOSTNAME=ravenos
# Unique per build: a new fingerprint makes Android drop its package cache after a dirty
# flash (otherwise apps whose resource IDs changed show the wrong labels and themes).
# Kept short so ro.build.fingerprint stays within the 91 characters property_get can read.
export BUILD_NUMBER="rv$(date +%y%m%d%H%M)"

# The RavenOS patches are commits in each project. A repo sync can drop them, and the build
# would then quietly produce stock LineageOS, so stop if any project is missing one.
missing=0
while IFS= read -r patch; do
    project="$(dirname "${patch#vendor/ravenos/patches/}")"
    subject="$(sed -n 's/^Subject: \[PATCH[^]]*\] //p' "$patch" | head -1)"
    # Long subjects are wrapped in the patch file, so match the first line as a prefix.
    if ! git -C "$project" log --format=%s -n 300 2>/dev/null | grep -qF -- "$subject"; then
        echo "Missing RavenOS patch in $project: $(basename "$patch")" >&2
        missing=1
    fi
done < <(find vendor/ravenos/patches -type f -name "*.patch" | sort)
if [ "$missing" -ne 0 ]; then
    echo "Apply them with: bash vendor/ravenos/patches/apply.sh" >&2
    exit 1
fi

# The About screen's version string is a resource, so it can't read RAVENOS_VERSION.
version=$(sed -n 's/^RAVENOS_VERSION := //p' vendor/ravenos/ravenos.mk)
if ! grep -qF "RavenOS $version " vendor/ravenos/overlay/lineage-sdk/lineage/res/res/values/strings.xml; then
    echo "lineage_version in vendor/ravenos/overlay/lineage-sdk/.../strings.xml doesn't say RavenOS $version" >&2
    exit 1
fi

source build/envsetup.sh
lunch lineage_ravenos_crown-userdebug || exit 1

target="${1:-bacon}"
# Android 11 doesn't regenerate build.prop when only BUILD_NUMBER or the date change, so
# the fingerprint would stay frozen. Remove them so every full build gets its own.
if [ "$target" = bacon ]; then
    find "$OUT" -maxdepth 3 -name build.prop -not -path "*/obj/*" -delete 2>/dev/null
    rm -f "$OUT"/obj/ETC/*build_prop_intermediates/build.prop
fi
echo "=== BUILD START $(date -Is) target=$target ==="
mka -j"${JOBS:-6}" "$target"
rc=$?
if [ $rc -eq 0 ] && [ "$target" = bacon ]; then
    src=$(ls -t "$OUT"/lineage-*-ravenos_crown.zip 2>/dev/null | head -1)
    dst="$OUT/RavenOS-$version-$(date +%Y%m%d)-crown.zip"
    if [ -z "$src" ]; then
        echo "The build finished but left no lineage-*-ravenos_crown.zip in $OUT" >&2
        rc=1
    elif ln -f "$src" "$dst" \
            && (cd "$OUT" && sha256sum "$(basename "$dst")" > "$(basename "$dst").sha256"); then
        echo "RavenOS zip: $dst"
    else
        echo "Couldn't link $src to $dst or write its checksum" >&2
        rc=1
    fi
fi
echo "=== BUILD EXIT code=$rc at $(date -Is) ==="
exit $rc
