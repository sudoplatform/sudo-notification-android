/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudonotification

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo

/**
 * Default implementation of the [NotificationDeviceInputProvider] interface.
 */
class DefaultNotificationDeviceInputProvider(
    context: Context,
    override val deviceIdentifier: String,
    override val pushToken: String,
) : NotificationDeviceInputProvider {
    override lateinit var appVersion: String
    override lateinit var appName: String
    override lateinit var bundleIdentifier: String
    override lateinit var buildType: String
    override lateinit var locale: String

    init {
        val pInfo: PackageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            0,
        )
        this.appVersion = pInfo.versionName.toString()

        val aInfo: ApplicationInfo = context.applicationInfo
        this.appName = aInfo.loadLabel(context.packageManager).toString()

        if (0 != (aInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)) {
            this.buildType = "DEBUG"
        } else {
            this.buildType = "RELEASE"
        }

        this.bundleIdentifier = context.packageName

        this.locale = context.resources.configuration.locales.get(0).toString()
    }

    override val clientEnv: String
        get() = "ANDROID"
}
