package de.sldk.mc.metrics;

import io.prometheus.client.Gauge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MinecraftGauge {

    private static final String LABEL_SERVER_ID = "server_id";
    private static final String LABEL_SERVER_NAME = "server_name";

    private final String serverId;
    private final String serverName;
    private final Gauge prometheusGauge;

    MinecraftGauge(String serverId, String serverName, String name, String help, String... labels) {
        this.serverId = serverId;
        this.serverName = serverName;
        this.prometheusGauge = Gauge.build()
                .name(name).help(help)
                .labelNames(withPredefinedLabelNames(labels))
                .create()
                .register();
    }

    public Gauge.Child labels(String... labels) {
        return prometheusGauge.labels(withPredefinedLabelValues(labels));
    }

    public void set(double value) {
        prometheusGauge.labels(withPredefinedLabelValues()).set(value);
    }

    private String[] withPredefinedLabelNames(String... labelNames) {
        return concatWithPredefinedLabels(LABEL_SERVER_ID, LABEL_SERVER_NAME, labelNames);
    }

    private String[] withPredefinedLabelValues(String... labelValues) {
        return concatWithPredefinedLabels(serverId, serverName, labelValues);
    }

    private String[] concatWithPredefinedLabels(String serverId, String serverName, String... extraLabels) {
        List<String> labels = new ArrayList<>();
        labels.add(serverId);
        labels.add(serverName);
        labels.addAll(Arrays.asList(extraLabels));

        return labels.toArray(new String[0]);

    }

}
