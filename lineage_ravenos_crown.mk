# RavenOS for the Amazon Echo Show 8 (2019, crown).
# The product name has to start with lineage_ so the LineageOS build pieces
# (kernel build, board config, sepolicy) are turned on.

$(call inherit-product, $(SRC_TARGET_DIR)/product/full_base.mk)
$(call inherit-product, device/amazon/crown/device.mk)

# Core LineageOS only (SDK, settings provider, parts). The mobile/mini
# profiles are skipped on purpose: they pull in the bundled apps.
$(call inherit-product, vendor/lineage/config/common.mk)

$(call inherit-product, vendor/ravenos/ravenos.mk)

PRODUCT_NAME := lineage_ravenos_crown
PRODUCT_DEVICE := crown
PRODUCT_MANUFACTURER := Amzn
PRODUCT_BRAND := RavenOS
PRODUCT_MODEL := Echo Show 8
PRODUCT_CHARACTERISTICS := tablet,nosdcard
