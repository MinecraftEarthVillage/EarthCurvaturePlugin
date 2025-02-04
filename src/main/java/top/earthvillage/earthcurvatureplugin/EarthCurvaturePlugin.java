package top.earthvillage.earthcurvatureplugin;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Collection;


public class EarthCurvaturePlugin extends JavaPlugin implements Listener {
    private static Configuration config;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = new Configuration(getConfig());
        getServer().getPluginManager().registerEvents(this, this);
        // 新增实体检测定时任务（每20 ticks执行一次）
        new BukkitRunnable() {
            @Override
            public void run() {
                checkAllEntities();
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    // 在玩家移动事件处理方法中添加载具检测
    @EventHandler
    public void 玩家移动事件(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getX() == to.getX() && from.getZ() == to.getZ()) return;

        Location loc = to.clone();
        Player player = event.getPlayer();

        // 处理X轴边界（东西经180度）
        if (Math.abs(loc.getX()) > config.xBoundary) {
            handleXBoundary(loc, player); // 修改方法签名
            event.setTo(loc);
            return;
        }

        // 处理Z轴边界（南北极）
        if (Math.abs(loc.getZ()) > config.zBoundary) {
            handleZBoundary(loc, player); // 修改方法签名
            event.setTo(loc);
        }
    }

    // 新增方法：统一处理载具与玩家的同步传送
    private void handleVehicleAndPlayer(Player player, Location newLoc, float yawOffset) {
        Entity vehicle = player.getVehicle();
        if (vehicle == null) return;

        // 让玩家暂时离开载具
        player.leaveVehicle();

        // 计算载具与玩家的相对偏移
        Location vehicleLoc = vehicle.getLocation();
        double offsetX = vehicleLoc.getX() - player.getLocation().getX();
        double offsetZ = vehicleLoc.getZ() - player.getLocation().getZ();

        // 计算载具新坐标和方向（应用Yaw偏移）
        Location vehicleNewLoc = newLoc.clone().add(offsetX, 0, offsetZ);
        float vehicleNewYaw = (vehicleLoc.getYaw() + yawOffset) % 360.0f;
        if (vehicleNewYaw < 0) vehicleNewYaw += 360.0f;
        vehicleNewLoc.setYaw(vehicleNewYaw); // 同步载具Yaw
        vehicleNewLoc.setPitch(vehicleLoc.getPitch());

        // 停止载具速度并传送
        vehicle.setVelocity(new Vector(0, 0, 0));
        vehicle.teleport(vehicleNewLoc, PlayerTeleportEvent.TeleportCause.PLUGIN);

        // 延迟重新绑定玩家到载具
        new BukkitRunnable() {
            @Override
            public void run() {
                if (vehicle.isValid() && player.isOnline()) {
                    vehicle.addPassenger(player);
                }
            }
        }.runTaskLater(this, 2);
    }
    // X轴越界
    private void handleXBoundary(Location loc, Player player) {
        double sign = Math.signum(loc.getX());
        double newX = (-sign * config.xBoundary) + (sign * 1);
        loc.setX(newX);

        Location newLoc = loc.clone();
        newLoc.setYaw(player.getLocation().getYaw());
        newLoc.setPitch(player.getLocation().getPitch());

        // 处理载具和玩家（这里不需要反转了）
        handleVehicleAndPlayer(player, newLoc,0.0f);
        player.teleport(newLoc);
    }

/*
Z方向就相当于现实中南北方向（沿着经线）
这个Z轴越界逻辑想破脑袋几个月了才摸索出来
在平面地图还原跨越极点是很难想象的
 */
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
            newX += halfWidth * 2; // 处理西边界溢出
        }

        // 计算新Z坐标（边界内1格）
        double newZ = (signZ > 0) ? (config.zBoundary - 1) : (-config.zBoundary + 1);

        // 计算玩家和载具的Yaw反转角度（180度）
        float originalYaw = player.getLocation().getYaw();
        float newYaw = (originalYaw + 180.0f) % 360.0f;
        if (newYaw < 0) newYaw += 360.0f;

        Location newLoc = new Location(
                loc.getWorld(),
                newX,
                loc.getY(),
                newZ,
                newYaw,
                player.getLocation().getPitch()
        );

        // 处理载具和玩家，传入Yaw偏移量（180度）
        handleVehicleAndPlayer(player, newLoc, 180.0f);

        // 最终执行传送
        player.teleport(newLoc);
        loc.setX(newX); // 同步更新事件坐标
        loc.setZ(newZ);
        player.sendMessage("你成功环绕了地球一圈！");//调试信息
        player.sendMessage("原方向:" + originalYaw + " → 新方向:" + newYaw);//调试信息

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

    //之前写的防卡地里或高空坠落，但是那样不真实（你总不可能碰到崖壁自动瞬移到顶上、遇到悬崖直接瞬移到地面上吧）
    private Location 获取安全高度(Location loc) {
        World world = loc.getWorld();
        int x = loc.getBlockX();
        int z = loc.getBlockZ();

        // 精确搜索安全高度（包括水下）
        for (int y = world.getMaxHeight(); y > world.getMinHeight(); y--) {
            Location testLoc = new Location(world, x, y, z);
            if (!testLoc.getBlock().getType().isSolid() &&
                    testLoc.clone().add(0, 1, 0).getBlock().isPassable()) {
                return testLoc.add(0.5, 0, 0.5); // 居中坐标
            }
        }
        return loc; // 保底返回原坐标
    }









    // 新增实体移动事件监听


    private void handleEntityMovement(Entity entity, Location to) {
        Location loc = to.clone();

        // 处理X轴边界
        if (Math.abs(loc.getX()) > config.xBoundary) {
            handleXBoundary2(entity, loc);
            entity.teleport(loc);
            return;
        }

        // 处理Z轴边界
        if (Math.abs(loc.getZ()) > config.zBoundary) {
            handleZBoundary2(entity, loc);
            entity.teleport(loc);
        }
    }

    // 非玩家实体X轴处理
    private void handleXBoundary2(Entity entity, Location loc) {
        double sign = Math.signum(loc.getX());
        double newX = (-sign * config.xBoundary) + (sign * 1);
        loc.setX(newX);

        // 矿车等载具需要重置速度
        if (entity instanceof Vehicle) {
            entity.setVelocity(new Vector(0, 0, 0));
        }
    }

    // 非玩家实体Z轴处理
    private void handleZBoundary2(Entity entity, Location loc) {
        final double halfWidth = config.xBoundary;
        double originalX = loc.getX();
        double originalZ = loc.getZ();
        double signZ = Math.signum(originalZ);

        // 计算对侧经线坐标
        double newX = (originalX - halfWidth + 2 * halfWidth) % (2 * halfWidth) - halfWidth;
        double newZ = (signZ > 0) ? (config.zBoundary - 1) : (-config.zBoundary + 1);

        // 应用新坐标
        loc.setX(newX);
        loc.setZ(newZ);

        // 生物实体反转移动方向（模拟环绕效果）
        if (entity instanceof LivingEntity) {
            LivingEntity livingEntity = (LivingEntity) entity;
            Location targetLoc = livingEntity.getEyeLocation();
            targetLoc.setYaw((targetLoc.getYaw() + 180) % 360);
            livingEntity.teleport(targetLoc);
        }

        // 载具类实体需要重置速度
        if (entity instanceof Vehicle) {
            entity.setVelocity(new Vector(0, 0, 0));
            // 矿车需要二次校准
            new BukkitRunnable() {
                @Override
                public void run() {
                    entity.teleport(loc.clone().add(0, 0.1, 0)); // 防止卡轨
                }
            }.runTaskLater(this, 1);
        }
    }




    // 扫描并处理所有需要检测的实体
    private void checkAllEntities() {
        for (World world : getServer().getWorlds()) {
            Collection<Entity> entities = world.getEntities();
            for (Entity entity : entities) {
                // 过滤掉玩家和不需要处理的实体
                if (entity instanceof Player) continue;
                if (!(entity instanceof LivingEntity) && !(entity instanceof Vehicle)) continue;

                checkEntityBoundary(entity);
            }
        }
    }

    // 实体边界检测核心方法
    private void checkEntityBoundary(Entity entity) {
        Location loc = entity.getLocation().clone();
        boolean modified = false;

        // 处理X轴
        if (Math.abs(loc.getX()) > config.xBoundary) {
            handleXBoundaryForEntity(loc);
            modified = true;
        }

        // 处理Z轴
        if (Math.abs(loc.getZ()) > config.zBoundary) {
            handleZBoundaryForEntity(loc);
            modified = true;
        }

        if (modified) {
            entity.teleport(loc);
            // 特殊处理矿车类实体
            if (entity instanceof Minecart) {
                entity.setVelocity(new Vector(0, 0, 0));
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        entity.teleport(loc.clone().add(0, 0.1, 0));
                    }
                }.runTaskLater(this, 1);
            }
        }
    }

    // 非玩家实体X轴处理
    private void handleXBoundaryForEntity(Location loc) {
        double sign = Math.signum(loc.getX());
        double newX = (-sign * config.xBoundary) + (sign * 1);
        loc.setX(newX);
    }

    // 非玩家实体Z轴处理
    private void handleZBoundaryForEntity(Location loc) {
        final double halfWidth = config.xBoundary;
        double originalX = loc.getX();
        double newX = (originalX - halfWidth + 2 * halfWidth) % (2 * halfWidth) - halfWidth;
        double newZ = (Math.signum(loc.getZ()) > 0) ?
                (config.zBoundary - 1) : (-config.zBoundary + 1);

        loc.setX(newX);
        loc.setZ(newZ);

        // 生物实体转向
        if (loc.getWorld().getNearbyEntities(loc, 1, 1, 1).stream()
                .anyMatch(e -> e instanceof LivingEntity)) {
            loc.setYaw((loc.getYaw() + 180) % 360);
        }
    }
}