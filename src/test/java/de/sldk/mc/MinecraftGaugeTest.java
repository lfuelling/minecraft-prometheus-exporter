package de.sldk.mc;

import de.sldk.mc.metrics.MinecraftGauge;
import de.sldk.mc.metrics.MinecraftMetrics;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

public class MinecraftGaugeTest {

    private static final String SAMPLE_SERVER_ID = "sample-server-id";
    private static final String SAMPLE_SERVER_NAME = "sample-server-name";

    private static final String SAMPLE_METRIC = "sample_metric";
    private static final String SAMPLE_HELP_TEXT = "Help text";
    private static final Double SAMPLE_VALUE = 4.2;

    @Before
    public void setup() {
        CollectorRegistry.defaultRegistry.clear();
    }

    @Test
    public void testGaugeWithoutExtraLabels() throws IOException {

        MinecraftMetrics minecraftMetrics = MinecraftMetrics.forServer(SAMPLE_SERVER_ID, SAMPLE_SERVER_NAME);
        MinecraftGauge gauge = minecraftMetrics.getGauge(SAMPLE_METRIC, SAMPLE_HELP_TEXT);

        gauge.set(SAMPLE_VALUE);

        Writer writer = new StringWriter();
        TextFormat.write004(writer, CollectorRegistry.defaultRegistry.metricFamilySamples());
        String prometheusString = writer.toString();

        Assert.assertEquals("# HELP sample_metric Help text\n" +
                "# TYPE sample_metric gauge\n" +
                "sample_metric{server_id=\"sample-server-id\",server_name=\"sample-server-name\",} 4.2\n", prometheusString);
    }

    @Test
    public void testGaugeWithExtraLabels() throws IOException {

        MinecraftMetrics minecraftMetrics = MinecraftMetrics.forServer(SAMPLE_SERVER_ID, SAMPLE_SERVER_NAME);
        MinecraftGauge gauge = minecraftMetrics.getGauge(SAMPLE_METRIC, SAMPLE_HELP_TEXT, "label_1", "label_2");

        gauge.labels("label_1_value", "label_2_value").set(SAMPLE_VALUE);

        Writer writer = new StringWriter();
        TextFormat.write004(writer, CollectorRegistry.defaultRegistry.metricFamilySamples());
        String prometheusString = writer.toString();

        Assert.assertEquals("# HELP sample_metric Help text\n" +
                "# TYPE sample_metric gauge\n" +
                "sample_metric{server_id=\"sample-server-id\",server_name=\"sample-server-name\"," +
                "label_1=\"label_1_value\",label_2=\"label_2_value\",} 4.2\n", prometheusString);
    }

}