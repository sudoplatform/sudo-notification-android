/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudonotification

import android.content.Context
import com.google.firebase.messaging.RemoteMessage
import com.sudoplatform.sudoapiclient.ApiClientManager
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudonotification.logging.LogConstants
import com.sudoplatform.sudonotification.types.NotifiableClient
import com.sudoplatform.sudonotification.types.NotificationConfiguration
import com.sudoplatform.sudonotification.types.NotificationSettingsInput
import com.sudoplatform.sudouser.SudoUserClient
import com.sudoplatform.sudouser.amplify.GraphQLClient
import java.util.Objects

/**
 * Interface encapsulating a library for interacting with the Sudo Platform Notification service.
 *
 * @sample com.sudoplatform.sudonotification.samples.Samples.sudoNotificationClient
 */
interface SudoNotificationClient {

    companion object {
        /** Create a [Builder] for [SudoNotificationClient]. */
        @JvmStatic
        fun builder() = Builder()
    }

    /**
     * Builder used to construct the [SudoNotificationClient].
     */
    class Builder internal constructor() {
        private var context: Context? = null
        private var sudoUserClient: SudoUserClient? = null
        private var graphQLClient: GraphQLClient? = null
        private var logger: Logger =
            Logger(LogConstants.SUDOLOG_TAG, AndroidUtilsLogDriver(LogLevel.INFO))
        private var notifiableServices: List<NotifiableClient>? = emptyList()

        /**
         * Provide the application context (required input).
         */
        fun setContext(context: Context) = also {
            this.context = context
        }

        /**
         * Provide the implementation of the [SudoUserClient] used to perform
         * sign in and ownership operations (required input).
         */
        fun setSudoUserClient(sudoUserClient: SudoUserClient) = also {
            this.sudoUserClient = sudoUserClient
        }

        /**
         * Provide the implementation of [NotifiableClient] (required input)
         */
        fun setNotifiableClients(notifiableServices: List<NotifiableClient>) = also {
            this.notifiableServices = notifiableServices
        }

        /**
         * Provide an [GraphQLClient] for the [SudoNotificationClient] to use
         * (optional input). If this is not supplied, an [GraphQLClient] will
         * be constructed and used.
         */
        fun setGraphQLClient(graphQLClient: GraphQLClient) = also {
            this.graphQLClient = graphQLClient
        }

        /**
         * Provide the implementation of the [Logger] used for logging errors (optional input).
         * If a value is not supplied a default implementation will be used.
         */
        fun setLogger(logger: Logger) = also {
            this.logger = logger
        }

        /**
         * Construct the [SudoNotificationClient]. Will throw a [NullPointerException] if
         * the [context] and [sudoUserClient] has not been provided.
         */
        @Throws(NullPointerException::class)
        fun build(): DefaultSudoNotificationClient {
            Objects.requireNonNull(context, "Context must be provided.")
            Objects.requireNonNull(sudoUserClient, "SudoUserClient must be provided.")

            val graphQLClient = graphQLClient
                ?: ApiClientManager.getClient(
                    this@Builder.context!!,
                    this@Builder.sudoUserClient!!,
                    "notificationService",
                )

            return DefaultSudoNotificationClient(
                context = context!!,
                sudoUserClient = this@Builder.sudoUserClient!!,
                notifiableServices = notifiableServices!!,
                graphQLClient = graphQLClient,
                logger = logger,
            )
        }
    }

    /**
     * Checksums for each file are generated and are used to create a checksum that is used when publishing to maven central.
     * In order to retry a failed publish without needing to change any functionality, we need a way to generate a different checksum
     * for the source code.  We can change the value of this property which will generate a different checksum for publishing
     * and allow us to retry.  The value of `version` doesn't need to be kept up-to-date with the version of the code.
     */
    val version: String

    val notifiableServices: List<NotifiableClient>

    /**
     * Defines the exceptions for the notification methods.
     *
     * @property message Accompanying message for the exception.
     * @property cause The cause for the exception.
     */
    sealed class NotificationException(message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause) {
        class InvalidConfigException(message: String? = null, cause: Throwable? = null) :
            NotificationException(message = message, cause = cause)
        class NotSignedInException(message: String? = null, cause: Throwable? = null) :
            NotificationException(message = message, cause = cause)
        class NoNotificationConfigException(message: String? = null, cause: Throwable? = null) :
            NotificationException(message = message, cause = cause)
        class AccountLockedException(message: String? = null, cause: Throwable? = null) :
            NotificationException(message = message, cause = cause)
        class AlreadyRegisteredNotificationException(message: String? = null, cause: Throwable? = null) :
            NotificationException(message = message, cause = cause)
        class NoDeviceNotificationException(message: String? = null, cause: Throwable? = null) :
            NotificationException(message = message, cause = cause)
        class NotAuthorizedException(message: String? = null, cause: Throwable? = null) :
            NotificationException(message = message, cause = cause)
        class ServiceException(message: String? = null, cause: Throwable? = null) :
            NotificationException(message = message, cause = cause)
        class RequestFailedException(message: String? = null, cause: Throwable? = null) :
            NotificationException(message = message, cause = cause)
        class NotificationPayloadException(message: String? = null, cause: Throwable? = null) :
            NotificationException(message = message, cause = cause)
        class InvalidArgumentException(message: String? = null, cause: Throwable? = null) :
            NotificationException(message = message, cause = cause)
        class UnknownException(cause: Throwable) :
            NotificationException(cause = cause)
    }

    /*
     * Clear all local cache data
     */
    @Throws
    suspend fun reset()

    /*
     * Process the remote notification and dispatch to the corresponding client
     */
    @Throws
    fun process(message: RemoteMessage): Unit

    /*
     * Get current notification configuration on this device
     */
    @Throws(SudoNotificationClient.NotificationException::class)
    suspend fun getNotificationConfiguration(device: NotificationDeviceInputProvider): NotificationConfiguration

    /*
     * DeRgister for push notification for the user on this device
     */
    @Throws(SudoNotificationClient.NotificationException::class)
    suspend fun deRegisterNotification(device: NotificationDeviceInputProvider): Unit

    /*
     * Register for push notification for the user on this device
     */
    @Throws(SudoNotificationClient.NotificationException::class)
    suspend fun registerNotification(device: NotificationDeviceInputProvider): Unit

    /*
     * Set the notification configuration for the user.
     */
    @Throws(SudoNotificationClient.NotificationException::class)
    suspend fun setNotificationConfiguration(config: NotificationSettingsInput): NotificationConfiguration

    /*
     * Update push notification registration for the user on this device
     */
    @Throws(SudoNotificationClient.NotificationException::class)
    suspend fun updateNotificationRegistration(device: NotificationDeviceInputProvider): Unit
}
