package gg.fotia.crates;

import gg.fotia.crates.command.CrateCommand;
import gg.fotia.crates.config.ConfigManager;
import gg.fotia.crates.crate.CrateManager;
import gg.fotia.crates.database.DatabaseManager;
import gg.fotia.crates.gui.GuiManager;
import gg.fotia.crates.history.HistoryManager;
import gg.fotia.crates.hologram.HologramManager;
import gg.fotia.crates.hook.FotiaCratesExpansion;
import gg.fotia.crates.key.KeyManager;
import gg.fotia.crates.lang.LanguageManager;
import gg.fotia.crates.listener.BlockListener;
import gg.fotia.crates.listener.EntityInteractPacketListener;
import gg.fotia.crates.listener.GuiListener;
import gg.fotia.crates.listener.PlayerListener;
import gg.fotia.crates.modelengine.ModelEngineManager;
import gg.fotia.crates.pity.PityManager;
import gg.fotia.crates.reward.PendingRewardManager;
import gg.fotia.crates.reward.RewardItemDeliveryService;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class FotiaCrates extends JavaPlugin {

    private static FotiaCrates instance;
    private ConfigManager configManager;
    private LanguageManager languageManager;
    private DatabaseManager databaseManager;
    private CrateManager crateManager;
    private KeyManager keyManager;
    private GuiManager guiManager;
    private HistoryManager historyManager;
    private PityManager pityManager;
    private ModelEngineManager modelEngineManager;
    private PendingRewardManager pendingRewardManager;
    private RewardItemDeliveryService rewardItemDeliveryService;
    private HologramManager hologramManager;
    private EntityInteractPacketListener entityInteractPacketListener;
    private Economy economy;

    @Override
    public void onEnable() {
        instance = this;

        // 加载配置
        configManager = new ConfigManager(this);
        configManager.loadConfigs();

        // 加载多语言系统
        languageManager = new LanguageManager(this);

        // 初始化数据库
        databaseManager = new DatabaseManager(this);
        if (!databaseManager.initialize()) {
            getLogger().severe("Failed to initialize database! Disabling plugin...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 初始化管理器
        keyManager = new KeyManager(this);
        crateManager = new CrateManager(this);
        guiManager = new GuiManager(this);
        historyManager = new HistoryManager(this);
        pityManager = new PityManager(this);
        modelEngineManager = new ModelEngineManager(this);
        pendingRewardManager = new PendingRewardManager(this);
        rewardItemDeliveryService = new RewardItemDeliveryService(this);
        hologramManager = new HologramManager(this);

        // 加载抽奖箱
        crateManager.loadCrates();

        // 加载抽奖箱位置
        crateManager.loadLocations();

        // 延迟生成 ModelEngine 模型和全息显示（等待世界加载完成）
        getServer().getScheduler().runTaskLater(this, () -> {
            // 为已放置的宝箱生成 ModelEngine 模型
            spawnAllCrateModels();
            // 创建全息显示
            hologramManager.createAllHolograms();
        }, 40L); // 延迟2秒

        // 设置Vault经济
        setupEconomy();

        // 注册命令
        CrateCommand crateCommand = new CrateCommand(this);
        getCommand("crate").setExecutor(crateCommand);
        getCommand("crate").setTabCompleter(crateCommand);

        // 注册监听器
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);

        // 注册PacketEvents数据包监听器（用于ModelEngine左键预览）
        entityInteractPacketListener = new EntityInteractPacketListener(this);
        entityInteractPacketListener.register();

        // 注册PlaceholderAPI扩展
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new FotiaCratesExpansion(this).register();
            getLogger().info("PlaceholderAPI expansion registered!");
        }

        getLogger().info("FotiaCrates has been enabled!");
    }

    @Override
    public void onDisable() {
        if (entityInteractPacketListener != null) {
            entityInteractPacketListener.unregister();
        }
        if (hologramManager != null) {
            hologramManager.cleanup();
        }
        if (modelEngineManager != null) {
            modelEngineManager.cleanup();
        }
        if (databaseManager != null) {
            databaseManager.close();;
        }
        getLogger().info("FotiaCrates has been disabled!");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("Vault not found! Economy features will be disabled.");
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().warning("No economy provider found! Economy features will be disabled.");
            return false;
        }
        economy = rsp.getProvider();
        getLogger().info("Vault economy hooked successfully!");
        return true;
    }

    public void reload() {
        configManager.loadConfigs();
        languageManager.reload();
        keyManager.reload();
        crateManager.loadCrates();
        crateManager.loadLocations();
        guiManager.reload();
        hologramManager.reload();
        // 重新生成 ModelEngine 模型
        spawnAllCrateModels();
    }

    /**
     * 为所有已放置的宝箱生成 ModelEngine 模型
     */
    private void spawnAllCrateModels() {
        if (!modelEngineManager.isAvailable()) {
            return;
        }

        for (var crateLocation : crateManager.getCrateLocations()) {
            org.bukkit.World world = getServer().getWorld(crateLocation.getWorld());
            if (world == null) continue;

            org.bukkit.Location loc = crateLocation.toLocation(world);
            if (loc == null) continue;

            var crate = crateManager.getCrate(crateLocation.getCrateId());
            if (crate == null || !crate.isModelEngineEnabled()) continue;

            // 检查是否已有模型
            if (modelEngineManager.hasModel(loc)) continue;

            // 生成模型（使用保存的yaw朝向）
            modelEngineManager.spawnCrateModel(crate, loc, crateLocation.getYaw());
        }
    }

    public static FotiaCrates getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public CrateManager getCrateManager() {
        return crateManager;
    }

    public KeyManager getKeyManager() {
        return keyManager;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }

    public HistoryManager getHistoryManager() {
        return historyManager;
    }

    public PityManager getPityManager() {
        return pityManager;
    }

    public ModelEngineManager getModelEngineManager() {
        return modelEngineManager;
    }

    public PendingRewardManager getPendingRewardManager() {
        return pendingRewardManager;
    }

    public RewardItemDeliveryService getRewardItemDeliveryService() {
        return rewardItemDeliveryService;
    }

    public HologramManager getHologramManager() {
        return hologramManager;
    }

    public Economy getEconomy() {
        return economy;
    }

    public boolean hasEconomy() {
        return economy != null;
    }
}
