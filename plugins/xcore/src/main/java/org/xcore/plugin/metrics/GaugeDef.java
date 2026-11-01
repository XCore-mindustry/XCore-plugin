package org.xcore.plugin.metrics;

public record GaugeDef(
        String name,
        String help,
        String unit,
        LabelSchema labels
) implements MetricDef {
    public GaugeDef {
        MetricValidation.validateCommon(name, help, unit, labels);
    }
}
