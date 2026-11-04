# SPDX-FileCopyrightText: 2026 RavenOS
# SPDX-License-Identifier: Apache-2.0
"""Downloads and converts the "Hey Raven" training data into /work (runs in the container).

Every step is skipped when its output already exists, so an interrupted run can be resumed.
"""

import io
import math
import subprocess
from pathlib import Path

import datasets
import numpy as np
import pyarrow.parquet
import scipy.io.wavfile
import scipy.signal
import soundfile
from tqdm import tqdm

WORK = Path("/work")
OWW_RELEASE = "https://github.com/dscripka/openWakeWord/releases/download/v0.5.1"
FEATURES = "https://huggingface.co/datasets/davidscripka/openwakeword_features/resolve/main"
PIPER_MODEL = ("https://github.com/rhasspy/piper-sample-generator/releases/download/v2.0.0/"
               "en_US-libritts_r-medium.pt")
# One ~700 MB shard of the balanced AudioSet training set (published as Parquet).
AUDIOSET_PART = ("https://huggingface.co/datasets/agkphysics/AudioSet/resolve/main/"
                 "data/bal_train/09.parquet")
FMA_HOURS = 3


def fetch(url, dest):
    dest = Path(dest)
    dest.parent.mkdir(parents=True, exist_ok=True)
    # wget -c resumes a partial download of the 17 GB feature file.
    subprocess.run(["wget", "-q", "--show-progress", "-c", "-O", str(dest), url], check=True)


def write_wav(path, audio):
    scipy.io.wavfile.write(path, 16000, (np.asarray(audio) * 32767).astype(np.int16))


def done(marker):
    return (WORK / marker).exists()


def mark(marker):
    (WORK / marker).touch()


def main():
    for name in ["melspectrogram", "embedding_model"]:
        for ext in ["onnx", "tflite"]:
            dest = WORK / "oww_models" / f"{name}.{ext}"
            if not dest.exists():
                fetch(f"{OWW_RELEASE}/{name}.{ext}", dest)

    if not (WORK / "en_US-libritts_r-medium.pt").exists():
        fetch(PIPER_MODEL, WORK / "en_US-libritts_r-medium.pt")

    if not done(".rirs_done"):
        out = WORK / "mit_rirs"
        out.mkdir(exist_ok=True)
        rirs = datasets.load_dataset("davidscripka/MIT_environmental_impulse_responses",
                                     split="train", streaming=True)
        for row in tqdm(rirs, desc="room echoes"):
            write_wav(out / Path(row["audio"]["path"]).name, row["audio"]["array"])
        mark(".rirs_done")

    if not done(".audioset_done"):
        shard = WORK / "audioset" / "bal_train_09.parquet"
        if not done(".audioset_shard_done"):
            fetch(AUDIOSET_PART, shard)
            mark(".audioset_shard_done")
        out = WORK / "audioset_16k"
        out.mkdir(exist_ok=True)
        # Read the shard with pyarrow: its schema metadata is newer than datasets 2.14 knows.
        table = pyarrow.parquet.read_table(str(shard), columns=["audio"])
        for i, clip in enumerate(tqdm(table.column("audio").to_pylist(),
                                      desc="audioset to 16 kHz")):
            audio, rate = soundfile.read(io.BytesIO(clip["bytes"]), dtype="float32")
            if audio.ndim > 1:
                audio = audio.mean(axis=1)
            if rate != 16000:
                divisor = math.gcd(rate, 16000)
                audio = scipy.signal.resample_poly(audio, 16000 // divisor, rate // divisor)
            write_wav(out / f"audioset_{i:05d}.wav", np.clip(audio, -1, 1))
        shard.unlink(missing_ok=True)
        mark(".audioset_done")

    if not done(".fma_done"):
        out = WORK / "fma"
        out.mkdir(exist_ok=True)
        fma = iter(datasets.load_dataset("rudraml/fma", name="small", split="train",
                                         streaming=True)
                   .cast_column("audio", datasets.Audio(sampling_rate=16000)))
        # FMA "small" clips are 30 s each.
        for _ in tqdm(range(FMA_HOURS * 3600 // 30), desc="music"):
            row = next(fma)
            write_wav(out / Path(row["audio"]["path"]).with_suffix(".wav").name,
                      row["audio"]["array"])
        mark(".fma_done")

    for name in ["validation_set_features.npy", "openwakeword_features_ACAV100M_2000_hrs_16bit.npy"]:
        if not done(f".{name}_done"):
            fetch(f"{FEATURES}/{name}", WORK / name)
            mark(f".{name}_done")

    print("Training data ready in", WORK)


if __name__ == "__main__":
    main()
