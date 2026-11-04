#!/usr/bin/env bash
# Builds RavenOS for the Echo Show 8 (crown).
#   bash vendor/ravenos/build-ravenos.sh            # full flashable zip
#   bash vendor/ravenos/build-ravenos.sh RavenHome  # just one module
# Run it niced on a shared machine:  nice -n 18 ionice -c3 bash ...
TREE="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$TREE" || exit 1
export PATH="$HOME/.local/bin:$PATH"
export LC_ALL=C.UTF-8
export USE_CCACHE=1 CCACHE_EXEC="${CCACHE_EXEC:-/usr/bin/ccache}"
export CCACHE_DIR="${CCACHE_DIR:-$TREE/../.ccache}"
mkdir -p "$CCACHE_DIR"
ccache -M 50G >/dev/null 2>&1
ccache -o compression=true >/dev/null 2>&1
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
    version=$(sed -n 's/^RAVENOS_VERSION := //p' vendor/ravenos/ravenos.mk)
    src=$(ls -t "$OUT"/lineage-*-ravenos_crown.zip | head -1)
    dst="$OUT/RavenOS-$version-$(date +%Y%m%d)-crown.zip"
    ln -f "$src" "$dst" && (cd "$OUT" && sha256sum "$(basename "$dst")" > "$(basename "$dst").sha256")
    echo "RavenOS zip: $dst"
fi
echo "=== BUILD EXIT code=$rc at $(date -Is) ==="
exit $rc
