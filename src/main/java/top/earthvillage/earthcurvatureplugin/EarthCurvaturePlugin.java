package top.earthvillage.earthcurvatureplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class EarthCurvaturePlugin extends JavaPlugin implements Listener {
    private static EarthCurvaturePlugin instance;//获取插件实例的静态引用

    //变量区↓

    // 定义一个静态的Configuration类型的变量config（旧的）
    //private static Configuration config;

    // 定义一个final类型的Map类型的变量boundary，键为String类型，值为Integer数组类型（用于配置文件）
    public final Map<String, Integer[]> boundary = new HashMap<>();
    // 定义一个静态的boolean类型的变量调试信息
    private static boolean 调试信息;
    public boolean 跨越时发送聊天栏消息;
    public boolean 恢复骑乘;
    public 多国语言 langConfig;

    //变量区↑

    public void 读取配置项(){
        调试信息 = getConfig().getBoolean("调试信息",false);
        跨越时发送聊天栏消息 = getConfig().getBoolean("跨越时发送聊天栏消息",false);
        恢复骑乘 = getConfig().getBoolean("恢复骑乘",true);
        // 遍历配置文件中的所有键
        for(String key : getConfig().getKeys(true)){
            // 如果键以"boundary."开头，以".x"结尾
            if(key.startsWith("boundary.") && key.endsWith(".x")){
                // 获取键中的名称
                String name = key.split("\\.")[1];
                // 将名称和对应的x、z坐标存入boundary中
                boundary.put(name, new Integer[] {
                        // 获取配置文件中指定名称的边界x坐标
                        getConfig().getInt("boundary." + name + ".x"),
                        // 获取配置文件中指定名称的边界z坐标
                        getConfig().getInt("boundary." + name + ".z")
                });
            }
        }
        langConfig.loadConfig();
    }
    // 生成默认世界边界配置
    private void generateDefaultWorldConfig() {
        // 默认边界值（可自定义）
        final int DEFAULT_X_BOUNDARY = 1000;
        final int DEFAULT_Z_BOUNDARY = 1000;

// 遍历所有世界
        for (World world : getServer().getWorlds()) {
            // 获取世界的名称
            String worldName = world.getName();
            // 获取世界的x坐标路径
            String xPath = "boundary." + worldName + ".x";
            // 获取世界的z坐标路径
            String zPath = "boundary." + worldName + ".z";

            // 如果配置不存在，则设置默认值
            // 如果配置文件中不包含xPath或zPath，则设置默认的x z边界值
            if (!getConfig().contains(xPath) || !getConfig().contains(zPath)) {
                getConfig().set(xPath, DEFAULT_X_BOUNDARY);
                getConfig().set(zPath, DEFAULT_Z_BOUNDARY);
                getLogger().info(langConfig.format("世界配置生成",
                        "{世界}", worldName,
                        "{X-value}", DEFAULT_X_BOUNDARY,
                        "{Z-value}", DEFAULT_Z_BOUNDARY));
            }
        }
        // 保存配置文件
        saveConfig();
    }

    @Override
    public void onEnable() {
        instance = this; // 在启用时保存实例
        try {//用这个抛出异常
            saveDefaultConfig();//从jar复制示例模板配置文件
            langConfig = new 多国语言(this);//先初始化语言配置
            langConfig.saveDefaultConfig();
            langConfig.loadConfig();
            // 生成默认的世界配置（此时langConfig已可用，一定要把generateDefaultWorldConfig放在后面）
            generateDefaultWorldConfig();
            读取配置项();

            System.out.println(langConfig.getMessage("插件启动"));

            getServer().getPluginManager().registerEvents(this, this);
            // 实体检测定时任务（每10tick执行一次）
            new BukkitRunnable() {
                @Override
                public void run() {
                    checkAllEntities();
                }
            }.runTaskTimer(this, 0L, 10L);
        } catch (Exception e) {
            // 获取日志记录器
            getLogger().severe("插件启动失败: " + e.getMessage());
            // 打印异常堆栈信息
            e.printStackTrace();
            // 禁用插件
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        //System.out.println(langConfig.getMessage("插件关闭"));/
    }

    public void 重载(){
        onDisable();
        instance.getLogger().warning("重新加载");
        //System.out.println(langConfig.getMessage("重新加载"));
        onEnable();
    }



    // 获取EarthCurvaturePlugin实例
    public static EarthCurvaturePlugin getInstance() {
        // 返回EarthCurvaturePlugin实例
        return instance;
    }



    //命令
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args[0].equals("reload") && sender.hasPermission("earthCurvature.重新加载")) {
            sender.sendMessage(langConfig.getMessage("插件关闭"));
            重载();
            sender.sendMessage(langConfig.getMessage("重新加载"));
            sender.sendMessage(langConfig.getMessage("插件启动"));
            return true;
        }
        return true;
    }
    // 扫描并处理所有需要检测的实体
    private void checkAllEntities() {
        for (World world : getServer().getWorlds()) {
            Integer[] bounds = boundary.get(world.getName());
            if (bounds == null) {
                if(调试信息){
                    getLogger().warning(langConfig.format("世界未配置", world.getName()));
                  //  getLogger().warning("未找到世界 " + world.getName() + " 的边界配置，已跳过。");
                }
                continue; // 跳过未配置的世界
            }
            for (Entity entity : world.getEntities()) {
                checkEntityBoundary(entity, bounds[0], bounds[1]);
            }
        }
    }

    // 实体边界检测核心方法
    private void checkEntityBoundary(Entity entity, int xBoundary, int zBoundary) {
        Location loc = entity.getLocation().clone();
        loc.setX(loc.getX());
        loc.setY(Math.round(loc.getY()));
        loc.setZ(loc.getZ());
        boolean modified = false;
        boolean reverseVector = false;

        // 处理X轴
        if (Math.abs(loc.getX()) > xBoundary) {
            handleXBoundary(loc, xBoundary);
            modified = true;
            if (跨越时发送聊天栏消息) {
                //entity.sendMessage("你刚刚环绕了这个星球一圈！");
                entity.sendMessage(langConfig.getMessage("跨越X坐标边界"));
            }
        }
        // Z轴处理
        // 如果loc的Z坐标的绝对值大于config的zBoundary
        // 如果实体的Z坐标绝对值大于配置的Z边界值
        if (Math.abs(loc.getZ()) > zBoundary) {
            // 处理实体的Z边界
            handleZBoundary(loc,xBoundary,zBoundary);
            // 标记为已修改
            modified = true;
            // 设置反向向量
            reverseVector = true;
            if (跨越时发送聊天栏消息) {
                entity.sendMessage(langConfig.getMessage("跨越极点"));
            }
        }

        if (modified) {
            // 如果实体已经修改过，则返回
            if (entity.getVehicle() != null) return;
            // 处理Y轴边界
            handleYBoundary(loc);
            // 获取实体的速度
            //Vector v = entity.getVelocity().clone();
            // 设置实体的速度为(0, 0, 0)
            entity.setVelocity(new Vector(0, 0, 0));
            // 创建实体的乘客列表副本
            List<Entity> passengers = new ArrayList<>(entity.getPassengers()); // 创建副本
            // 遍历乘客列表，将每个乘客从实体上移除
            // 移除乘客时遍历副本
            for (Entity e : passengers) {
                // 从实体中移除乘客
                entity.removePassenger(e);
                e.teleport(loc);
            }

            // 将实体传送到指定位置
            entity.teleport(loc);
            // 遍历乘客列表，将每个乘客重新添加到实体上
            // 重新添加时检查有效性
            for (Entity e : passengers) {
                if (恢复骑乘) {
                    if (e.isValid()) entity.addPassenger(e);
                }
            }
            // 设置实体的速度（这个没有起效果）
            /*
            if(reverseVector){
                v.setX(-v.getX());
                v.setY(-v.getZ());
            }
            entity.setVelocity(v);
             */
        }
    }

    // 处理实体的X边界越界
    private void handleXBoundary(Location loc,int xBoundary) {
        // 如果实体的X坐标大于0，则将实体的X坐标设置为-config.xBoundary，否则设置为config.xBoundary
        loc.setX((loc.getX() > 0 ? -xBoundary : xBoundary) );
    }

    // 实体Z轴越界处理
    private void handleZBoundary(Location loc,int xBoundary, int zBoundary) {
        // 计算对侧坐标
        // 计算新的X坐标
        double newX = loc.getX() - xBoundary;
        // 如果新的X坐标小于-config.xBoundary，则将其加上2倍的config.xBoundary
        if (newX < -xBoundary) {
            newX += xBoundary * 2;
        }

        // 计算新Z坐标
// 如果loc的z坐标大于0，则将newZ赋值为config的zBoundary减1，否则将newZ赋值为1减去config的zBoundary
        double newZ = loc.getZ() > 0 ? zBoundary - 1 : 1 - zBoundary;

        // 调整Yaw方向（180度反转）
        float newYaw = (loc.getYaw() + 180) % 360;
        if (newYaw < 0) newYaw += 360;

        // 应用新坐标和角度

// 设置loc对象的x坐标为newX
        loc.setX(newX);
// 设置loc对象的z坐标为newZ
        loc.setZ(newZ);
        // 设置新的朝向
        loc.setYaw(newYaw);
    }




    //新高度修正法
    // 尝试处理Y轴，去解决卡墙和悬空问题
    private static void handleYBoundary(Location loc) {
        // 获取可生成的Y坐标
        Integer 安全落地y = findBlock(loc);
        // 打印TP Y坐标，调试信息
        // 设置Y坐标
// 将loc的y坐标设置为“安全落地y”的值加1，如果安全y为null，则设置为loc的y坐标（无更改）
        loc.setY(/*Math.round*/(安全落地y+1));
        //这个“安全落地y”也就是下面getSpawnableYEx方法 从上往下找到的第一个方块的位置
        //要使得玩家能够站上去，就要加一
    }

    //新高度修正法——寻找可站立方块
    private static Integer findBlock(Location loc) {
        //这个方法基础是——从上往下遍历方块
        // 获取位置所在的世界
        World 当前世界 = loc.getWorld();//←变量取名太随便（尤其是只写个字母），以后会看不懂的（doge）不管如何，I Love 汉字~

        int x = (int) Math.round(loc.getX()-0.5);// 获取位置所在区块的X坐标（带四舍五入）(-0.5修正误差，我也不知道为什么会有个BUG)
        //int x = (int) (loc.getX()-0.5);// 获取位置所在区块的X坐标（不四舍五入）

        // 获取位置所在区块的Y坐标
        int y = (int) Math.round(loc.getY());

        // int z = (int) Math.round(loc.getZ());// 获取位置所在区块的Z坐标（带四舍五入算法的）
        int z = (int) loc.getZ();// 获取位置所在区块的Z坐标（不四舍五入）
        //System.out.println("目的地Z坐标:"+z);// 打印Z坐标，调试信息
        // 从256（兼容旧版本）开始，向下遍历，直到-64（这里不确定要不要改0）

        for (int j = 256; j >= -64; j--) {
            // 获取当前位置的方块
            Block block = 当前世界.getBlockAt(x, j, z);
            // 如果方块的材质是固体或者液体（只要不是空气），则返回当前Y坐标（取名为j）
            if (!block.getType().isAir()) {
                if (调试信息) {         //TMD为了这一段话我又得搞个静态实例，多写几行
                    // 获取EarthCurvaturePlugin实例
                    String message = EarthCurvaturePlugin.getInstance().langConfig.format(
                            // 格式化debug-teleport消息
                            "控制台输出",
                            // 将x、j、z替换为对应的值
                            "{x}", x,
                            "{y}", j,
                            "{z}", z
                    );
                    // 获取EarthCurvaturePlugin实例的Logger
                    EarthCurvaturePlugin.getInstance().getLogger().info(
                            // 输出消息，去除颜色代码
                            ChatColor.stripColor(message)
                    );
                }
                return j;
            }
        }

        // 如果没有找到固体方块，则直接丢虚空(doge
        return y;

    }

}