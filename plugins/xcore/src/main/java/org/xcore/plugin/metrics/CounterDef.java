package org.xcore.plugin.metrics;

public record CounterDef(
        String name,
        String help,
        String unit,
        LabelSchema labels
) implements MetricDef {
    public CounterDef {
        MetricValidation.validateCommon(name, help, unit, labels);
        if (!name.endsWith("_total")) {
            throw new IllegalArgumentException("Counter metrics must end with _total: " + name);
        }
    }
}
