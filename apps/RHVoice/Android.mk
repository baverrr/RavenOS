# SPDX-FileCopyrightText: 2026 RavenOS
# SPDX-License-Identifier: Apache-2.0
#
# RHVoice, the offline text-to-speech engine Dicio speaks with. RHVoice.apk is the F-Droid
# build of version 1.18.4 (versionCode 118040), checked against F-Droid's index:
#   sha256 fdb0f7d6f3ec455477a84aa8cc86d1f2d580cb180d69a0c780240975c2efd288
# It keeps its F-Droid signature, so F-Droid can update it. Voices are downloaded once from
# inside the app; see vendor/ravenos/VOICE.md.
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := RHVoice
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)
LOCAL_SRC_FILES := RHVoice.apk
LOCAL_CERTIFICATE := PRESIGNED
LOCAL_MULTILIB := 32
LOCAL_DEX_PREOPT := false
LOCAL_PREBUILT_JNI_LIBS := \
    @lib/armeabi-v7a/libRHVoice_jni.so \
    @lib/armeabi-v7a/libconscrypt_jni.so
include $(BUILD_PREBUILT)
