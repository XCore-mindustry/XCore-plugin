package org.xcore.plugin.cloud.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.cloud.mindustry.MindustryCommandManager;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.cloud.exception.XCoreCommandException;
import org.xcore.plugin.localization.BundleService;
import org.xcore.plugin.session.SessionService;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CloudExceptionConfigurerTest {

    private BundleService bundleService;
    private SessionService sessionService;
    private CloudExceptionConfigurer configurer;

    @BeforeEach
    void setUp() {
        bundleService = mock(BundleService.class);
        sessionService = mock(SessionService.class);
        configurer = new CloudExceptionConfigurer(bundleService, () -> sessionService);
    }

    @Test
    @DisplayName("can be instantiated and configured without failing")
    void configure_doesNotFail() {
        @SuppressWarnings("unchecked")
        MindustryCommandManager<XCoreSender> manager = mock(MindustryCommandManager.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);

        configurer.configure(manager);

        verify(manager.exceptionController()).registerHandler(eq(XCoreCommandException.class), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("silent XCoreCommandException carries no visible rendering intent")
    void silentException_preservesSilentFlag() {
        XCoreCommandException exception = new XCoreCommandException(true);

        org.assertj.core.api.Assertions.assertThat(exception.isSilent()).isTrue();
        org.assertj.core.api.Assertions.assertThat(exception.getKey()).isNull();
        verify(bundleService, never()).format(org.mockito.ArgumentMatchers.any(), eq("error-access-denied"), anyMap());
    }

    @Test
    @DisplayName("keyed XCoreCommandException preserves key and args")
    void keyedException_preservesData() {
        XCoreCommandException exception = new XCoreCommandException("error-test", java.util.Map.of("foo", "bar"));

        org.assertj.core.api.Assertions.assertThat(exception.isSilent()).isFalse();
        org.assertj.core.api.Assertions.assertThat(exception.getKey()).isEqualTo("error-test");
        org.assertj.core.api.Assertions.assertThat(exception.getArgs()).containsEntry("foo", "bar");
    }
}
