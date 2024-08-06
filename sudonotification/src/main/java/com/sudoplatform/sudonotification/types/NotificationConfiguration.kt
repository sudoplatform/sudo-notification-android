/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudonotification.types

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize
import java.util.*

@Parcelize
data class NotificationFilterItem(
    @SerializedName("uuid") val uuid: UUID? = UUID.randomUUID(),
    @SerializedName("name") val name: String,
    @SerializedName("status") var status: String? = NotificationConfiguration.ENABLE_STR,
    @SerializedName("rules") var rules: String? = NotificationConfiguration.DEFAULT_RULE_STRING,
    @SerializedName("meta") var meta: String? = "",
) : Parcelable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NotificationFilterItem) return false

        if (name != other.name) return false
        if (status != other.status) return false
        if (rules != other.rules) return false
        if (meta != other.meta) return false

        return true
    }
}

data class NotificationConfiguration(
    val version: String = "v1",
    val configs: List<NotificationFilterItem>,
) {
    companion object {
        /** Status enums  */
        const val ENABLE_STR = "ENABLE"
        const val DISABLE_STR = "DISABLE"

        const val DEFAULT_RULE_STRING = "{\"==\" : [ 1, 1]}"
    }
    fun updateConfig(uuid: UUID, status: Boolean, rules: String = "", meta: String = "") {
        configs.find { it.uuid == uuid }?.let {
            if (meta.isNotEmpty()) {
                it.meta = meta
            }
            if (rules.isNotEmpty()) {
                it.rules = rules
            }
            if (status) {
                it.status = ENABLE_STR
            } else {
                it.status = DISABLE_STR
            }
        }
    }
}

data class NotificationSettingsInput(
    val bundleId: String,
    val deviceId: String,
    val filter: List<NotificationFilterItem>,
    val services: List<NotificationMetaData>,
)
