LOCAL_PATH := $(call my-dir)

# Installs nothing; its only job is to drop these packages from the image.
include $(CLEAR_VARS)
LOCAL_MODULE := RavenRemovePackages
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_TAGS := optional
LOCAL_OVERRIDES_PACKAGES := \
    Backgrounds \
    BasicDreams \
    BookmarkProvider \
    BuiltInPrintService \
    Contacts \
    CtsShimPrebuilt \
    CtsShimPrivPrebuilt \
    DeskClock \
    DynamicSystemInstallationService \
    EasterEgg \
    Eleven \
    Etar \
    ExactCalculator \
    Gallery2 \
    Jelly \
    LineageSetupWizard \
    LiveWallpapersPicker \
    LocalContactsBackup \
    ManagedProvisioning \
    OneTimeInitializer \
    PhotoTable \
    PrintRecommendationService \
    PrintSpooler \
    QuickAccessWallet \
    Recorder \
    Seedvault \
    SimAppDialog \
    ThemePicker \
    Traceur \
    TrebuchetQuickStep \
    Updater
LOCAL_UNINSTALLABLE_MODULE := true
LOCAL_CERTIFICATE := PRESIGNED
LOCAL_SRC_FILES := /dev/null
include $(BUILD_PREBUILT)
