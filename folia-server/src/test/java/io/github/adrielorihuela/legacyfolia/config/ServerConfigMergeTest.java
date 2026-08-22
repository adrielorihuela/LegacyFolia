package io.github.adrielorihuela.legacyfolia.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.CommentedConfig;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The merge is the one part of loading that can destroy somebody's work, so it is pinned down
 * here: it may only ever add.
 */
class ServerConfigMergeTest {

    private static CommentedConfig template() {
        final CommentedConfig template = CommentedConfig.inMemory();
        template.set(List.of("dimensions", "nether"), true);
        template.setComment(List.of("dimensions", "nether"), " The Nether.");
        template.set(List.of("dimensions", "end"), true);
        template.setComment(List.of("dimensions", "end"), " The End.");
        template.setComment(List.of("dimensions"), " Which worlds exist.");
        return template;
    }

    @Test
    @DisplayName("an empty file gains every setting the template has")
    void fillsAnEmptyFile() {
        final CommentedConfig target = CommentedConfig.inMemory();

        final List<String> added = ServerConfig.merge(target, template());

        // A wholly missing section is copied in one go, so it is reported as the one path added
        // rather than as each of the keys inside it.
        assertIterableEquals(List.of("dimensions"), added);
        assertEquals(Boolean.TRUE, target.get(List.of("dimensions", "nether")));
        assertEquals(Boolean.TRUE, target.get(List.of("dimensions", "end")));
        assertEquals(" The Nether.", target.getComment(List.of("dimensions", "nether")));
        assertEquals(" Which worlds exist.", target.getComment(List.of("dimensions")));
    }

    @Test
    @DisplayName("a value the administrator changed is never reset to the default")
    void keepsChangedValues() {
        final CommentedConfig target = CommentedConfig.inMemory();
        target.set(List.of("dimensions", "nether"), false);
        target.set(List.of("dimensions", "end"), false);

        final List<String> added = ServerConfig.merge(target, template());

        assertTrue(added.isEmpty(), "nothing was missing, so nothing should have been added");
        assertEquals(Boolean.FALSE, target.get(List.of("dimensions", "nether")));
        assertEquals(Boolean.FALSE, target.get(List.of("dimensions", "end")));
    }

    @Test
    @DisplayName("a setting added by a new release appears with its comment")
    void addsNewSettingsOnUpgrade() {
        final CommentedConfig target = CommentedConfig.inMemory();
        target.set(List.of("dimensions", "nether"), false);

        final List<String> added = ServerConfig.merge(target, template());

        assertIterableEquals(List.of("dimensions.end"), added);
        assertEquals(Boolean.FALSE, target.get(List.of("dimensions", "nether")),
            "the existing value must survive the upgrade");
        assertEquals(Boolean.TRUE, target.get(List.of("dimensions", "end")));
        assertEquals(" The End.", target.getComment(List.of("dimensions", "end")));
    }

    @Test
    @DisplayName("a key the template does not know about is left in place")
    void keepsUnknownKeys() {
        final CommentedConfig target = CommentedConfig.inMemory();
        target.set(List.of("dimensions", "from-a-newer-build"), "keep me");

        ServerConfig.merge(target, template());

        assertEquals("keep me", target.get(List.of("dimensions", "from-a-newer-build")));
    }

    @Test
    @DisplayName("merging twice changes nothing the second time")
    void isIdempotent() {
        final CommentedConfig target = CommentedConfig.inMemory();

        ServerConfig.merge(target, template());
        final List<String> secondPass = ServerConfig.merge(target, template());

        assertTrue(secondPass.isEmpty(), "a second merge should find nothing left to add");
    }

    @Test
    @DisplayName("a section replaced by a scalar is rebuilt rather than merged into")
    void replacesAScalarStandingWhereASectionBelongs() {
        final CommentedConfig target = CommentedConfig.inMemory();
        target.set(List.of("dimensions"), "not a section");

        final List<String> added = ServerConfig.merge(target, template());

        assertIterableEquals(List.of("dimensions"), added);
        assertTrue(target.get(List.of("dimensions")) instanceof CommentedConfig);
        assertFalse(added.contains("dimensions.nether"),
            "the whole section is copied in one go, not key by key");
    }
}
