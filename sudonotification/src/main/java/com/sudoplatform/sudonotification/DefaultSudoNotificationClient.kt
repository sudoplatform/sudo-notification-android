/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudonotification

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.amazonaws.mobileconnectors.appsync.fetcher.AppSyncResponseFetchers
import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.apollographql.apollo.api.Error
import com.apollographql.apollo.exception.ApolloException
import com.google.firebase.messaging.RemoteMessage
import com.sudoplatform.sudoapiclient.ApiClientManager
import com.sudoplatform.sudoconfigmanager.DefaultSudoConfigManager
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudonotification.appsync.enqueueFirst
import com.sudoplatform.sudonotification.graphql.DeleteAppFromDeviceMutation
import com.sudoplatform.sudonotification.graphql.GetNotificationSettingsQuery
import com.sudoplatform.sudonotification.graphql.RegisterAppOnDeviceMutation
import com.sudoplatform.sudonotification.graphql.UpdateDeviceInfoMutation
import com.sudoplatform.sudonotification.graphql.UpdateNotificationSettingsMutation
import com.sudoplatform.sudonotification.graphql.type.DeleteAppFromDeviceInput
import com.sudoplatform.sudonotification.graphql.type.Filter
import com.sudoplatform.sudonotification.graphql.type.FilterAction
import com.sudoplatform.sudonotification.graphql.type.GetSettingsInput
import com.sudoplatform.sudonotification.graphql.type.NotifiableServiceSchema
import com.sudoplatform.sudonotification.graphql.type.RegisterAppOnDeviceInput
import com.sudoplatform.sudonotification.graphql.type.SchemaEntry
import com.sudoplatform.sudonotification.graphql.type.UpdateInfoInput
import com.sudoplatform.sudonotification.graphql.type.UpdateSettingsInput
import com.sudoplatform.sudonotification.graphql.type.buildType
import com.sudoplatform.sudonotification.graphql.type.clientEnvType
import com.sudoplatform.sudonotification.logging.LogConstants
import com.sudoplatform.sudonotification.types.NotifiableClient
import com.sudoplatform.sudonotification.types.NotificationConfiguration
import com.sudoplatform.sudonotification.types.NotificationSettingsInput
import com.sudoplatform.sudonotification.types.transformers.NotificationTransformer
import com.sudoplatform.sudouser.SudoUserClient
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.CancellationException

/**
 * Default implementation of the [SudoNotificationClient] interface.
 *
 * @property context [Context] Application context.
 * @property sudoUserClient [SudoUserClient] Instance required to issue authentication tokens
 * @property appSyncClient [AWSAppSyncClient] Optional AppSync client to use. Mainly used for unit testing.
 * @property logger [Logger] Errors and warnings will be logged here.
 */
class DefaultSudoNotificationClient(
    private val context: Context,
    private val sudoUserClient: SudoUserClient,
    override val notifiableServices: List<NotifiableClient>,
    appSyncClient: AWSAppSyncClient? = null,
    private val logger: Logger = Logger(LogConstants.SUDOLOG_TAG, AndroidUtilsLogDriver(LogLevel.INFO)),
) : SudoNotificationClient {

    init {
        val configManager = DefaultSudoConfigManager(context)
        if (configManager.getConfigSet("apiService") == null) {
            throw SudoNotificationClient.NotificationException.InvalidConfigException("apiService not found")
        }
        val notificationConfig = configManager.getConfigSet("notificationService")
            ?: throw SudoNotificationClient.NotificationException.NoNotificationConfigException()

        // The old way of identifying notifiable services was via a notifiableServices
        // item in notificationService sudoplatformconfig.json stanza.
        //
        // The new way looks for a "notifiable: true" element in the service's own
        // sudoplatformconfig.json stanza
        //
        // For backwards compatibility we look for notifiableServices defined the old
        // way as well as the new way.

        val nApps = notificationConfig.optJSONArray("notifiableServices")
        val notifyingApps = if (nApps == null) {
            mutableListOf<String>()
        } else {
            MutableList(nApps.length()) { nApps.getString(it) }
        }

        for (ns in notifiableServices) {
            // Old way
            if (notifyingApps.contains(ns.serviceName)) {
                continue
            }

            // New way
            val config = configManager.getConfigSet(ns.serviceName)
            if (config?.getBoolean("notifiable") == true) {
                continue
            }

            throw SudoNotificationClient.NotificationException.InvalidConfigException("${ns.serviceName} is not notifiable")
        }
    }

    companion object {
        private const val SUDO_PLATFORM_KEY = "sudoplatform"

        /** Exception messages */
        private const val SERVICE_ERROR_MSG = "Service error"
        private const val ALREADY_REG_ERROR_MSG = "Device already registered"
        private const val NOT_FOUND_ERROR_MSG = "Device not found"
        private const val DELETE_ERROR_MSG = "Device deletion error"
        private const val UPDATE_ERROR_MSG = "Device update error"
        private const val READ_ERROR_MSG = "Device read error"
        private const val CREATE_ERROR_MSG = "Device create error"
        private const val PAYLOAD_ERROR_MSG = "notification payload error"

        /** Errors returned from the service */
        private const val ERROR_TYPE = "errorType"

        private const val ERROR_SERVICE = "sudoplatform.ServiceError"
        private const val ERROR_ALREADY_REG = "sudoplatform.ns.DeviceExist"
        private const val ERROR_NOT_FOUND = "sudoplatform.ns.DeviceNotFound"
        private const val ERROR_DELETE = "sudoplatform.ns.DeviceDelete"
        private const val ERROR_UPDATE = "sudoplatform.ns.DeviceUpdate"
        private const val ERROR_READ = "sudoplatform.ns.DeviceRead"
        private const val ERROR_CREATE = "sudoplatform.ns.DeviceCreate"
        private const val ERROR_INVALID_ARGUMENT = "sudoplatform.InvalidArgumentError"
    }

    override val version: String = "3.0.0"

    private val appSyncClient: AWSAppSyncClient =
        appSyncClient ?: ApiClientManager.getClient(
            context,
            this.sudoUserClient,
            "notificationService",
        )

    override suspend fun reset() {
        appSyncClient.clearCaches()
    }

    override fun process(message: RemoteMessage) {
        if (message.data.isEmpty()) {
            throw SudoNotificationClient.NotificationException.NotificationPayloadException(PAYLOAD_ERROR_MSG)
        }
        val sudoplatform = message.data[SUDO_PLATFORM_KEY]
            ?: throw SudoNotificationClient.NotificationException.NotificationPayloadException(PAYLOAD_ERROR_MSG)

        val serviceName = JSONObject(sudoplatform).getString("servicename")
        val service = notifiableServices.find { it.serviceName == serviceName }
        if (service == null) {
            logger.info("notifiable service [$serviceName] not found")
        } else {
            service.processPayload(message)
        }
    }

    override suspend fun getNotificationConfiguration(device: NotificationDeviceInputProvider): NotificationConfiguration {
        if (!this.sudoUserClient.isSignedIn()) {
            throw SudoNotificationClient.NotificationException.NotSignedInException()
        }

        try {
            val inputDevice = GetSettingsInput.builder()
                .bundleId(device.bundleIdentifier)
                .deviceId(device.deviceIdentifier)
                .build()
            val query = GetNotificationSettingsQuery.builder().input(inputDevice).build()
            val queryResponse = appSyncClient.query(query)
                .responseFetcher(AppSyncResponseFetchers.NETWORK_ONLY)
                .enqueueFirst()

            if (queryResponse.hasErrors()) {
                logger.warning("errors = ${queryResponse.errors()}")
                throw interpretNotificationError(queryResponse.errors().first())
            }
            val result = queryResponse.data()?.notificationSettings
            return result?.let { NotificationTransformer.toEntityFromGetNotificationSettingsQueryResult(it) }!!
        } catch (e: Throwable) {
            logger.debug("unexpected getNotificationConfiguration error $e")
            throw recognizeError(e)
        }
    }

    override suspend fun deRegisterNotification(device: NotificationDeviceInputProvider) {
        if (!this.sudoUserClient.isSignedIn()) {
            throw SudoNotificationClient.NotificationException.NotSignedInException()
        }

        try {
            val delInput = DeleteAppFromDeviceInput.builder()
                .bundleId(device.bundleIdentifier)
                .deviceId(device.deviceIdentifier)
                .build()
            val mutation = DeleteAppFromDeviceMutation.builder().input(delInput).build()
            val mutationResponse = appSyncClient.mutate(mutation)
                .enqueueFirst()

            if (mutationResponse.hasErrors()) {
                logger.warning("errors = ${mutationResponse.errors()}")
                throw interpretNotificationError(mutationResponse.errors().first())
            }
        } catch (e: Throwable) {
            logger.debug("unexpected deRegisterNotification error $e")
            throw recognizeError(e)
        }
    }

    override suspend fun registerNotification(device: NotificationDeviceInputProvider) {
        if (!this.sudoUserClient.isSignedIn()) {
            throw SudoNotificationClient.NotificationException.NotSignedInException()
        }

        try {
            val inputDevice = RegisterAppOnDeviceInput.builder()
                .deviceId(device.deviceIdentifier)
                .clientEnv(clientEnvType.valueOf(device.clientEnv.uppercase()))
                .bundleId(device.bundleIdentifier)
                .build(buildType.valueOf(device.buildType.uppercase()))
                .locale(device.locale)
                .version(device.appVersion)
                .standardToken(device.pushToken)
                .build()
            val mutation = RegisterAppOnDeviceMutation.builder().input(inputDevice).build()
            val mutationResponse = appSyncClient.mutate(mutation)
                .enqueueFirst()

            if (mutationResponse.hasErrors()) {
                logger.warning("errors = ${mutationResponse.errors()}")
                throw interpretNotificationError(mutationResponse.errors().first())
            }
        } catch (e: Throwable) {
            logger.debug("unexpected registerNotification error $e")
            throw recognizeError(e)
        }
    }

    override suspend fun setNotificationConfiguration(
        config: NotificationSettingsInput,
    ): NotificationConfiguration {
        if (!this.sudoUserClient.isSignedIn()) {
            throw SudoNotificationClient.NotificationException.NotSignedInException()
        }

        try {
            val filters = config.filter.map {
                Filter.builder()
                    .actionType(FilterAction.valueOf(it.status!!))
                    .enableMeta(it.meta!!)
                    .serviceName(it.name)
                    .rule(it.rules!!)
                    .build()
            }
            val services = config.services.map { service ->
                NotifiableServiceSchema.builder()
                    .serviceName(service.serviceName)
                    .schema(
                        service.schema.map { schema ->
                            SchemaEntry.builder().description(schema.description)
                                .type(schema.type).fieldName(schema.fieldName).build()
                        },
                    )
                    .build()
            }
            val settingsInput = UpdateSettingsInput
                .builder()
                .bundleId(config.bundleId)
                .deviceId(config.deviceId)
                .services(services)
                .filter(filters)
                .build()
            val mutation = UpdateNotificationSettingsMutation.builder().input(settingsInput).build()
            val mutationResponse = appSyncClient.mutate(mutation)
                .enqueueFirst()

            if (mutationResponse.hasErrors()) {
                logger.warning("errors = ${mutationResponse.errors()}")
                throw interpretNotificationError(mutationResponse.errors().first())
            }
            return NotificationConfiguration(configs = config.filter)
        } catch (e: Throwable) {
            logger.debug("unexpected setNotificationConfiguration error $e")
            throw recognizeError(e)
        }
    }

    override suspend fun updateNotificationRegistration(device: NotificationDeviceInputProvider) {
        if (!this.sudoUserClient.isSignedIn()) {
            throw SudoNotificationClient.NotificationException.NotSignedInException()
        }

        try {
            val inputDevice = UpdateInfoInput.builder()
                .bundleId(device.bundleIdentifier)
                .deviceId(device.deviceIdentifier)
                .build(buildType.valueOf(device.buildType.uppercase()))
                .locale(device.locale)
                .version(device.appVersion)
                .standardToken(device.pushToken)
                .build()
            val mutation = UpdateDeviceInfoMutation.builder().input(inputDevice).build()
            val mutationResponse = appSyncClient.mutate(mutation)
                .enqueueFirst()

            if (mutationResponse.hasErrors()) {
                logger.warning("errors = ${mutationResponse.errors()}")
                throw interpretNotificationError(mutationResponse.errors().first())
            }
        } catch (e: Throwable) {
            logger.debug("unexpected registerNotification error $e")
            throw recognizeError(e)
        }
    }

    private fun interpretNotificationError(e: Error): SudoNotificationClient.NotificationException {
        val error = e.customAttributes()[ERROR_TYPE]?.toString() ?: ""
        if (error.contains(ERROR_SERVICE)) {
            return SudoNotificationClient.NotificationException.ServiceException(SERVICE_ERROR_MSG)
        }
        if (error.contains(ERROR_ALREADY_REG)) {
            return SudoNotificationClient.NotificationException.AlreadyRegisteredNotificationException(ALREADY_REG_ERROR_MSG)
        }
        if (error.contains(ERROR_NOT_FOUND)) {
            return SudoNotificationClient.NotificationException.NoDeviceNotificationException(
                NOT_FOUND_ERROR_MSG,
            )
        }
        if (error.contains(ERROR_UPDATE)) {
            return SudoNotificationClient.NotificationException.RequestFailedException(
                UPDATE_ERROR_MSG,
            )
        }
        if (error.contains(ERROR_DELETE)) {
            return SudoNotificationClient.NotificationException.RequestFailedException(
                DELETE_ERROR_MSG,
            )
        }
        if (error.contains(ERROR_READ)) {
            return SudoNotificationClient.NotificationException.RequestFailedException(
                READ_ERROR_MSG,
            )
        }
        if (error.contains(ERROR_CREATE)) {
            return SudoNotificationClient.NotificationException.RequestFailedException(
                CREATE_ERROR_MSG,
            )
        }
        return SudoNotificationClient.NotificationException.RequestFailedException(e.toString())
    }
}

@VisibleForTesting
fun recognizeError(e: Throwable): Throwable {
    return recognizeRootCause(e) ?: when (e) {
        is ApolloException -> SudoNotificationClient.NotificationException.RequestFailedException(cause = e)
        else -> SudoNotificationClient.NotificationException.UnknownException(e)
    }
}

private fun recognizeRootCause(e: Throwable?): Throwable? {
    // If we find a Sudo Platform exception, return that
    if (e?.javaClass?.`package`?.name?.startsWith("com.sudoplatform.") ?: false) {
        return e
    }

    return when (e) {
        is SudoNotificationClient.NotificationException -> e
        is NotAuthorizedException -> SudoNotificationClient.NotificationException.NotAuthorizedException(cause = e)
        is ApolloException -> recognizeRootCause(e.cause)
        is CancellationException -> e
        is IOException -> recognizeRootCause(e.cause)
        is RuntimeException -> recognizeRootCause(e.cause)
        else -> null
    }
}
