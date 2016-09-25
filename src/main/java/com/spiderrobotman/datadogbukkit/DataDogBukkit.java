package com.spiderrobotman.datadogbukkit;

import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.StatsDClient;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.regex.Pattern;

/**
 * Project: DataDogBukkit
 * Author: SpiderRobotMan
 * Date: Sep 24 2016
 * Website: http://www.spiderrobotman.com
 */
public class DataDogBukkit extends JavaPlugin {

    @Override
    public void onEnable() {
        //Save default config if not already there
        saveDefaultConfig();

        //Import config
        String DD_HOST = getConfig().getString("datadog.host", "localhost");
        int DD_PORT = getConfig().getInt("datadog.port", 8125);
        String DD_PREFIX = getConfig().getString("datadog.prefix", "bukkit.stats");

        //Start TPS monitor task
        Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(this, new TPS(), 100L, 1L);

        //Initialize StatsD client
        final StatsDClient statsd = new NonBlockingStatsDClient(DD_PREFIX, DD_HOST, DD_PORT);

        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                int entities = 0;
                int tiles = 0;
                int ping_count = 0;
                int ping_avg = 0;
                int ping_max = 0;
                int ping_min = 500;

                for(World w : Bukkit.getWorlds()) {
                    for(Chunk c : w.getLoadedChunks()) {
                        entities += c.getEntities().length;
                        tiles += c.getTileEntities().length;
                    }
                }

                for(Player p : Bukkit.getOnlinePlayers()) {
                    try {
                        int cping = getPlayerPing(p);
                        if(cping <= 0) continue;
                        if(cping > 500) cping = 500;
                        ping_count++;
                        ping_avg += cping;
                        if(cping < ping_min) ping_min = cping;
                        if(cping > ping_max) ping_max = cping;
                    } catch(Exception ignored) {}
                }

                ping_avg = ping_avg/ping_count;

                statsd.recordGaugeValue("players", Bukkit.getOnlinePlayers().size());
                statsd.recordGaugeValue("entities", entities);
                statsd.recordGaugeValue("tiles", tiles);
                statsd.recordGaugeValue("tps", TPS.getTPS());
                statsd.recordGaugeValue("ping.avg", ping_avg);
                statsd.recordGaugeValue("ping.min", ping_min);
                statsd.recordGaugeValue("ping.max", ping_max);
            }
        }, 0, 1200); //Run task every 1 minute.
    }

    private static int getPlayerPing(Player player) throws Exception {
        int ping;
        Class<?> craftPlayer = Class.forName("org.bukkit.craftbukkit." + getServerVersion() + "entity.CraftPlayer");
        Object converted = craftPlayer.cast(player);
        Method handle = converted.getClass().getMethod("getHandle");
        Object entityPlayer = handle.invoke(converted);
        Field pingField = entityPlayer.getClass().getField("ping");
        ping = pingField.getInt(entityPlayer);
        return ping;
    }

    private static String getServerVersion() {
        Pattern brand = Pattern.compile("(v|)[0-9][_.][0-9][0-9]?[_.][R0-9]*");
        String version;
        String pkg = Bukkit.getServer().getClass().getPackage().getName();
        String version0 = pkg.substring(pkg.lastIndexOf('.') + 1);
        if (!brand.matcher(version0).matches()) version0 = "";
        version = version0;
        return !"".equals(version) ? version + "." : "";
    }

}
