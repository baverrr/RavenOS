/*
 * RavenOS Bluetooth build config. Takes the crown device's settings and only
 * swaps the name the Echo advertises when pairing. The Bluetooth stack uses this
 * compiled-in name and ignores bluetooth.device.default_name.
 */
#ifndef _RAVENOS_BDROID_BUILDCFG_H
#define _RAVENOS_BDROID_BUILDCFG_H

#include "../../../device/amazon/crown/bluetooth/bdroid_buildcfg.h"

#undef BTM_DEF_LOCAL_NAME
#define BTM_DEF_LOCAL_NAME "RavenOS"

#endif
