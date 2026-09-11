package gg.fotia.crates.reward;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Bukkit item serialization is performed only on the server thread. */
public final class RewardSnapshotCodec {
    private RewardSnapshotCodec() {
    }

    public static String serialize(Reward reward) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("id", reward.getId());
        config.set("display-name", reward.getDisplayName());
        config.set("rarity", reward.getRarity());
        config.set("chance", reward.getChance());
        config.set("broadcast", reward.shouldBroadcast());
        config.set("type", reward.getType().name());
        config.set("display-item", reward.getDisplayItem());
        config.set("commands", reward.getCommands());
        config.set("item", reward.getItem());
        config.set("extra-items", reward.getExtraItems());

        switch (reward.getType()) {
            case ITEM, COMMAND -> {
            }
            case MONEY -> {
                if (reward instanceof MoneyReward moneyReward) {
                    config.set("amount", moneyReward.getAmount());
                }
            }
            case EXPERIENCE -> {
                if (reward instanceof ExperienceReward experienceReward) {
                    config.set("amount", experienceReward.getAmount());
                    config.set("levels", experienceReward.isLevels());
                }
            }
        }

        return config.saveToString();
    }

    public static Reward deserialize(String rewardData, String defaultRarity) {
        if (rewardData == null || rewardData.isBlank()) {
            return null;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(new StringReader(rewardData));
        String typeName = config.getString("type");
        if (typeName == null || typeName.isBlank()) {
            return null;
        }

        RewardType rewardType;
        try {
            rewardType = RewardType.valueOf(typeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }

        String id = config.getString("id", "pending_reward");
        String displayName = config.getString("display-name", id);
        String rarity = config.getString("rarity", defaultRarity);
        double chance = config.getDouble("chance", 0D);
        boolean broadcast = config.getBoolean("broadcast", false);
        ItemStack displayItem = config.getItemStack("display-item");
        if (displayItem == null) {
            displayItem = new ItemStack(Material.PAPER);
        }

        ItemStack item = config.getItemStack("item");
        List<ItemStack> extraItems = new ArrayList<>();
        for (Object extraItem : config.getList("extra-items", List.of())) {
            if (extraItem instanceof ItemStack extraStack) {
                extraItems.add(extraStack);
            }
        }
        List<String> commands = config.getStringList("commands");

        return switch (rewardType) {
            case ITEM -> new ItemReward(id, displayName, rarity, chance, broadcast, displayItem,
                    item, extraItems, commands);
            case COMMAND -> new CommandReward(id, displayName, rarity, chance, broadcast, displayItem,
                    commands, item, extraItems);
            case MONEY -> new MoneyReward(id, displayName, rarity, chance, broadcast, displayItem,
                    config.getDouble("amount", 0D));
            case EXPERIENCE -> new ExperienceReward(id, displayName, rarity, chance, broadcast, displayItem,
                    config.getInt("amount", 0), config.getBoolean("levels", false));
        };
    }

}
