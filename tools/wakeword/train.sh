#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 RavenOS
# SPDX-License-Identifier: Apache-2.0
#
# Trains the "Hey Raven" wake word for Dicio on the CPU, in Docker.
#   bash vendor/ravenos/tools/wakeword/train.sh            # all steps
#   bash vendor/ravenos/tools/wakeword/train.sh train      # resume from one step
# Steps: image, data, generate, augment, train, convert. About 22 GB of data goes to $WAKEWORD_DATA
# (default /mnt/media/ravenos/wakeword). The result is copied to
# vendor/ravenos/wakeword/hey_raven.tflite.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAVENOS="$(cd "$HERE/../.." && pwd)"
DATA="${WAKEWORD_DATA:-/mnt/media/ravenos/wakeword}"
IMAGE=ravenos-wakeword-train
STEPS=(image data generate augment train convert)
FROM="${1:-image}"
if [[ " ${STEPS[*]} " != *" $FROM "* ]]; then
    echo "Unknown step '$FROM'. Steps: ${STEPS[*]}" >&2
    exit 1
fi

mkdir -p "$DATA"

run() {
    # Low priority so the PC stays usable; the config is mounted read-only.
    docker run --rm --shm-size=4g --cpus="${WAKEWORD_CPUS:-8}" \
        -v "$DATA:/work" -v "$HERE/hey_raven.yml:/opt/hey_raven.yml:ro" \
        -v "$HERE/prepare_data.py:/opt/prepare_data.py:ro" \
        -v "$HERE/convert_tflite.py:/opt/convert_tflite.py:ro" \
        -e HF_HOME=/work/hf-cache \
        "$IMAGE" nice -n 15 bash -c "
            mkdir -p /opt/openwakeword/openwakeword/resources
            ln -sfn /work/oww_models /opt/openwakeword/openwakeword/resources/models
            mkdir -p /opt/piper-sample-generator/models
            ln -sf /work/en_US-libritts_r-medium.pt /opt/piper-sample-generator/models/
            $1"
}

started=false
for step in "${STEPS[@]}"; do
    [ "$step" = "$FROM" ] && started=true
    $started || continue
    echo "=== $step $(date -Is) ==="
    case "$step" in
        image)    docker build -t "$IMAGE" "$HERE" ;;
        data)     run "python -I /opt/prepare_data.py" ;;
        generate) run "python /opt/openwakeword/openwakeword/train.py --training_config /opt/hey_raven.yml --generate_clips" ;;
        augment)  run "python /opt/openwakeword/openwakeword/train.py --training_config /opt/hey_raven.yml --augment_clips" ;;
        # Training saves the ONNX model, then aborts in its own TFLite conversion (see
        # convert_tflite.py), so success here means a fresh ONNX file.
        train)    run "rm -f /work/hey_raven_model/hey_raven.onnx; python /opt/openwakeword/openwakeword/train.py --training_config /opt/hey_raven.yml --train_model || test -s /work/hey_raven_model/hey_raven.onnx" ;;
        convert)  run "python -I /opt/convert_tflite.py" ;;
    esac
done

mkdir -p "$RAVENOS/wakeword"
cp "$DATA/hey_raven_model/hey_raven.tflite" "$RAVENOS/wakeword/hey_raven.tflite"
echo "=== done $(date -Is): $RAVENOS/wakeword/hey_raven.tflite ==="
