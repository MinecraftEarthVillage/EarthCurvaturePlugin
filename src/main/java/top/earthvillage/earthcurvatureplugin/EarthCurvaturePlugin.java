package top.earthvillage.earthcurvatureplugin;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;


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

    private static class Configuration {
        public final double xBoundary;
        public final double zBoundary;

        public Configuration(FileConfiguration cfg) {
            xBoundary = cfg.getDouble("boundary.x", 1000);
            zBoundary = cfg.getDouble("boundary.z", 1000);
        }
    }


    // 扫描并处理所有需要检测的实体
    private void checkAllEntities() {
        for (World world : getServer().getWorlds()) {
            Collection<Entity> entities = world.getEntities();
            for (Entity entity : entities) {
                // 过滤掉玩家和不需要处理的实体
                // 如果entity是Player类型，则跳过本次循环
//                if (entity instanceof Player) continue;
                // if (!(entity instanceof LivingEntity) && !(entity instanceof Vehicle)) continue;

                checkEntityBoundary(entity);
            }
        }
    }

    // 实体边界检测核心方法
    private void checkEntityBoundary(Entity entity) {
        Location loc = entity.getLocation().clone();
        boolean modified = false;
        boolean reverseVector = false;

        // 处理X轴
        if (Math.abs(loc.getX()) > config.xBoundary) {
            handleXBoundaryForEntity(loc);
            modified = true;
        }

        // Z轴处理
        // 如果loc的Z坐标的绝对值大于config的zBoundary
        // 如果实体的Z坐标绝对值大于配置的Z边界值
        if (Math.abs(loc.getZ()) > config.zBoundary) {
            // 处理实体的Z边界
            handleZBoundaryForEntity(loc);
            // 标记为已修改
            modified = true;
            // 设置反向向量
            reverseVector = true;
        }

        if (modified) {
            // 如果实体已经修改过，则返回
            if (entity.getVehicle() != null) return;
            handleYBoundary(loc); // 修正到可生成的位置
            // 开始处理
            Vector v = entity.getVelocity();
            entity.setVelocity(new Vector(0, 0, 0));
            // 获取实体中的乘客列表
            // 获取实体上的乘客列表
            List<Entity> passengers = entity.getPassengers();
            // 遍历乘客列表，将每个乘客从实体上移除
            for (Entity e : passengers) {
                entity.removePassenger(e);
            }
            // 将实体传送到指定位置
            entity.teleport(loc);
            // 遍历乘客列表，将每个乘客重新添加到实体上
            for (Entity e : passengers) {
                entity.addPassenger(e);
            }
            // 设置实体的速度
            if(reverseVector){
                v.setX(-v.getX());
                v.setY(-v.getY());
            }
            entity.setVelocity(v);
        }
    }

    // 非玩家实体X轴处理
    // 处理实体的X边界
    private void handleXBoundaryForEntity(Location loc) {
        // 如果实体的X坐标大于0，则将实体的X坐标设置为-config.xBoundary，否则设置为config.xBoundary
        loc.setX((loc.getX() > 0 ? -config.xBoundary : config.xBoundary) );
    }

    // 实体Z轴越界处理
    private void handleZBoundaryForEntity(Location loc) {
        // 计算对侧坐标
        // 计算新的X坐标
        double newX = loc.getX() - config.xBoundary;
        // 如果新的X坐标小于-config.xBoundary，则将其加上2倍的config.xBoundary
        if (newX < -config.xBoundary) {
            newX += config.xBoundary * 2;
        }

        // 计算新Z坐标
        double newZ = loc.getZ() > 0 ? config.zBoundary - 1 : 1 - config.zBoundary;

        // 调整Yaw方向（180度反转）
        float newYaw = (loc.getYaw() + 180) % 360;
        if (newYaw < 0) newYaw += 360;

        // 应用新坐标和角度

// 设置loc对象的x坐标为newX
        loc.setX(newX );
// 设置loc对象的z坐标为newZ
        loc.setZ(newZ);
        // 设置新的朝向
        loc.setYaw(newYaw);
    }


    // 尝试处理Y轴，去解决卡墙和悬空问题
    private static void handleYBoundary(Location loc) {
        Integer y = getSpawnableY(loc);
        System.out.println("TP Y坐标：" + y);
        loc.setY(Math.round(y == null ? loc.getY() : y));
    }

    private static Integer getSpawnableY(Location loc) {
        // 获取位置所在的世界
        World w = loc.getWorld();
        // 获取位置所在区块的X坐标
        int x = (int) Math.round(loc.getX());
        // 获取位置所在区块的Y坐标
        int y = (int) Math.round(loc.getY());
        // 获取位置所在区块的Z坐标
        int z = (int) Math.round(loc.getZ());

        // 定义一个函数，用于判断方块是否是固体
        Function<Block, Boolean> motionBlockingMaterial = b -> {
            Material m = b.getType();
            return m != Material.COBWEB && m != Material.BAMBOO_SAPLING && b.getType().isSolid();
        };

        // 定义一个函数，用于判断方块是否是空气
        Function<Block, Boolean> isAir = b -> b.getType().isAir();

        // 遍历y坐标
        boolean b1 = false;
        boolean b2 = false;
        for(int i = 0;; i++) {
            if (b1 && b2) break;
            int y2 = y + (i % 2 == 0 ? i / 2 : (i + 1) / -2);
            // 如果y坐标大于最大高度，则跳过
            if (y2 > w.getMaxHeight()) {
                b1 = true;
                continue;
            }
            // 如果y坐标小于最小高度，则跳过
            if (y2 < w.getMinHeight()) {
                b2 = true;
                continue;
            }
            // 获取当前位置的方块
            Block b = w.getBlockAt(x, y2, z);
            // 如果方块不是阻挡材料，则跳过
            if(!motionBlockingMaterial.apply(b)) {
                if (y2 < -30) System.out.println("方块x" + x + "y" + y2 + "z" + z + "不能站脚");
                continue;
            } // 不能站脚
            // 如果y坐标等于最大高度或者上方是空气，则返回y+1
            if (y == w.getMaxHeight() || isAir.apply(w.getBlockAt(x, y + 1, z)))
                return y + 1; // 上方是空气，可TP
        }
        // 不行找水面
        b1 = false;
        b2 = false;
        for(int i = 0;; i++) {
            if (b1 && b2) break;
            int y2 = y + (i % 2 == 0 ? i / 2 : (i + 1) / -2);
            // 如果y坐标大于最大高度，则跳过
            if (y2 > w.getMaxHeight()) {
                b1 = true;
                continue;
            }
            // 如果y坐标小于最小高度，则跳过
            if (y2 < w.getMinHeight()) {
                b2 = true;
                continue;
            }
            // 获取当前位置的方块
            Block b = w.getBlockAt(x, y2, z);
            if (b.getType() == Material.WATER && (y == w.getMaxHeight() || isAir.apply(w.getBlockAt(x, y + 1, z))))
                return y + 1; // 上方是空气，可TP
        }
        return null;
    }
}