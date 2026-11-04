# RavenOS board overrides. Loaded through vendor/extra/BoardConfigExtra.mk, which the
# build reads after the device's BoardConfig.mk, so these win over device/amazon/crown.
ifeq ($(TARGET_PRODUCT),lineage_ravenos_crown)

# Advertise as "RavenOS" when pairing instead of "Echo Show 8".
BOARD_BLUETOOTH_BDROID_BUILDCFG_INCLUDE_DIR := vendor/ravenos/bluetooth

endif
