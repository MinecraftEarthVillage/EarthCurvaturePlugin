package top.earthvillage.earthcurvatureplugin;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class 多国语言 {
    private final EarthCurvaturePlugin plugin;
    private YamlConfiguration config;
    private String defaultLanguage;
    // 控制台语言变量
    private String consoleLanguage;
    private final Map<String, YamlConfiguration> languages = new HashMap<>();

    public 多国语言(EarthCurvaturePlugin plugin) {
        this.plugin = plugin;
    }

    public void 检查语言文件() {
        // 创建语言文件目录
        File langDir = new File(plugin.getDataFolder(), "lang");
        // 如果语言目录不存在，则创建语言目录
        if (!langDir.exists()) {
            langDir.mkdirs();
        }

        // 定义所有默认语言文件
        String[] defaultLanguages = {
                "zh_cn.yml", "bo_cn.yml", "kk_cn.yml", "mn_cn.yml", "ug_cn.yml",
                "zh_tw.yml", "mn_mn.yml", "es_cu.yml", "lo_la.yml", "vi_vn.yml", "ko_kp.yml"
        };

        // 遍历所有默认语言文件
        for (String langFile : defaultLanguages) {
            File targetFile = new File(langDir, langFile);
            // 如果文件不存在，则保存默认语言文件
            if (!targetFile.exists()) {
                plugin.saveResource("lang/" + langFile, false);
            }
        }

        plugin.getLogger().info("默认语言文件检查完毕");
    }

    public void loadConfig() {
        // 加载主配置中的语言设置
        defaultLanguage = plugin.getConfig().getString("语言", "zh_cn");
        consoleLanguage = plugin.getConfig().getString("语言", "zh_cn");
        // 加载所有语言文件

        languages.clear();// 清空languages集合
        File langDir = new File(plugin.getDataFolder(), "lang");
        for (File file : langDir.listFiles()) {
            if (file.getName().endsWith(".yml")) {
                YamlConfiguration lang = YamlConfiguration.loadConfiguration(file);
                String langName = file.getName().replace(".yml", "");
                languages.put(langName, lang);
            }
        }
    }

    public String getMessage(String key) {
        return getMessage(key, defaultLanguage);
    }
/*
    public String getMessage(String key, Player player) {
        // 如果未来需要根据玩家语言选择，可以在此扩展
        return getMessage(key);
    }
*/
    // 根据键和语言获取消息
    private String getMessage(String key, String lang) {
        // 获取指定语言的配置文件，如果没有则获取默认语言的配置文件
        YamlConfiguration language = languages.getOrDefault(lang, languages.get(defaultLanguage));
        // 获取指定键的消息，如果没有则返回键本身
        String message = language.getString("messages." + key, key);
        // 将消息中的颜色代码转换为Minecraft颜色代码
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    // 根据key获取消息
    public String format(String key, Object... args) {
        // 获取消息
        String msg = getMessage(key);
        // 遍历参数
        for (int i = 0; i < args.length; i += 2) {
            // 如果参数长度为奇数，则跳出循环
            if (i+1 >= args.length) break;
            // 将参数替换为对应的值
            msg = msg.replace(String.valueOf(args[i]), String.valueOf(args[i+1]));
        }
        // 返回替换后的消息
        return msg;
    }
}