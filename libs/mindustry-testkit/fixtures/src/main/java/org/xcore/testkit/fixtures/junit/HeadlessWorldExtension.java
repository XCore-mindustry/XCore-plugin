package org.xcore.testkit.fixtures.junit;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.platform.commons.support.AnnotationSupport;
import org.xcore.testkit.fixtures.HeadlessWorld;

import java.util.Optional;

/**
 * JUnit 5 Extension providing automated {@link HeadlessWorld} lifecycle management
 * and parameter resolution for test methods.
 */
public class HeadlessWorldExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(HeadlessWorldExtension.class);
    private static final String WORLD_KEY = "headlessWorld";

    @Override
    public void beforeEach(ExtensionContext context) {
        HeadlessWorld.Builder builder = HeadlessWorld.builder();
        config(context).ifPresent(annotation -> builder
                .dimensions(annotation.width(), annotation.height())
                .generating(annotation.generating()));
        context.getStore(NAMESPACE).put(WORLD_KEY, builder.build());
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

    /** The method's annotation, else the test class's (superclasses included), else an enclosing class's. */
    private static Optional<WithHeadlessWorld> config(ExtensionContext context) {
        Optional<WithHeadlessWorld> found = AnnotationSupport.findAnnotation(context.getTestMethod(), WithHeadlessWorld.class);
        for (Class<?> type = context.getRequiredTestClass(); found.isEmpty() && type != null; type = type.getEnclosingClass()) {
            found = AnnotationSupport.findAnnotation(type, WithHeadlessWorld.class);
        }
        return found;
    }
}
