# SPDX-FileCopyrightText: 2026 RavenOS
# SPDX-License-Identifier: Apache-2.0
#
# CPU-only environment for training the "Hey Raven" OpenWakeWord model. Versions follow
# openWakeWord's automatic_model_training notebook; TensorFlow 2.8 (used only to export the
# .tflite file Dicio loads) needs Python 3.10, numpy < 1.24 and protobuf < 3.20.
FROM python:3.10-slim-bookworm

RUN apt-get update \
 && apt-get install -y --no-install-recommends git wget ca-certificates libsndfile1 ffmpeg \
      build-essential \
 && rm -rf /var/lib/apt/lists/*

RUN pip install --no-cache-dir \
      --extra-index-url https://download.pytorch.org/whl/cpu \
      torch==1.13.1+cpu torchaudio==0.13.1+cpu \
 && pip install --no-cache-dir \
      numpy==1.23.5 protobuf==3.19.6 \
      tensorflow-cpu==2.8.1 tensorflow_probability==0.16.0 onnx==1.12.0 onnx_tf==1.10.0 \
      onnxruntime==1.16.3 scipy==1.11.4 scikit-learn==1.3.2 tqdm requests pyyaml==6.0.1 \
      mutagen==1.47.0 torchinfo==1.8.0 torchmetrics==0.11.4 speechbrain==0.5.14 \
      audiomentations==0.33.0 torch-audiomentations==0.11.0 acoustics==0.2.6 \
      pronouncing==0.2.0 datasets==2.14.6 deep-phonemizer==0.0.19 \
      piper-phonemize==1.1.0 webrtcvad==2.0.10 soundfile==0.12.1

# datasets 2.14 uses pyarrow.PyExtensionType, which pyarrow 21 removed.
RUN pip install --no-cache-dir pyarrow==14.0.2

# Sources pinned to the commits this model was trained with.
ARG OWW_COMMIT=368c03716d1e92591906a84949bc477f3a834455
ARG PIPER_GEN_COMMIT=195e3bd967d54589c2137c9de2b22ad526ba6b6f
WORKDIR /opt
RUN git clone https://github.com/dscripka/openWakeWord openwakeword \
 && git -C openwakeword checkout "$OWW_COMMIT" \
 && pip install --no-cache-dir --no-deps -e ./openwakeword \
 && git clone https://github.com/rhasspy/piper-sample-generator \
 && git -C piper-sample-generator checkout "$PIPER_GEN_COMMIT"

WORKDIR /work
