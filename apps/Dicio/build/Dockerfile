# SPDX-FileCopyrightText: 2026 RavenOS
# SPDX-License-Identifier: Apache-2.0
#
# Gradle build environment for the RavenOS fork of Dicio: JDK 21 (plus JDK 17, the toolchain
# dicio-numbers asks for) and Android SDK 36.
# Building it accepts the Android Software Development Kit License Agreement
# (https://developer.android.com/studio/terms), which the RavenOS maintainer has accepted.
FROM eclipse-temurin:21-jdk-jammy

ARG CMDLINE_TOOLS=commandlinetools-linux-11076708_latest.zip
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin

RUN apt-get update \
 && apt-get install -y --no-install-recommends unzip git ca-certificates wget \
      openjdk-17-jdk-headless \
 && rm -rf /var/lib/apt/lists/* \
 && mkdir -p $ANDROID_HOME/cmdline-tools \
 && wget -q https://dl.google.com/android/repository/$CMDLINE_TOOLS -O /tmp/tools.zip \
 && unzip -q /tmp/tools.zip -d $ANDROID_HOME/cmdline-tools \
 && mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest \
 && rm /tmp/tools.zip \
 && yes | sdkmanager --licenses >/dev/null \
 && sdkmanager "platforms;android-36" "build-tools;36.0.0" >/dev/null

WORKDIR /src
