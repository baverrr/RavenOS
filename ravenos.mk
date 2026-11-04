# RavenOS common configuration.

# Also shown in Settings › About: keep overlay/lineage-sdk/lineage/res/res/values/strings.xml
# (lineage_version) in step. build-ravenos.sh stops if the two differ.
RAVENOS_VERSION := 1.1

PRODUCT_PACKAGE_OVERLAYS += vendor/ravenos/overlay

# Glass theme for Settings and the keyboard. Every variant is kept in vendor/ravenos/themes/
# (see THEME.md); only the one named here is built into the image. Override it per build with
#   RAVENOS_GLASS_THEME=tide bash vendor/ravenos/build-ravenos.sh
RAVENOS_GLASS_THEME ?= obsidian
ifeq ($(wildcard vendor/ravenos/themes/$(RAVENOS_GLASS_THEME)/overlay),)
$(error RAVENOS_GLASS_THEME=$(RAVENOS_GLASS_THEME) has no folder in vendor/ravenos/themes)
endif
PRODUCT_PACKAGE_OVERLAYS += vendor/ravenos/themes/$(RAVENOS_GLASS_THEME)/overlay
# Compile the colors into Settings and LatinIME instead of shipping them as separate RROs.
PRODUCT_ENFORCE_RRO_EXCLUDED_OVERLAYS += vendor/ravenos/themes/$(RAVENOS_GLASS_THEME)/overlay

PRODUCT_PACKAGES += \
    LatinIME \
    RavenHome \
    RavenRemovePackages \
    RavenWifiOverlay

# Voice control: Dicio with the "Hey Raven" wake word, reminders and the sleep alarm, and
# RHVoice to speak. See VOICE.md.
PRODUCT_PACKAGES += \
    Dicio \
    RHVoice

# Drop stock features RavenOS doesn't use (printing), grant Camera2 what it needs, and map the
# Portal TV remote's buttons.
PRODUCT_COPY_FILES += \
    vendor/ravenos/etc/permissions/ravenos-unavailable-features.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/permissions/ravenos-unavailable-features.xml \
    vendor/ravenos/etc/default-permissions/default-permissions-com.android.camera2.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/default-permissions/default-permissions-com.android.camera2.xml \
    vendor/ravenos/keylayout/Vendor_1915_Product_eeee.kl:$(TARGET_COPY_OUT_SYSTEM)/usr/keylayout/Vendor_1915_Product_eeee.kl

# Own boot animation instead of the generated LineageOS one.
TARGET_BOOTANIMATION := vendor/ravenos/bootanimation/bootanimation.zip

PRODUCT_SYSTEM_DEFAULT_PROPERTIES += \
    ro.ravenos.version=$(RAVENOS_VERSION) \
    persist.sys.usb.config=adb \
    persist.adb.tcp.port=5555

PRODUCT_PRODUCT_PROPERTIES += \
    bluetooth.device.default_name=RavenOS \
    ro.config.alarm_alert=Hassium.ogg
