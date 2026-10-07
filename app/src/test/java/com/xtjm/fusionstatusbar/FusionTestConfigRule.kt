package com.xtjm.fusionstatusbar

import org.junit.rules.ExternalResource
import org.robolectric.RuntimeEnvironment

/** Robolectric changes Application between tests; process singletons must be recreated first. */
class FusionTestConfigRule : ExternalResource() {
    override fun before() {
        FusionConfigRepository::class.java.getDeclaredField("application").apply { isAccessible = true }.set(null, null)
        FusionConfigStore.write(RuntimeEnvironment.getApplication(), FusionConfig.defaults())
    }
}
