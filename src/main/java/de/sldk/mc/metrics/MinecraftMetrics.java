package de.sldk.mc.metrics;

public class MinecraftMetrics {

    private final String serverId;
    private final String serverName;

    private MinecraftMetrics(String serverId, String serverName) {

        this.serverId = serverId;
        this.serverName = serverName;
    }

    public static MinecraftMetrics forServer(String serverId, String serverName) {
        return new MinecraftMetrics(serverId, serverName);
    }

    public MinecraftGauge getGauge(String name, String help, String... labels) {
        return new MinecraftGauge(serverId, serverName, name, help, labels);
    }

}
