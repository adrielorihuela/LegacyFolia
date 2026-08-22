package io.github.adrielorihuela.legacyfolia;

import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Runs this fork's own tests.
 *
 * <p>The test task only runs classes matching {@code **TestSuite}, and upstream's suites select
 * only the {@code org.bukkit}, {@code io.papermc.paper} and {@code com.destroystokyo.paper}
 * packages, so ours would never be picked up without a suite of its own.</p>
 */
@Suite(failIfNoTests = false)
@SuiteDisplayName("LegacyFolia")
@SelectPackages("io.github.adrielorihuela.legacyfolia")
public class LegacyFoliaTestSuite {
}
