package org.xcore.plugin.metrics;

import jakarta.inject.Singleton;
import org.xcore.protocol.generated.shared.MetricSampleV1;
import org.xcore.protocol.generated.shared.MetricSampleV1Type;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

@Singleton
public final class LocalMetricRegistry {
    private final ConcurrentMap<String, CounterSeries> counters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, GaugeSeries> gauges = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, HistogramSeries> histograms = new ConcurrentHashMap<>();

    public Counter counter(CounterDef def, Tags tags) {
        String key = seriesKey(def, tags);
        return counters.computeIfAbsent(key, ignored -> new CounterSeries(def, def.labels().toLabelMap(tags)));
    }

    public Gauge gauge(GaugeDef def, Tags tags) {
        String key = seriesKey(def, tags);
        return gauges.computeIfAbsent(key, ignored -> new GaugeSeries(def, def.labels().toLabelMap(tags)));
    }

    public Histogram histogram(HistogramDef def, Tags tags) {
        String key = seriesKey(def, tags);
        return histograms.computeIfAbsent(key, ignored -> new HistogramSeries(def, def.labels().toLabelMap(tags)));
    }

    public List<MetricSampleV1> snapshot() {
        List<MetricSampleV1> samples = new ArrayList<>(counters.size() + gauges.size() + histograms.size());
        counters.values().forEach(series -> samples.add(series.toSample()));
        gauges.values().forEach(series -> samples.add(series.toSample()));
        histograms.values().forEach(series -> samples.add(series.toSample()));
        samples.sort(Comparator.comparing(MetricSampleV1::name).thenComparing(sample -> sample.labels().toString()));
        return List.copyOf(samples);
    }

    private String seriesKey(MetricDef def, Tags tags) {
        def.labels().validate(tags);
        StringBuilder builder = new StringBuilder(def.name());
        for (int i = 0; i < tags.size(); i++) {
            builder.append('\u0000').append(tags.nameAt(i)).append('=').append(tags.valueAt(i));
        }
        return builder.toString();
    }

    private record CounterSeries(CounterDef def, Map<String, Object> labels, LongAdder value) implements Counter {
        private CounterSeries(CounterDef def, Map<String, Object> labels) {
            this(def, labels, new LongAdder());
        }

        @Override
        public void increment() {
            value.increment();
        }

        @Override
        public void add(long amount) {
            if (amount > 0) {
                value.add(amount);
            }
        }

        MetricSampleV1 toSample() {
            return new MetricSampleV1(
                    def.name(),
                    MetricSampleV1Type.COUNTER,
                    labels,
                    def.help(),
                    def.unit(),
                    (double) value.sum(),
                    null,
                    null,
                    null,
                    null
            );
        }
    }

    private record GaugeSeries(GaugeDef def, Map<String, Object> labels, AtomicLong valueBits) implements Gauge {
        private GaugeSeries(GaugeDef def, Map<String, Object> labels) {
            this(def, labels, new AtomicLong(Double.doubleToRawLongBits(0.0d)));
        }

        @Override
        public void set(long value) {
            set((double) value);
        }

        @Override
        public void set(double value) {
            valueBits.set(Double.doubleToRawLongBits(value));
        }

        MetricSampleV1 toSample() {
            return new MetricSampleV1(
                    def.name(),
                    MetricSampleV1Type.GAUGE,
                    labels,
                    def.help(),
                    def.unit(),
                    Double.longBitsToDouble(valueBits.get()),
                    null,
                    null,
                    null,
                    null
            );
        }
    }

    private static final class HistogramSeries implements Histogram {
        private final HistogramDef def;
        private final Map<String, Object> labels;
        private final LongAdder[] bucketCounts;
        private final LongAdder count = new LongAdder();
        private final DoubleAdder sum = new DoubleAdder();

        private HistogramSeries(HistogramDef def, Map<String, Object> labels) {
            this.def = def;
            this.labels = labels;
            this.bucketCounts = new LongAdder[def.buckets().length];
            Arrays.setAll(this.bucketCounts, ignored -> new LongAdder());
        }

        @Override
        public void observe(double value) {
            if (!Double.isFinite(value)) {
                return;
            }
            count.increment();
            sum.add(value);
            for (int i = 0; i < def.buckets().length; i++) {
                if (value <= def.buckets()[i]) {
                    bucketCounts[i].increment();
                    return;
                }
            }
        }

        private MetricSampleV1 toSample() {
            List<Double> buckets = Arrays.stream(def.buckets()).boxed().toList();
            List<Integer> cumulativeCounts = new ArrayList<>(bucketCounts.length);
            long running = 0L;
            for (LongAdder bucketCount : bucketCounts) {
                running += bucketCount.sum();
                cumulativeCounts.add(Math.toIntExact(running));
            }

            return new MetricSampleV1(
                    def.name(),
                    MetricSampleV1Type.HISTOGRAM,
                    labels,
                    def.help(),
                    def.unit(),
                    null,
                    buckets,
                    cumulativeCounts,
                    count.sum(),
                    sum.sum()
            );
        }
    }
}
