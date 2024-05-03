/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudonotification

import com.sudoplatform.sudoapiclient.ApiClientManager
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudonotification.types.NotifiableClient
import com.sudoplatform.sudonotification.types.NotificationConfiguration
import com.sudoplatform.sudonotification.types.NotificationFilterItem
import com.sudoplatform.sudonotification.types.NotificationMetaData
import com.sudoplatform.sudonotification.types.NotificationSettingsInput
import io.kotlintest.matchers.numerics.shouldBeExactly
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotThrowAny
import io.kotlintest.shouldThrow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import timber.log.Timber

/**
 * Test the operation of the [SudoNotificationClient].
 */
class SudoNotificationClientIntegrationTest : BaseIntegrationTest() {
    private val verbose = false
    private val logLevel = if (verbose) LogLevel.VERBOSE else LogLevel.INFO
    private val logger = Logger("notification-test", AndroidUtilsLogDriver(logLevel))

    private lateinit var notificationClient: SudoNotificationClient
    private lateinit var deviceInfoProvider: DefaultNotificationDeviceInputProvider
    private lateinit var sampleNotifiableClient: NotifiableClient

    companion object {
        private const val TEST_SERVICE_NAME = "sampleService"
        private const val TEST_ALT_SERVICE_NAME = "test-alternative-service"
        private const val TEST_RULE = "{\"==\" : [{\"var\" : \"meta.f1\"}, \"sudo123@sudomail.com\"]}"
        private const val TEST_DEVICE_ID = "test-notification-deviceID"
        private const val INVALID_DEVICE_ID = "not-test-notification-deviceID"
        private const val TEST_TOKEN = "StandardToken-test-push-token"
    }

    @Before
    fun init() {
        Timber.plant(Timber.DebugTree())

        if (verbose) {
            java.util.logging.Logger.getLogger("com.amazonaws").level = java.util.logging.Level.FINEST
            java.util.logging.Logger.getLogger("org.apache.http").level = java.util.logging.Level.FINEST
        }

        sampleNotifiableClient = TestNotifiableClient()

        notificationClient = SudoNotificationClient.builder()
            .setContext(context)
            .setSudoUserClient(userClient)
            .setLogger(logger)
            .setNotifiableClients(listOf(sampleNotifiableClient))
            .build()

        deviceInfoProvider = DefaultNotificationDeviceInputProvider(context, TEST_DEVICE_ID, TEST_TOKEN)
    }

    @After
    fun fini() = runBlocking {
        userClient.reset()
        Timber.uprootAll()
    }

    @Test
    fun shouldThrowIfRequiredItemsNotProvidedToBuilder() {
        // Can only run if client config files are present
        Assume.assumeTrue(clientConfigFilesPresent())

        // All required items not provided
        shouldThrow<NullPointerException> {
            SudoNotificationClient.builder().build()
        }

        // Context not provided
        shouldThrow<NullPointerException> {
            SudoNotificationClient.builder()
                .setSudoUserClient(userClient)
                .build()
        }

        // SudoUserClient not provided
        shouldThrow<NullPointerException> {
            SudoNotificationClient.builder()
                .setContext(context)
                .build()
        }
    }

    @Test
    fun shouldNotThrowIfTheRequiredItemsAreProvidedToBuilder() {
        // Can only run if client config files are present
        Assume.assumeTrue(clientConfigFilesPresent())

        SudoNotificationClient.builder()
            .setContext(context)
            .setSudoUserClient(userClient)
            .setNotifiableClients(listOf(sampleNotifiableClient))
            .build()
    }

    @Test
    fun shouldNotThrowIfAllItemsAreProvidedToBuilder() {
        // Can only run if client config files are present
        Assume.assumeTrue(clientConfigFilesPresent())

        val appSyncClient = ApiClientManager.getClient(context, userClient)

        SudoNotificationClient.builder()
            .setContext(context)
            .setSudoUserClient(userClient)
            .setAppSyncClient(appSyncClient)
            .setLogger(logger)
            .setNotifiableClients(listOf(sampleNotifiableClient))
            .build()
    }

    @Test
    fun registerDeviceShouldNotThrow() = runBlocking {
        // Can only run if client config files are present
        Assume.assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        shouldNotThrowAny {
            notificationClient.registerNotification(deviceInfoProvider)
        }

        deRegister()
    }

    @Test
    fun registerDevicesAgainShouldThrow() = runBlocking {
        // Can only run if client config files are present
        Assume.assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        notificationClient.registerNotification(deviceInfoProvider)

        shouldThrow<SudoNotificationClient.NotificationException.AlreadyRegisteredNotificationException> {
            notificationClient.registerNotification(deviceInfoProvider)
        }

        deRegister()
    }

    @Test
    fun deRegisterDevicesShouldNotThrow() = runBlocking {
        // Can only run if client config files are present
        Assume.assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        notificationClient.registerNotification(deviceInfoProvider)

        shouldNotThrowAny {
            notificationClient.deRegisterNotification(deviceInfoProvider)
        }

        deRegister()
    }

    @Test
    fun deRegisterInvalidDevicesShouldThrow() = runBlocking {
        // Can only run if client config files are present
        Assume.assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        notificationClient.registerNotification(deviceInfoProvider)

        val deviceInfoUpdate = DefaultNotificationDeviceInputProvider(context, INVALID_DEVICE_ID, "")

        shouldThrow<SudoNotificationClient.NotificationException.NoDeviceNotificationException> {
            notificationClient.deRegisterNotification(deviceInfoUpdate)
        }

        deRegister()
    }

    @Test
    fun updateDevicesShouldNotThrow() = runBlocking {
        // Can only run if client config files are present
        Assume.assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        notificationClient.registerNotification(deviceInfoProvider)

        val deviceInfoUpdate = DefaultNotificationDeviceInputProvider(context, TEST_DEVICE_ID, "")

        shouldNotThrowAny {
            notificationClient.updateNotificationRegistration(deviceInfoUpdate)
        }

        deRegister()
    }

    @Test
    fun updateDeviceWithoutRegisterShouldThrow() = runBlocking {
        // Can only run if client config files are present
        Assume.assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        shouldThrow<SudoNotificationClient.NotificationException.NoDeviceNotificationException> {
            notificationClient.updateNotificationRegistration(deviceInfoProvider)
        }

        deRegister()
    }

    @Test
    fun updateInvalidDeviceShouldThrow() = runBlocking {
        // Can only run if client config files are present
        Assume.assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        notificationClient.registerNotification(deviceInfoProvider)

        deRegister()

        // Now we are with a different user despite same device ID.
        signInAndRegister()

        shouldThrow<SudoNotificationClient.NotificationException.NoDeviceNotificationException> {
            notificationClient.updateNotificationRegistration(deviceInfoProvider)
        }

        deRegister()
    }

    @Test
    fun getNotificationConfigListAfterRegisterShouldReturnEmpty() = runBlocking {
        // Can only run if client config files are present
        Assume.assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        notificationClient.registerNotification(deviceInfoProvider)

        val notifConf = notificationClient.getNotificationConfiguration(deviceInfoProvider)
        notifConf.configs.size shouldBeExactly 0

        notificationClient.deRegisterNotification(deviceInfoProvider)

        deRegister()
    }

    @Test
    fun getNotificationConfigListOnInvalidDeviceShouldThrow() = runBlocking {
        // Can only run if client config files are present
        Assume.assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        shouldThrow<SudoNotificationClient.NotificationException.NoDeviceNotificationException> {
            notificationClient.getNotificationConfiguration(deviceInfoProvider)
        }
        deRegister()
    }

    @Test
    fun setNotificationConfigShouldReturnSuccess() = runBlocking {
        // Can only run if client config files are present
        Assume.assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        notificationClient.registerNotification(deviceInfoProvider)

        val confList = listOf<NotificationFilterItem>(NotificationFilterItem(name = TEST_SERVICE_NAME))
        val schema = listOf<NotificationMetaData>(TestNotificationMetaData(TEST_SERVICE_NAME))
        val config = NotificationConfiguration(configs = confList)
        val input = NotificationSettingsInput(deviceInfoProvider.bundleIdentifier, deviceInfoProvider.deviceIdentifier, confList, schema)

        val ret = notificationClient.setNotificationConfiguration(input)
        ret shouldBe config

        notificationClient.deRegisterNotification(deviceInfoProvider)

        deRegister()
    }

    @Test
    fun setNotificationConfigOnInvalidDeviceShouldThrow() = runBlocking {
        // Can only run if client config files are present
        Assume.assumeTrue(clientConfigFilesPresent())

        signInAndRegister()
        logger.warning("user is ${userClient.getUserName()}")

        val confList = listOf<NotificationFilterItem>(NotificationFilterItem(name = TEST_SERVICE_NAME))
        val schema = listOf<NotificationMetaData>(TestNotificationMetaData(TEST_SERVICE_NAME))
        val input = NotificationSettingsInput(deviceInfoProvider.bundleIdentifier, deviceInfoProvider.deviceIdentifier, confList, schema)

        shouldThrow<SudoNotificationClient.NotificationException.NoDeviceNotificationException> {
            notificationClient.setNotificationConfiguration(input)
        }

        deRegister()
    }

    @Test
    fun setAndgetNotificationConfigShouldMatch() = runBlocking {
        // Can only run if client config files are present
        Assume.assumeTrue(clientConfigFilesPresent())

        signInAndRegister()

        logger.warning("user is ${userClient.getUserName()}")
        notificationClient.registerNotification(deviceInfoProvider)

        val confList = listOf<NotificationFilterItem>(
            NotificationFilterItem(name = TEST_SERVICE_NAME),
            NotificationFilterItem(name = TEST_SERVICE_NAME, rules = TEST_RULE),
            NotificationFilterItem(name = TEST_ALT_SERVICE_NAME),
        )
        val config = NotificationConfiguration(configs = confList)
        config.updateConfig(uuid = confList.first { it.name == TEST_ALT_SERVICE_NAME }.uuid!!, status = false)

        val schema = listOf<NotificationMetaData>(TestNotificationMetaData(TEST_SERVICE_NAME), TestNotificationMetaData(TEST_ALT_SERVICE_NAME))
        val input = NotificationSettingsInput(deviceInfoProvider.bundleIdentifier, deviceInfoProvider.deviceIdentifier, config.configs, schema)
        notificationClient.setNotificationConfiguration(input)
        val ret = notificationClient.getNotificationConfiguration(deviceInfoProvider)
        ret shouldBe config
        ret.configs shouldBe config.configs

        notificationClient.deRegisterNotification(deviceInfoProvider)

        deRegister()
    }
}
