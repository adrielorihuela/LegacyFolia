package io.github.adrielorihuela.legacyfolia.config.bridge;

import java.lang.reflect.Field;
import net.minecraft.server.MinecraftServer;
import org.bukkit.support.RegistryHelper;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.Mockito;

/**
 * Lets Paper's global configuration be loaded for real in a test, post-processing included.
 *
 * <p>Mapping {@code GlobalConfiguration} binds the item obfuscation, and the class that does it
 * asks the running server for its registries while it initialises. A test has no running server,
 * so the class fails to initialise and stays failed for the rest of the JVM. The test environment
 * gets away with that because it only builds an empty configuration, where Configurate swallows
 * the failure; the bridge's tests load real nodes, where it does not.</p>
 *
 * <p>So, before the {@code AllFeatures} environment starts, a stand-in server is put in place
 * that answers that one question with the test registries, which the environment sets up before
 * it builds its configuration. Before the first test runs, the class is initialised if nothing
 * has done it yet, and the stand-in is removed: the chat-thread post-processor, for one, looks
 * for a server and must find none in the tests themselves (while the environment builds its own
 * configuration it finds the stand-in, fails, and Configurate leaves that one part empty, which
 * nothing in these tests reads). Declare this before {@code @AllFeatures} on every class that
 * uses both, so that whichever of them runs first gets here first.</p>
 */
public final class ItemSanitizerFirst implements BeforeAllCallback, BeforeEachCallback {

    private static final String SANITIZER = "io.papermc.paper.util.sanitizer.ItemComponentSanitizer";

    private static boolean installed;

    private static MinecraftServer standIn;

    @Override
    public void beforeAll(final ExtensionContext context) throws Exception {
        synchronized (ItemSanitizerFirst.class) {
            if (installed) {
                return;
            }
            installed = true;
            standIn = Mockito.mock(MinecraftServer.class, Mockito.withSettings().stubOnly().defaultAnswer(invocation -> {
                if ("registryAccess".equals(invocation.getMethod().getName())) {
                    return RegistryHelper.registryAccess();
                }
                return Mockito.RETURNS_DEFAULTS.answer(invocation);
            }));
            server(standIn);
        }
    }

    @Override
    public void beforeEach(final ExtensionContext context) throws Exception {
        synchronized (ItemSanitizerFirst.class) {
            if (standIn != null && MinecraftServer.getServer() == standIn) {
                Class.forName(SANITIZER, true, ItemSanitizerFirst.class.getClassLoader());
                server(null);
            }
        }
    }

    private static void server(final MinecraftServer server) throws ReflectiveOperationException {
        final Field field = MinecraftServer.class.getDeclaredField("SERVER");
        field.setAccessible(true);
        field.set(null, server);
    }
}
