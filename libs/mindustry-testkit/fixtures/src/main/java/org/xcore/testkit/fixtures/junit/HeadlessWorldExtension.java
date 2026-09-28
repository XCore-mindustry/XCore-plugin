package org.xcore.testkit.fixtures.junit;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.xcore.testkit.fixtures.HeadlessWorld;

/**
 * JUnit 5 Extension providing automated {@link HeadlessWorld} lifecycle management
 * and parameter resolution for test methods.
 */
public class HeadlessWorldExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(HeadlessWorldExtension.class);
    private static final String WORLD_KEY = "headlessWorld";

    @Override
    public void beforeEach(ExtensionContext context) {
        WithHeadlessWorld annotation = context.getRequiredTestMethod().getAnnotation(WithHeadlessWorld.class);
        if (annotation == null) {
            annotation = context.getRequiredTestClass().getAnnotation(WithHeadlessWorld.class);
        }

        int width = annotation != null ? annotation.width() : 32;
        int height = annotation != null ? annotation.height() : 32;

        HeadlessWorld world = HeadlessWorld.create(width, height);
        context.getStore(NAMESPACE).put(WORLD_KEY, world);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        HeadlessWorld world = context.getStore(NAMESPACE).remove(WORLD_KEY, HeadlessWorld.class);
        if (world != null) {
            world.close();
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType().equals(HeadlessWorld.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return extensionContext.getStore(NAMESPACE).get(WORLD_KEY, HeadlessWorld.class);
    }
}
