package tilelogger;

import com.ospx.flubundle.Bundle;
import io.avaje.inject.BeanScope;
import io.avaje.inject.spi.AvajeModule;
import io.avaje.inject.spi.Builder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.xcore.plugin.XcorePlugin;
import org.xcore.plugin.cloud.CloudService;
import org.xcore.plugin.integration.blockhistory.BlockHistoryRegistry;
import org.xcore.plugin.integration.blockhistory.BlockHistorySummary;
import org.xcore.plugin.startup.PluginScopes;
import org.xcore.plugin.startup.ShutdownOrder;
import tilelogger.command.HistoryController;
import tilelogger.command.RollbackController;
import tilelogger.command.TileLoggerController;
import tilelogger.command.TileLoggerServerController;
import tilelogger.integration.XcoreBlockHistoryProvider;
import tilelogger.integration.XcoreBlockHistoryProvider$DI;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TileLoggerPluginBlockHistoryTest {
    private BeanScope previousParent;
    private BeanScope parent;
    private BeanScope child;
    private PluginScopes scopes;
    private BlockHistoryRegistry registry;
    private BlockHistoryRegistry.Registration evidenceRegistration;
    private Bundle bundle;
    private CloudService cloud;
    private TileLoggerPlugin plugin;

    @BeforeEach
    void setUp() {
        previousParent = XcorePlugin.container;
        scopes = new PluginScopes();
        registry = mock(BlockHistoryRegistry.class);
        evidenceRegistration = mock(BlockHistoryRegistry.Registration.class);
        when(registry.register(any())).thenReturn(evidenceRegistration);
        bundle = mock(Bundle.class);
        cloud = mock(CloudService.class);
        parent = BeanScope.builder()
                .modules(new EmptyModule())
                .bean(BlockHistoryRegistry.class, registry)
                .bean(PluginScopes.class, scopes)
                .addPreDestroy(scopes::shutdown, ShutdownOrder.CHILD_SCOPES)
                .forTesting().build();
        XcorePlugin.container = parent;
        plugin = new TileLoggerPlugin() {
            @Override
            BeanScope createScope(BeanScope actualParent) {
                assertThat(actualParent).isSameAs(parent);
                child = BeanScope.builder()
                        .parent(actualParent)
                        .modules(new ProviderModule())
                        .bean(Bundle.class, bundle)
                        .bean(CloudService.class, cloud)
                        .bean(TileLoggerController.class, mock(TileLoggerController.class))
                        .bean(HistoryController.class, mock(HistoryController.class))
                        .bean(RollbackController.class, mock(RollbackController.class))
                        .bean(TileLoggerServerController.class, mock(TileLoggerServerController.class))
                        .forTesting().build();
                return child;
            }
        };
    }

    @AfterEach
    void tearDown() {
        try {
            plugin.disable();
            if (child != null) child.close();
            parent.close();
        } finally {
            XcorePlugin.container = previousParent;
        }
    }

    @Test
    void initializationUsesTheParentRegistryAndDisableClosesItsRegistrationExactlyOnce() {
        plugin.init();
        plugin.init();
        var provider = child.get(XcoreBlockHistoryProvider.class);
        verify(registry).register(provider);
        verify(cloud, times(3)).registerClient(any());
        verify(cloud, times(3)).registerServer(any());
        verify(evidenceRegistration, never()).close();

        plugin.disable();
        plugin.disable();
        verify(evidenceRegistration).close();
        assertThat(provider.summarize("player", 600)).isEmpty();
        assertThatThrownBy(provider::start).isInstanceOf(IllegalStateException.class);
    }

    static Stream<Throwable> startupFailures() {
        return Stream.of(new IllegalStateException("bundle failed"), new LinkageError("bundle unavailable"));
    }

    @ParameterizedTest
    @MethodSource("startupFailures")
    void initializationFailureClosesTheAlreadyRegisteredProvider(Throwable failure) {
        doThrow(failure).when(bundle).addSource(any(Class.class));
        assertThatThrownBy(plugin::init).isSameAs(failure);
        verify(registry).register(child.get(XcoreBlockHistoryProvider.class));
        verify(evidenceRegistration).close();
        verifyNoInteractions(cloud);
        plugin.disable();
        verify(evidenceRegistration, times(1)).close();
    }

    @Test
    void lateInitializationFailureAlsoClosesRegistration() {
        var failure = new IllegalStateException("commands failed");
        doThrow(failure).when(cloud).registerServer(any());
        assertThatThrownBy(plugin::init).isSameAs(failure);
        verify(evidenceRegistration).close();
    }

    @Test
    void parentShutdownClosesProviderWithoutExplicitPluginDisable() {
        plugin.init();
        parent.close();
        verify(evidenceRegistration).close();
        plugin.disable();
        verify(evidenceRegistration, times(1)).close();
    }

    @Test
    void rejectedChildScopeRegistrationDoesNotRegisterEvidence() {
        scopes.shutdown();
        assertThatThrownBy(plugin::init).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(registry, evidenceRegistration, cloud);
        assertThatThrownBy(child.get(XcoreBlockHistoryProvider.class)::start)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failedInitializationCanRetryWithANewChildScope() {
        var failure = new IllegalStateException("bundle failed once");
        doThrow(failure).doNothing().when(bundle).addSource(any(Class.class));
        assertThatThrownBy(plugin::init).isSameAs(failure);
        var firstProvider = child.get(XcoreBlockHistoryProvider.class);
        verify(evidenceRegistration).close();
        plugin.init();
        assertThat(child.get(XcoreBlockHistoryProvider.class)).isNotSameAs(firstProvider);
        assertThatThrownBy(firstProvider::start).isInstanceOf(IllegalStateException.class);
        verify(registry, times(2)).register(any());
        plugin.disable();
        verify(evidenceRegistration, times(2)).close();
    }

    @Test
    void disableClosesTheRealRegistrationWithoutRemovingItsSuccessor() {
        var realRegistry = useRealRegistry();
        plugin.init();
        var successor = emptySummary();
        var newer = realRegistry.register((uuid, window) -> Optional.of(successor));
        plugin.disable();
        verify(evidenceRegistration).close();
        assertThat(realRegistry.summarize("player", 600)).contains(successor);
        newer.close();
        assertThat(realRegistry.summarize("player", 600)).isEmpty();
    }

    @Test
    void startupFailureClosesTheRealRegistrationWithoutRemovingItsSuccessor() {
        var realRegistry = useRealRegistry();
        var successor = emptySummary();
        var failure = new IllegalStateException("bundle failed after another provider registered");
        doAnswer(invocation -> {
            realRegistry.register((uuid, window) -> Optional.of(successor));
            throw failure;
        }).when(bundle).addSource(any(Class.class));

        assertThatThrownBy(plugin::init).isSameAs(failure);
        verify(evidenceRegistration).close();
        assertThat(realRegistry.summarize("player", 600)).contains(successor);
        assertThatThrownBy(child.get(XcoreBlockHistoryProvider.class)::start)
                .isInstanceOf(IllegalStateException.class);
        realRegistry.stop();
    }

    @Test
    void providerRegistrationFailureClosesTheChildAndAllowsRetry() {
        var failure = new IllegalStateException("registry stopped");
        when(registry.register(any())).thenThrow(failure).thenReturn(evidenceRegistration);
        assertThatThrownBy(plugin::init).isSameAs(failure);
        var failedProvider = child.get(XcoreBlockHistoryProvider.class);
        assertThatThrownBy(failedProvider::start).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(bundle, cloud, evidenceRegistration);
        plugin.init();
        verify(registry, times(2)).register(any());
        plugin.disable();
        verify(evidenceRegistration).close();
    }

    private BlockHistoryRegistry useRealRegistry() {
        var realRegistry = new BlockHistoryRegistry();
        when(registry.register(any())).thenAnswer(invocation -> {
            var realRegistration = realRegistry.register(invocation.getArgument(0));
            doAnswer(closing -> {
                realRegistration.close();
                return null;
            }).when(evidenceRegistration).close();
            return evidenceRegistration;
        });
        return realRegistry;
    }

    private static BlockHistorySummary emptySummary() {
        return new BlockHistorySummary(600, 0, 0, 0, false, List.of(), null, null);
    }

    private static class EmptyModule implements AvajeModule {
        @Override
        public Class<?>[] classes() {
            return new Class<?>[0];
        }

        @Override
        public void build(Builder builder) {}
    }

    private static final class ProviderModule extends EmptyModule {
        @Override
        public Class<?>[] classes() {
            return new Class<?>[]{XcoreBlockHistoryProvider.class};
        }

        @Override
        public void build(Builder builder) {
            XcoreBlockHistoryProvider$DI.build(builder);
        }
    }
}
