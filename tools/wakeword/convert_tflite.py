# SPDX-FileCopyrightText: 2026 RavenOS
# SPDX-License-Identifier: Apache-2.0
"""Converts the trained hey_raven.onnx to the hey_raven.tflite that Dicio loads.

openWakeWord's own conversion runs in the training process, where torch and TensorFlow load
different protobuf runtimes and the process aborts; a separate process converts cleanly.
"""
import onnx
import tensorflow as tf
from onnx_tf.backend import prepare

prepare(onnx.load("/work/hey_raven_model/hey_raven.onnx"), device="CPU").export_graph("/tmp/hey_raven_tf")
converter = tf.lite.TFLiteConverter.from_saved_model("/tmp/hey_raven_tf")
with open("/work/hey_raven_model/hey_raven.tflite", "wb") as f:
    f.write(converter.convert())
