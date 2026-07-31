# Use Ubuntu as base and install Android SDK
FROM ubuntu:24.04

# Set environment variables
ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    PATH=$PATH:/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools \
    JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64

# Install dependencies
RUN apt-get update && apt-get install -y \
    openjdk-21-jdk-headless \
    wget \
    unzip \
    git \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Create SDK directory
RUN mkdir -p $ANDROID_HOME/cmdline-tools

# Download Android SDK command-line tools
RUN cd $ANDROID_HOME/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip && \
    unzip -q commandlinetools-linux-11076708_latest.zip && \
    rm commandlinetools-linux-11076708_latest.zip && \
    mv cmdline-tools latest

# Accept licenses and install Android SDK components
RUN yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses && \
    $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager \
    "platforms;android-34" \
    "build-tools;34.0.0" \
    "ndk;26.1.10909125" \
    --channel=0

# Install Gradle
RUN cd /opt && \
    wget -q https://services.gradle.org/distributions/gradle-8.1-bin.zip && \
    unzip -q gradle-8.1-bin.zip && \
    rm gradle-8.1-bin.zip && \
    ln -s /opt/gradle-8.1/bin/gradle /usr/local/bin/gradle

# Set working directory
WORKDIR /app

# Copy project files
COPY . .

# Make gradlew executable
RUN chmod +x gradlew

# Build the APK
RUN gradle clean assembleDebug

# Create output directory and copy APK
RUN mkdir -p /app/output && \
    cp app/build/outputs/apk/debug/app-debug.apk /app/output/app-debug.apk

CMD ["/bin/bash"]
