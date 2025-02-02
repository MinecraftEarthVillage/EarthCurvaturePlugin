package top.earthvillage.earthcurvatureplugin;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;


public class EarthCurvaturePlugin extends JavaPlugin implements Listener {
    private static Configuration config;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = new Configuration(getConfig());
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void 玩家移动事件(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getX() == to.getX() && from.getZ() == to.getZ()) return;

        Location loc = to.clone();
        Player player = event.getPlayer();

        // 处理X轴边界（东西经180度）
        if (Math.abs(loc.getX()) > config.xBoundary) {
            handleXBoundary(loc, player);
            event.setTo(loc);
            return;
        }

        // 处理Z轴边界（南北极）
        if (Math.abs(loc.getZ()) > config.zBoundary) {
            // 调用处理Z轴边界的函数
            handleZBoundary(loc, player);
            // 将事件的位置设置为新的位置
            event.setTo(loc);
        }
    }

    private void handleXBoundary(Location loc, Player player) {
        // 计算新坐标（边界内1格）
        double sign = Math.signum(loc.getX());
        double newX = (-sign * config.xBoundary) + (sign * 1);
        loc.setX(newX);

        // 创建新位置并保持朝向
        Location newLoc = loc.clone();
        newLoc.setYaw(player.getLocation().getYaw());
        newLoc.setPitch(player.getLocation().getPitch());

        // 使用teleport应用旋转
        player.teleport(newLoc);
    }

    private void handleZBoundary(Location loc, Player player) {
        // 地图参数
        final double halfWidth = config.xBoundary; // 配置文件里的X边界数值就是地图宽度的一半

        // 获取当前坐标
        double originalX = loc.getX();
        double originalZ = loc.getZ();
        double signZ = Math.signum(originalZ);

        // 计算对侧经线坐标
        double newX = originalX - halfWidth;
        if (newX < -halfWidth) {
            newX += halfWidth*2; // 处理西边界溢出
        }

        // 计算新Z坐标（边界内1格）
        double newZ = (signZ > 0) ? (config.zBoundary - 1) : (-config.zBoundary + 1);

        // 计算反转后的Yaw角度（精确转向）
        float originalYaw = player.getLocation().getYaw();
        float newYaw = (originalYaw + 180.0f) % 360.0f;
        if (newYaw < 0) newYaw += 360.0f;

        // 创建新位置并传送
        Location newLoc = new Location(
                loc.getWorld(),
                newX,
                loc.getY(),
                newZ,
                newYaw,
                player.getLocation().getPitch()
        );
        player.teleport(newLoc);

        // 执行传送
        player.teleport(newLoc);
        loc.setX(newX); // 同步更新事件坐标
        loc.setZ(newZ);
        //player.sendMessage("原方向:" + originalYaw + " → 新方向:" + newYaw);//调试信息
        // ====== 关键修复：强制视角同步 ======
        // 方法一：使用TeleportCause解决同步问题
        player.teleport(newLoc, PlayerTeleportEvent.TeleportCause.PLUGIN);

        // 方法二：延迟1 tick强制更新视角
        float finalNewYaw = newYaw;
        new BukkitRunnable() {
            @Override
            public void run() {
                Location syncLoc = newLoc.clone();
                syncLoc.setYaw(finalNewYaw);
                syncLoc.setPitch(player.getLocation().getPitch());
                player.teleport(syncLoc);
            }
        }.runTaskLater(this, 1);

    }

    private static class Configuration {
        public final double xBoundary;
        public final double zBoundary;

        public Configuration(FileConfiguration cfg) {
            xBoundary = cfg.getDouble("boundary.x", 30000);
            zBoundary = cfg.getDouble("boundary.z", 30000);
        }
    }
}