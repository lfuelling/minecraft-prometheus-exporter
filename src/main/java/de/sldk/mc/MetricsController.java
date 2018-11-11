package de.sldk.mc;

import de.sldk.mc.metrics.MinecraftGauge;
import de.sldk.mc.metrics.MinecraftMetrics;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public class MetricsController extends AbstractHandler {

    private final PrometheusExporter exporter;
    private final boolean individualPlayerMetrics;

    private final MinecraftGauge players;
    private final MinecraftGauge loadedChunks;
    private final MinecraftGauge playersOnline;
    private final MinecraftGauge entities;
    private final MinecraftGauge livingEntities;
    private final MinecraftGauge memory;
    private final MinecraftGauge tps;

    private final MinecraftGauge playerStats;
    private final MinecraftGauge playersWithNames;

    public MetricsController(PrometheusExporter exporter, boolean individualPlayerMetrics) {
        this.exporter = exporter;
        this.individualPlayerMetrics = individualPlayerMetrics;

        MinecraftMetrics minecraftMetrics =
                MinecraftMetrics.forServer(exporter.getServer().getServerName(), exporter.getServer().getServerId());

        players = minecraftMetrics.getGauge("mc_players_total", "Online and offline players", "state");
        loadedChunks = minecraftMetrics.getGauge("mc_loaded_chunks_total", "Chunks loaded per world", "world");
        playersOnline = minecraftMetrics.getGauge("mc_players_online_total", "Players currently online per world", "world");
        entities = minecraftMetrics.getGauge("mc_entities_total", "Entities loaded per world", "world");
        livingEntities = minecraftMetrics.getGauge("mc_living_entities_total", "Living entities loaded per world", "world");
        memory = minecraftMetrics.getGauge("mc_jvm_memory", "JVM memory usage", "type");
        tps = minecraftMetrics.getGauge("mc_tps", "Server TPS (ticks per second)");

        playerStats = minecraftMetrics.getGauge("mc_player_statistic", "Player statistics", "player_name", "statistic");
        playersWithNames = minecraftMetrics.getGauge("mc_player_online", "Online state by player name", "name");
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {

        if (!target.equals("/metrics")) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        tps.set(exporter.getAverageTPS());

        /*
         * Bukkit API calls have to be made from the main thread.
         * That's why we use the BukkitScheduler to retrieve the server stats.
         * */
        Future<Object> future = exporter.getServer().getScheduler().callSyncMethod(exporter, () -> {
            players.labels("online").set(Bukkit.getOnlinePlayers().size());
            players.labels("offline").set(Bukkit.getOfflinePlayers().length);

            for (World world : Bukkit.getWorlds()) {
                loadedChunks.labels(world.getName()).set(world.getLoadedChunks().length);
                playersOnline.labels(world.getName()).set(world.getPlayers().size());
                entities.labels(world.getName()).set(world.getEntities().size());
                livingEntities.labels(world.getName()).set(world.getLivingEntities().size());
            }

            if (individualPlayerMetrics) {
                addIndividualPlayerMetrics(playerStats, playersWithNames);
            }

            memory.labels("max").set(Runtime.getRuntime().maxMemory());
            memory.labels("free").set(Runtime.getRuntime().freeMemory());

            return null;
        });

        try {
            future.get();

            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(TextFormat.CONTENT_TYPE_004);

            TextFormat.write004(response.getWriter(), CollectorRegistry.defaultRegistry.metricFamilySamples());

            baseRequest.setHandled(true);
        } catch (InterruptedException | ExecutionException e) {
            exporter.getLogger().warning("Failed to read server statistic: " + e.getMessage());
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private void addIndividualPlayerMetrics(MinecraftGauge playerStats, MinecraftGauge playersWithNames) {
        try {
            for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                Map<String, Integer> statistics = getStatistics(player.getPlayer());

                statistics.forEach((stat, value) -> playerStats.labels(player.getName(), stat).set(value));

                playersWithNames.labels(player.getName()).set(player.isOnline() ? 1 : 0);
            }
        } catch (Exception e) {
            exporter.getLogger().warning("Failed to read player statistic: " + e.getMessage());
        }
    }

    private static Map<String, Integer> getStatistics(Player player) {

        if (player == null) {
            return Collections.emptyMap();
        }

        EntityType[] entityTypes = EntityType.values();
        Material[] materials = Material.values();

        Statistic[] statistics = Statistic.values();

        return Arrays.stream(statistics).collect(Collectors.toMap(Enum::name, statistic -> {

            if (Statistic.Type.UNTYPED == statistic.getType()) {
                return player.getStatistic(statistic);
            } else if (Statistic.Type.ENTITY == statistic.getType()) {
                return Arrays.stream(entityTypes).map(type -> getSafeStatistic(player, statistic, type))
                        .filter(Objects::nonNull)
                        .mapToInt(Integer::intValue).sum();
            } else if (Statistic.Type.ITEM == statistic.getType()
                    || Statistic.Type.BLOCK == statistic.getType()) {
                return Arrays.stream(materials).map(material -> getSafeStatistic(player, statistic, material))
                        .filter(Objects::nonNull)
                        .mapToInt(Integer::intValue).sum();
            }

            return 0;
        }));
    }

    private static Integer getSafeStatistic(Player player, Statistic statistic, Material material) {
        try {
            return player.getStatistic(statistic, material);
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer getSafeStatistic(Player player, Statistic statistic, EntityType type) {
        try {
            return player.getStatistic(statistic, type);
        } catch (Exception e) {
            return null;
        }
    }
}
