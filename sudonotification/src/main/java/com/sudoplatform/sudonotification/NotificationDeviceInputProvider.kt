/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudonotification

interface NotificationDeviceInputProvider {
    /*
     *  String representation of the build type to assign to a device or device info.
     */
    val buildType: String

    /*
     *  String representation of client env (OS)
     */
    val clientEnv: String

    /*
     *  Unique identifier for the device.
     */
    val deviceIdentifier: String

    /*
     *  App name, e.g. "MYSUDO". Used to route the push notifications to the correct app.
     */
    val appName: String

    /*
     * The bundle identifier to assign to any devices or device info.
     */
    val bundleIdentifier: String

    /*
     * String representation of the application version a device is for.
     */
    val appVersion: String

    /*
     * The locale identifier for the device.
     */
    val locale: String

    /*
     * The registered FCM token used for delivering push notification
     */
    val pushToken: String
}
