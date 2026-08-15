package com.bx.ultimateVirtualSpawner.managers;

import com.bx.ultimateVirtualSpawner.UltimateVirtualSpawner;
import com.bx.ultimateVirtualSpawner.menus.SpawnerMainMenu;
import com.bx.ultimateVirtualSpawner.menus.SpawnerPanelMenu;
import com.bx.ultimateVirtualSpawner.menus.SpawnerStorageMenu;
import com.bx.ultimateVirtualSpawner.menus.SpawnerWorldListMenu;
import com.bx.ultimateVirtualSpawner.models.SpawnerInstance;
import com.bx.ultimateVirtualSpawner.models.SpawnerLootEntry;
import com.bx.ultimateVirtualSpawner.models.SpawnerTypeDefinition;
import com.bx.ultimateVirtualSpawner.utils.ColorUtils;
import com.bx.ultimateVirtualSpawner.utils.ItemUtils;
import com.bx.ultimateVirtualSpawner.utils.NumberUtils;
import com.bx.ultimateVirtualSpawner.utils.PermissionUtils;
import com.bx.ultimateVirtualSpawner.utils.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.Hopper;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

public class SpawnerManager {

    public static final String ADMIN_PERMISSION = "ultimatevirtualspawner.admin.spawner";
    public static final String SILK_TOUCH_BYPASS_PERMISSION = "ultimatevirtualspawner.spawner.bypass";

    public record ActionResult(boolean success, String message, int consumedAmount, boolean fullyDestroyed) {
        public ActionResult(boolean success, String message, int consumedAmount) {
            this(success, message, consumedAmount, true);
        }

        public ActionResult(boolean success, String message) {
            this(success, message, 0, true);
        }
    }

    public record SellLootResult(boolean success, String message, double payout, long soldItems) {}

    public record SpawnerSellPreview(double totalPayout, long totalSellableItems, double multiplier) {}

    public record WorldSummary(String worldName, int count) {}

    private final UltimateVirtualSpawner plugin;
    private final NamespacedKey spawnerItemMarkerKey;
    private final NamespacedKey spawnerItemTypeKey;
    private final NamespacedKey spawnerItemAmountKey;
    private final NamespacedKey blockMarkerKey;
    private final NamespacedKey blockTypeKey;
    private final NamespacedKey blockAmountKey;
    private final NamespacedKey blockOwnerKey;
    private final NamespacedKey blockOwnerNameKey;
    private final NamespacedKey blockAccessKey;
    private final Object lock = new Object();
    private final Map<Long, SpawnerInstance> spawnersById = new LinkedHashMap<>();
    private final Map<String, Long> locationIndex = new HashMap<>();
    private final Map<String, LinkedHashSet<Long>> worldIndex = new HashMap<>();
    private final Map<String, LinkedHashSet<Long>> chunkIndex = new HashMap<>();
    private final Map<String, SpawnerTypeDefinition> typeDefinitions = new LinkedHashMap<>();
    private final AtomicLong temporarySpawnerIdSequence = new AtomicLong(-1L);
    private final Set<Long> temporarySpawnerIds = new HashSet<>();
    private final Map<Long, List<SpawnerLootEntry>> pendingLootMap = new ConcurrentHashMap<>();
    private final Map<UUID, SpawnerStorageMenu> openStorageMenus = new ConcurrentHashMap<>();

    private boolean enabled;
    private boolean xpEnabled;
    private SpawnerInstance.AccessMode defaultAccessMode;
    private long generationIntervalSeconds;
    private boolean processOnlyLoadedChunks;
    private boolean requirePlayerNearby;
    private double playerNearbyRadius;
    private long maxStackPerBlock;
    private long storageCapPerLootKey;
    private boolean dropOnBreakIfInventoryFull;
    private boolean allowSpawnerSteal;
    private boolean hopperExtractionEnabled;
    private int hopperExtractionAmountPerCycle;
    private boolean requireSilkTouch;
    private boolean cancelMobSpawn = true;
    private double defaultXpPerCycle;
    private String mainMenuTitle;
    private int mainMenuSize;
    private String storageTitle;
    private int storageSize;
    private int storageItemsPerPage;
    private String panelTitle;
    private int panelSize;
    private String worldListTitle;
    private int worldListSize;
    private String soundOpenMenu;
    private String soundCollectLoot;
    private String soundDropLoot;
    private String soundCollectXp;
    private String soundSellConfirmOpen;
    private String soundSellSuccess;
    private String soundSellCancel;
    private String soundFilterOpen;
    private String soundFilterToggle;

    public SpawnerManager(UltimateVirtualSpawner plugin) {
        this.plugin = plugin;
        this.spawnerItemMarkerKey = plugin.getKey("managed_spawner_item");
        this.spawnerItemTypeKey = plugin.getKey("managed_spawner_type");
        this.spawnerItemAmountKey = plugin.getKey("managed_spawner_amount");
        this.blockMarkerKey = plugin.getKey("managed_spawner_block");
        this.blockTypeKey = plugin.getKey("managed_spawner_block_type");
        this.blockAmountKey = plugin.getKey("managed_spawner_block_amount");
        this.blockOwnerKey = plugin.getKey("managed_spawner_block_owner");
        this.blockOwnerNameKey = plugin.getKey("managed_spawner_block_owner_name");
        this.blockAccessKey = plugin.getKey("managed_spawner_block_access");
        reload();
        loadPersistedSpawners();
    }


    public void reload() {
        FileConfiguration config = plugin.getConfigManager().getSpawners();
        enabled = config.getBoolean("SETTINGS.ENABLED", true);
        xpEnabled = config.getBoolean("SETTINGS.XP_ENABLED", true);
        defaultAccessMode = SpawnerInstance.AccessMode.fromString(
                config.getString("SETTINGS.ACCESS_MODE", "OWNER_ONLY"),
                SpawnerInstance.AccessMode.OWNER_ONLY
        );
        allowSpawnerSteal = config.getBoolean("SETTINGS.ALLOW_SPAWNER_STEAL", false);
        generationIntervalSeconds = Math.max(1L, config.getLong("SETTINGS.GENERATION_INTERVAL_SECONDS", 5L));
        processOnlyLoadedChunks = config.getBoolean("SETTINGS.PROCESS_ONLY_LOADED_CHUNKS", true);
        requirePlayerNearby = config.getBoolean("SETTINGS.REQUIRE_PLAYER_NEARBY", false);
        playerNearbyRadius = Math.max(1D, config.getDouble("SETTINGS.PLAYER_NEARBY_RADIUS", 16D));
        maxStackPerBlock = Math.max(1L, config.getLong("SETTINGS.MAX_STACK_PER_BLOCK", 100_000L));
        storageCapPerLootKey = Math.max(1L, config.getLong("SETTINGS.STORAGE_CAP_PER_LOOT_KEY", 1_000_000L));
        dropOnBreakIfInventoryFull = config.getBoolean("SETTINGS.DROP_ON_BREAK_IF_INVENTORY_FULL", true);
        requireSilkTouch = config.getBoolean("SETTINGS.REQUIRE_SILK_TOUCH", true);
        cancelMobSpawn = config.getBoolean("SETTINGS.CANCEL_MOB_SPAWN", true);
        defaultXpPerCycle = Math.max(0.0, config.getDouble("SETTINGS.XP_PER_CYCLE", 3.7));
        hopperExtractionEnabled = config.getBoolean("SETTINGS.HOPPER_EXTRACTION.ENABLED", false);
        hopperExtractionAmountPerCycle = Math.max(1, config.getInt("SETTINGS.HOPPER_EXTRACTION.AMOUNT_PER_CYCLE", 64));

        FileConfiguration menus = plugin.getConfigManager().getMenus();
        mainMenuTitle = menus.getString("SPAWNER-MENUS.MAIN-MENU.TITLE", "{stack} {mob}");
        mainMenuSize = normalizeSize(menus.getInt("SPAWNER-MENUS.MAIN-MENU.SIZE", 27));
        storageTitle = menus.getString("SPAWNER-MENUS.STORAGE-MENU.TITLE", "&8{mob} Spawners - {page}/{max_page}");
        storageSize = normalizeSize(menus.getInt("SPAWNER-MENUS.STORAGE-MENU.SIZE", 54));
        storageItemsPerPage = Math.max(9, Math.min(storageSize - 9,
                menus.getInt("SPAWNER-MENUS.STORAGE-MENU.ITEMS-PER-PAGE", 45)));
        panelTitle = menus.getString("SPAWNER-MENUS.PANEL-MENU.TITLE", "&8Spawners");
        panelSize = normalizeSize(menus.getInt("SPAWNER-MENUS.PANEL-MENU.SIZE", 54));
        worldListTitle = menus.getString("SPAWNER-MENUS.WORLD-LIST-MENU.TITLE", "&8Spawners Panel");
        worldListSize = normalizeSize(menus.getInt("SPAWNER-MENUS.WORLD-LIST-MENU.SIZE", 27));

        FileConfiguration sounds = plugin.getConfigManager().getSounds();
        soundOpenMenu = sounds.getString("SPAWNERS.OPEN-MENU", "minecraft:block.chest.open|1.0|1.0");
        soundCollectLoot = sounds.getString("SPAWNERS.COLLECT-LOOT", "minecraft:entity.item.pickup|1.0|1.0");
        soundDropLoot = sounds.getString("SPAWNERS.DROP-LOOT", "minecraft:entity.item.pickup|1.0|1.2");
        soundCollectXp = sounds.getString("SPAWNERS.COLLECT-XP", "minecraft:entity.experience_orb.pickup|1.0|1.0");
        soundSellConfirmOpen = sounds.getString("SPAWNERS.SELL-CONFIRM-OPEN", "minecraft:ui.button.click|1.0|1.0");
        soundSellSuccess = sounds.getString("SPAWNERS.SELL-SUCCESS", "minecraft:entity.villager.yes|1.0|1.0");
        soundSellCancel = sounds.getString("SPAWNERS.SELL-CANCEL", "minecraft:ui.button.click|1.0|0.8");
        soundFilterOpen = sounds.getString("SPAWNERS.FILTER-OPEN", "minecraft:ui.button.click|1.0|1.2");
        soundFilterToggle = sounds.getString("SPAWNERS.FILTER-TOGGLE", "minecraft:ui.button.click|1.0|1.0");

        loadTypeDefinitions(config);

        List<SpawnerInstance> copy;
        synchronized (lock) {
            copy = new ArrayList<>(spawnersById.values());
        }
        for (SpawnerInstance instance : copy) {
            syncSpawnerBlockState(instance);
        }
    }

    private void loadPersistedSpawners() {
        synchronized (lock) {
            spawnersById.clear();
            locationIndex.clear();
            worldIndex.clear();
            chunkIndex.clear();
            temporarySpawnerIds.clear();
        }

        Map<Long, List<SpawnerLootEntry>> lootBySpawnerId = plugin.getDatabaseManager().loadAllSpawnerLoot();
        int loaded = 0;
        for (SpawnerInstance instance : plugin.getDatabaseManager().loadAllSpawners()) {
            instance.setStoredLootEntries(lootBySpawnerId.get(instance.getId()));
            registerSpawner(instance);
            syncSpawnerBlockState(instance);
            loaded++;
        }

        plugin.getLogger().info("[SpawnerManager] Loaded " + loaded + " managed spawner(s) from the database.");
    }

    private void loadTypeDefinitions(FileConfiguration config) {
        typeDefinitions.clear();

        ConfigurationSection typesSection = config.getConfigurationSection("TYPES");
        if (typesSection == null) {
            plugin.getLogger().warning("[SpawnerManager] No TYPES section found in spawners.yml.");
            return;
        }

        for (String rawKey : typesSection.getKeys(false)) {
            ConfigurationSection section = typesSection.getConfigurationSection(rawKey);
            if (section == null || !section.getBoolean("ENABLED", true)) {
                continue;
            }

            String key = rawKey.trim().toUpperCase(Locale.US);
            EntityType entityType;
            try {
                entityType = EntityType.valueOf(section.getString("ENTITY_TYPE", key).trim().toUpperCase(Locale.US));
            } catch (Exception exception) {
                plugin.getLogger().warning("[SpawnerManager] Invalid ENTITY_TYPE for spawner type " + key + ".");
                continue;
            }

            Material iconMaterial = ItemUtils.parseMaterial(section.getString("ICON_MATERIAL", "SPAWNER"));
            long baseItemsPerCycle = Math.max(1L, section.getLong("BASE_ITEMS_PER_CYCLE", 1L));
            double xpPerCycle = Math.max(0.0, section.getDouble("XP_PER_CYCLE", defaultXpPerCycle));

            List<SpawnerTypeDefinition.DropDefinition> drops = new ArrayList<>();
            ConfigurationSection dropsSection = section.getConfigurationSection("DROPS");
            if (dropsSection != null) {
                for (String dropKey : dropsSection.getKeys(false)) {
                    ConfigurationSection dropSection = dropsSection.getConfigurationSection(dropKey);
                    if (dropSection == null || !dropSection.getBoolean("ENABLED", true)) {
                        continue;
                    }

                    Material material = ItemUtils.parseMaterial(dropSection.getString("MATERIAL", "STONE"));
                    long min = Math.max(0L, dropSection.getLong("MIN", 0L));
                    long max = Math.max(min, dropSection.getLong("MAX", min));
                    double chance = Math.max(0D, Math.min(1D, dropSection.getDouble("CHANCE", 1D)));
                    drops.add(new SpawnerTypeDefinition.DropDefinition(
                            dropKey.toUpperCase(Locale.US), material, min, max, chance));
                }
            }

            String headTexture = section.getString("HEAD_TEXTURE", null);
            typeDefinitions.put(key, new SpawnerTypeDefinition(
                    key,
                    section.getString("DISPLAY_NAME", "&d" + prettifyKey(key) + " Spawner"),
                    entityType,
                    iconMaterial,
                    baseItemsPerCycle,
                    xpPerCycle,
                    headTexture,
                    drops
            ));
        }
    }


    public boolean isEnabled() {
        return enabled;
    }

    public boolean isCancelMobSpawn() {
        return cancelMobSpawn;
    }

    public boolean isRequireSilkTouch() {
        return requireSilkTouch;
    }

    public boolean isXpEnabled() {
        return xpEnabled;
    }

    public long getStorageCapPerLootKey() {
        return storageCapPerLootKey;
    }

    public long getMaxStackPerBlock() {
        return maxStackPerBlock;
    }

    public boolean hasSilkTouchAccess(Player player) {
        if (player == null) {
            return false;
        }
        if (player.getGameMode() == GameMode.CREATIVE || PermissionUtils.has(player, SILK_TOUCH_BYPASS_PERMISSION)) {
            return true;
        }
        ItemStack heldTool = player.getInventory().getItemInMainHand();
        return heldTool != null
                && heldTool.getType().name().endsWith("_PICKAXE")
                && heldTool.containsEnchantment(org.bukkit.enchantments.Enchantment.SILK_TOUCH);
    }


    public ItemStack createSpawnerItem(String typeKey, long amount) {
        SpawnerTypeDefinition definition = getTypeDefinition(typeKey);
        if (definition == null || amount <= 0L) {
            return null;
        }

        ItemStack item = new ItemStack(Material.SPAWNER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName(ColorUtils.toComponent(definition.displayName()));
        meta.setLore(buildSpawnerItemLore(definition));

        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(spawnerItemMarkerKey, PersistentDataType.BYTE, (byte) 1);
        container.set(spawnerItemTypeKey, PersistentDataType.STRING, definition.key());

        if (amount <= 64) {
            container.set(spawnerItemAmountKey, PersistentDataType.LONG, 1L);
            item.setItemMeta(meta);
            item.setAmount((int) amount);
        } else {
            container.set(spawnerItemAmountKey, PersistentDataType.LONG, amount);
            item.setItemMeta(meta);
            item.setAmount(1);
        }
        return item;
    }

    private List<String> buildSpawnerItemLore(SpawnerTypeDefinition definition) {
        List<String> lore = plugin.getConfigManager().getMessages().getStringList("ITEM.SPAWNER_LORE");
        if (lore.isEmpty()) {
            lore = List.of("&7Type: &f{type}", "", "&ePlace to create or stack this spawner.");
        }

        List<String> resolved = new ArrayList<>(lore.size());
        for (String line : lore) {
            resolved.add(line.replace("{type}", ColorUtils.strip(definition.displayName())));
        }
        return ColorUtils.toComponentList(resolved);
    }

    public void updateSpawnerItemAmount(ItemStack item, long newAmount) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        SpawnerTypeDefinition definition = getTypeDefinition(getSpawnerItemType(item));
        if (definition == null) {
            return;
        }

        meta.setLore(buildSpawnerItemLore(definition));
        if (newAmount <= 64) {
            meta.getPersistentDataContainer().set(spawnerItemAmountKey, PersistentDataType.LONG, 1L);
            item.setItemMeta(meta);
            item.setAmount((int) newAmount);
        } else {
            meta.getPersistentDataContainer().set(spawnerItemAmountKey, PersistentDataType.LONG, newAmount);
            item.setItemMeta(meta);
            item.setAmount(1);
        }
    }

    public boolean isSpawnerItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();
        Byte marker = container.get(spawnerItemMarkerKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1
                && container.has(spawnerItemTypeKey, PersistentDataType.STRING)
                && container.has(spawnerItemAmountKey, PersistentDataType.LONG);
    }

    public String getSpawnerItemType(ItemStack item) {
        if (!isSpawnerItem(item)) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(spawnerItemTypeKey, PersistentDataType.STRING);
    }

    public long getSpawnerItemBaseAmount(ItemStack item) {
        if (!isSpawnerItem(item)) {
            return 0L;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 1L;
        }
        Long amount = meta.getPersistentDataContainer().get(spawnerItemAmountKey, PersistentDataType.LONG);
        return amount == null ? 1L : Math.max(1L, amount);
    }

    public long getSpawnerItemAmount(ItemStack item) {
        if (!isSpawnerItem(item)) {
            return 0L;
        }
        return getSpawnerItemBaseAmount(item) * item.getAmount();
    }


    public SpawnerTypeDefinition getTypeDefinition(String typeKey) {
        if (typeKey == null || typeKey.isBlank()) {
            return null;
        }
        return typeDefinitions.get(typeKey.trim().toUpperCase(Locale.US));
    }

    public Collection<SpawnerTypeDefinition> getTypeDefinitions() {
        return List.copyOf(typeDefinitions.values());
    }

    public Set<String> getTypeKeys() {
        return new LinkedHashSet<>(typeDefinitions.keySet());
    }

    public String getTypeDisplayName(String typeKey) {
        SpawnerTypeDefinition definition = getTypeDefinition(typeKey);
        return definition == null ? "&dSpawner" : definition.displayName();
    }

    public String getPlainTypeDisplayName(String typeKey) {
        return ColorUtils.strip(getTypeDisplayName(typeKey));
    }

    public Material getTypeIcon(String typeKey) {
        SpawnerTypeDefinition definition = getTypeDefinition(typeKey);
        return definition == null || definition.iconMaterial() == null ? Material.SPAWNER : definition.iconMaterial();
    }


    public ActionResult giveSpawner(Player target, String typeKey, long amount) {
        if (!enabled) {
            return fail("SPAWNER.SYSTEM_DISABLED");
        }
        if (target == null) {
            return fail("SPAWNER.TARGET_MUST_BE_ONLINE");
        }
        if (amount <= 0L) {
            return fail("SPAWNER.AMOUNT_MUST_BE_POSITIVE");
        }

        SpawnerTypeDefinition definition = getTypeDefinition(typeKey);
        if (definition == null) {
            return fail("SPAWNER.UNKNOWN_TYPE", "type", typeKey);
        }

        long remaining = amount;
        while (remaining > 0) {
            int stackSize = (int) Math.min(64, remaining);
            ItemStack item = createSpawnerItem(definition.key(), stackSize);
            if (item == null) {
                return fail("SPAWNER.ITEM_CREATE_FAILED");
            }
            Map<Integer, ItemStack> leftovers = target.getInventory().addItem(item);
            leftovers.values().forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
            remaining -= stackSize;
        }

        return ok("SPAWNER.GIVE_SUCCESS",
                "amount", NumberUtils.format(amount),
                "type", ColorUtils.strip(definition.displayName()),
                "player", target.getName());
    }

    public ActionResult placeSpawner(Player player, Block block, ItemStack item) {
        if (!enabled) {
            return fail("SPAWNER.SYSTEM_DISABLED");
        }
        if (player == null || block == null || !isSpawnerItem(item)) {
            return fail("SPAWNER.NOT_MANAGED_ITEM");
        }

        String typeKey = getSpawnerItemType(item);
        long baseAmount = getSpawnerItemBaseAmount(item);

        boolean stackAll = player.isSneaking();
        int quantity = stackAll ? item.getAmount() : 1;
        long amount = baseAmount * quantity;

        if (amount <= 0L) {
            return fail("SPAWNER.INVALID_ITEM_AMOUNT");
        }
        if (amount > maxStackPerBlock) {
            return fail("SPAWNER.EXCEEDS_MAX_STACK", "max", NumberUtils.format(maxStackPerBlock));
        }
        if (getSpawner(block) != null) {
            return fail("SPAWNER.ALREADY_MANAGED_BLOCK");
        }

        SpawnerTypeDefinition definition = getTypeDefinition(typeKey);
        if (definition == null) {
            return fail("SPAWNER.UNKNOWN_TYPE", "type", typeKey);
        }

        long now = System.currentTimeMillis();
        SpawnerInstance instance = new SpawnerInstance(
                0L,
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ(),
                player.getUniqueId(),
                player.getName(),
                definition.key(),
                amount,
                defaultAccessMode,
                now,
                now,
                now
        );

        long id = plugin.getDatabaseManager().createSpawner(instance);
        if (id <= 0L) {
            return fail("SPAWNER.SAVE_FAILED");
        }

        instance.setId(id);
        registerSpawner(instance);

        plugin.getSpigotScheduler().runRegion(block.getLocation(), () -> {
            syncSpawnerBlockStateImmediate(instance);
            refreshAntiEspNearby(block.getLocation());
        });

        return new ActionResult(true, message("SPAWNER.PLACE_SUCCESS",
                "amount", NumberUtils.format(instance.getStackAmount()),
                "type", ColorUtils.strip(definition.displayName())), quantity);
    }

    public ActionResult createTemporarySpawner(
            Player owner,
            Block block,
            String typeKey,
            long amount,
            SpawnerInstance.AccessMode accessMode
    ) {
        if (!enabled) {
            return fail("SPAWNER.SYSTEM_DISABLED");
        }
        if (owner == null || block == null) {
            return fail("SPAWNER.NOT_MANAGED_BLOCK");
        }
        if (getSpawner(block) != null) {
            return fail("SPAWNER.ALREADY_MANAGED_BLOCK");
        }

        SpawnerTypeDefinition definition = getTypeDefinition(typeKey);
        if (definition == null) {
            return fail("SPAWNER.UNKNOWN_TYPE", "type", typeKey);
        }

        long now = System.currentTimeMillis();
        SpawnerInstance instance = new SpawnerInstance(
                temporarySpawnerIdSequence.getAndDecrement(),
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ(),
                owner.getUniqueId(),
                owner.getName(),
                definition.key(),
                Math.max(1L, amount),
                accessMode == null ? SpawnerInstance.AccessMode.PUBLIC : accessMode,
                now,
                now,
                now
        );

        registerSpawner(instance);
        synchronized (lock) {
            temporarySpawnerIds.add(instance.getId());
        }
        syncSpawnerBlockStateImmediate(instance);
        refreshAntiEspNearby(block.getLocation());
        return ok("SPAWNER.TEMPORARY_REGISTERED");
    }

    public boolean isTemporarySpawner(SpawnerInstance instance) {
        if (instance == null) {
            return false;
        }
        synchronized (lock) {
            return temporarySpawnerIds.contains(instance.getId());
        }
    }

    public boolean removeTemporarySpawner(Block block) {
        SpawnerInstance instance = getSpawner(block);
        if (!isTemporarySpawner(instance)) {
            return false;
        }

        unregisterSpawner(instance);
        synchronized (lock) {
            temporarySpawnerIds.remove(instance.getId());
        }
        if (block != null) {
            refreshAntiEspNearby(block.getLocation());
        }
        return true;
    }

    public ActionResult stackSpawner(Player player, Block block, ItemStack item) {
        if (!enabled) {
            return fail("SPAWNER.SYSTEM_DISABLED");
        }

        SpawnerInstance existing = getSpawner(block);
        if (existing == null) {
            return fail("SPAWNER.NOT_MANAGED_BLOCK");
        }
        if (isTemporarySpawner(existing)) {
            return fail("SPAWNER.TEMPORARY_CANNOT_STACK");
        }
        if (!isSpawnerItem(item)) {
            return fail("SPAWNER.HOLD_SPAWNER_ITEM");
        }
        if (!canModify(player, existing)) {
            return fail("SPAWNER.NOT_OWNER");
        }

        String typeKey = Objects.requireNonNullElse(getSpawnerItemType(item), "");
        if (!existing.getMobTypeKey().equalsIgnoreCase(typeKey)) {
            return fail("SPAWNER.TYPE_MISMATCH");
        }

        long baseAmount = getSpawnerItemBaseAmount(item);
        if (baseAmount <= 0L) {
            return fail("SPAWNER.INVALID_ITEM_AMOUNT");
        }

        long remainingCapacity = maxStackPerBlock - existing.getStackAmount();
        if (remainingCapacity <= 0L) {
            return fail("SPAWNER.MAX_STACK_REACHED", "max", NumberUtils.format(maxStackPerBlock));
        }

        boolean stackAll = player.isSneaking();
        long maxUnitsCanAdd = Math.max(1L, remainingCapacity / baseAmount);
        int quantity = stackAll ? (int) Math.min(item.getAmount(), maxUnitsCanAdd) : 1;
        long addAmount = baseAmount * quantity;

        if (addAmount <= 0L) {
            return fail("SPAWNER.INVALID_ITEM_AMOUNT");
        }

        long targetAmount = existing.getStackAmount() + addAmount;
        if (targetAmount > maxStackPerBlock) {
            return fail("SPAWNER.EXCEEDS_MAX_STACK", "max", NumberUtils.format(maxStackPerBlock));
        }

        existing.setStackAmount(targetAmount);
        existing.setUpdatedAt(System.currentTimeMillis());
        saveSpawnerAsync(existing);
        plugin.getSpigotScheduler().runRegion(block.getLocation(), () -> {
            syncSpawnerBlockStateImmediate(existing);
            refreshAntiEspNearby(block.getLocation());
        });

        return new ActionResult(true, message("SPAWNER.STACK_UPDATED",
                "amount", NumberUtils.format(existing.getStackAmount())), quantity);
    }

    public ActionResult breakSpawner(Player player, Block block) {
        SpawnerInstance instance = getSpawner(block);
        if (isTemporarySpawner(instance)) {
            unregisterSpawner(instance);
            synchronized (lock) {
                temporarySpawnerIds.remove(instance.getId());
            }
            plugin.getSpigotScheduler().runRegion(block.getLocation(), () -> refreshAntiEspNearby(block.getLocation()));
            return new ActionResult(true, message("SPAWNER.TEMPORARY_REMOVED"), 0, true);
        }
        if (instance == null) {
            return fail("SPAWNER.NOT_MANAGED_BLOCK");
        }
        if (!canBreak(player, instance)) {
            return fail("SPAWNER.BREAK_NO_PERMISSION");
        }
        if (requireSilkTouch && !hasSilkTouchAccess(player)) {
            return fail("SPAWNER.SILK_TOUCH_REQUIRED");
        }

        long totalStack = instance.getStackAmount();
        boolean stackAll = player != null && player.isSneaking();
        long breakAmount = stackAll ? Math.min(64L, totalStack) : 1L;
        long remainingStack = totalStack - breakAmount;

        boolean fullyDestroyed;
        if (remainingStack <= 0L) {
            unregisterSpawner(instance);
            deleteSpawnerAsync(instance.getId());
            fullyDestroyed = true;
        } else {
            instance.setStackAmount(remainingStack);
            instance.setUpdatedAt(System.currentTimeMillis());
            saveSpawnerAsync(instance);
            plugin.getSpigotScheduler().runRegion(block.getLocation(), () -> {
                syncSpawnerBlockStateImmediate(instance);
                refreshAntiEspNearby(block.getLocation());
            });
            fullyDestroyed = false;
        }

        if (player != null) {
            long remaining = breakAmount;
            List<ItemStack> itemsToGive = new ArrayList<>();
            while (remaining > 0) {
                int amount = (int) Math.min(64, remaining);
                ItemStack item = createSpawnerItem(instance.getMobTypeKey(), amount);
                if (item != null) {
                    itemsToGive.add(item);
                }
                remaining -= amount;
            }

            PlayerInventory inventory = player.getInventory();
            for (ItemStack item : itemsToGive) {
                Map<Integer, ItemStack> leftovers = inventory.addItem(item);
                if (dropOnBreakIfInventoryFull) {
                    leftovers.values()
                            .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
                } else if (!leftovers.isEmpty()) {
                    leftovers.values().forEach(inventory::addItem);
                }
            }
        }

        plugin.getSpigotScheduler().runRegion(block.getLocation(), () -> refreshAntiEspNearby(block.getLocation()));

        return new ActionResult(true, message("SPAWNER.BREAK_SUCCESS",
                "amount", NumberUtils.format(breakAmount),
                "type", ColorUtils.strip(getTypeDisplayName(instance.getMobTypeKey()))),
                (int) breakAmount, fullyDestroyed);
    }

    public ActionResult removeSpawner(SpawnerInstance instance, boolean dropItem, Player actor) {
        if (instance == null) {
            return fail("SPAWNER.NOT_FOUND");
        }
        if (isTemporarySpawner(instance)) {
            synchronized (lock) {
                temporarySpawnerIds.remove(instance.getId());
            }
            unregisterSpawner(instance);
            return ok("SPAWNER.TEMPORARY_REMOVED");
        }

        unregisterSpawner(instance);
        deleteSpawnerAsync(instance.getId());
        releaseSpawnerBlock(instance);

        World world = Bukkit.getWorld(instance.getWorld());
        if (world != null && dropItem) {
            long remaining = instance.getStackAmount();
            while (remaining > 0) {
                int amount = (int) Math.min(64, remaining);
                ItemStack item = createSpawnerItem(instance.getMobTypeKey(), amount);
                if (item != null) {
                    world.dropItemNaturally(getSpawnerCenter(instance), item);
                }
                remaining -= amount;
            }
        }

        if (world != null && actor != null && actor.getLocation().getWorld() == world) {
            plugin.getSpigotScheduler().runRegion(getSpawnerCenter(instance),
                    () -> refreshAntiEspNearby(getSpawnerCenter(instance)));
        }
        return ok("SPAWNER.REMOVED");
    }


    public SpawnerInstance getSpawner(Block block) {
        if (block == null) {
            return null;
        }
        return getSpawner(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    public SpawnerInstance getSpawner(String world, int x, int y, int z) {
        synchronized (lock) {
            Long id = locationIndex.get(SpawnerInstance.buildLocationKey(world, x, y, z));
            return id == null ? null : spawnersById.get(id);
        }
    }

    public SpawnerInstance getSpawner(long spawnerId) {
        synchronized (lock) {
            return spawnersById.get(spawnerId);
        }
    }

    public Collection<SpawnerInstance> getAllSpawners() {
        synchronized (lock) {
            return List.copyOf(spawnersById.values());
        }
    }

    public int getTotalSpawnerCount() {
        synchronized (lock) {
            return spawnersById.size();
        }
    }

    public List<SpawnerInstance> getSpawnersOwnedBy(UUID ownerUuid) {
        if (ownerUuid == null) {
            return List.of();
        }
        List<SpawnerInstance> owned = new ArrayList<>();
        synchronized (lock) {
            for (SpawnerInstance instance : spawnersById.values()) {
                if (ownerUuid.equals(instance.getOwnerUuid())) {
                    owned.add(instance);
                }
            }
        }
        return owned;
    }

    public List<SpawnerInstance> getSpawnersInWorld(String worldName) {
        synchronized (lock) {
            LinkedHashSet<Long> ids = worldIndex.get(worldName == null ? "" : worldName.toLowerCase(Locale.US));
            if (ids == null || ids.isEmpty()) {
                return List.of();
            }

            List<SpawnerInstance> spawners = new ArrayList<>();
            for (Long id : ids) {
                SpawnerInstance instance = spawnersById.get(id);
                if (instance != null) {
                    spawners.add(instance);
                }
            }
            spawners.sort(Comparator.comparingLong(SpawnerInstance::getStackAmount).reversed()
                    .thenComparingInt(SpawnerInstance::getX));
            return spawners;
        }
    }

    public boolean isNearManagedSpawner(Location loc, double maxDistance) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        double maxDistSq = maxDistance * maxDistance;
        for (SpawnerInstance instance : getSpawnersInWorld(loc.getWorld().getName())) {
            double dx = instance.getX() + 0.5D - loc.getX();
            double dy = instance.getY() + 0.5D - loc.getY();
            double dz = instance.getZ() + 0.5D - loc.getZ();
            if ((dx * dx + dy * dy + dz * dz) <= maxDistSq) {
                return true;
            }
        }
        return false;
    }

    public List<WorldSummary> getWorldSummaries() {
        List<WorldSummary> summaries = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            summaries.add(new WorldSummary(world.getName(), getSpawnersInWorld(world.getName()).size()));
        }
        summaries.sort(Comparator
                .comparingInt((WorldSummary summary) -> getWorldSortIndex(summary.worldName()))
                .thenComparing(summary -> describeWorld(summary.worldName()), String.CASE_INSENSITIVE_ORDER));
        return summaries;
    }


    public void playSound(Player player, String soundConfig) {
        if (player != null && soundConfig != null && !soundConfig.isBlank()) {
            SoundUtils.play(player, soundConfig);
        }
    }

    public void playOpenMenuSound(Player player) { playSound(player, soundOpenMenu); }

    public void playCollectLootSound(Player player) { playSound(player, soundCollectLoot); }

    public void playDropLootSound(Player player) { playSound(player, soundDropLoot); }

    public void playCollectXpSound(Player player) { playSound(player, soundCollectXp); }

    public void playSellConfirmOpenSound(Player player) { playSound(player, soundSellConfirmOpen); }

    public void playSellSuccessSound(Player player) { playSound(player, soundSellSuccess); }

    public void playSellCancelSound(Player player) { playSound(player, soundSellCancel); }

    public void playFilterOpenSound(Player player) { playSound(player, soundFilterOpen); }

    public void playFilterToggleSound(Player player) { playSound(player, soundFilterToggle); }


    public void openStorage(Player player, SpawnerInstance instance, int page) {
        if (player == null || instance == null) {
            return;
        }
        playOpenMenuSound(player);
        new SpawnerStorageMenu(plugin, instance.getId(), page).open(player);
    }

    public void openMainMenu(Player player, SpawnerInstance instance) {
        if (player == null || instance == null) {
            return;
        }
        playOpenMenuSound(player);
        new SpawnerMainMenu(plugin, instance.getId()).open(player);
    }

    public void openPanel(Player player) {
        if (player == null) {
            return;
        }
        new SpawnerWorldListMenu(plugin).open(player);
    }

    public void openWorldPanel(Player player, String worldName, int page) {
        if (player == null) {
            return;
        }
        new SpawnerPanelMenu(plugin, worldName, page).open(player);
    }

    public String getMainMenuTitle(SpawnerInstance instance) {
        if (instance == null) {
            return "&8Spawner";
        }
        String title = mainMenuTitle
                .replace("{mob}", prettifyKey(instance.getMobTypeKey()))
                .replace("{stack}", String.valueOf(instance.getStackAmount()));

        if (!ColorUtils.strip(title).toLowerCase(Locale.US).endsWith("spawner")) {
            title = title + " Spawner";
        }
        return title.replaceAll("(?i)\\bspawners?\\s+spawners?\\b", "Spawner");
    }

    public int getMainMenuSize() {
        return mainMenuSize;
    }

    public String getStorageTitle(SpawnerInstance instance, int page, int maxPage) {
        if (instance == null) {
            return "&8Spawner Storage";
        }
        String title = storageTitle
                .replace("{mob}", prettifyKey(instance.getMobTypeKey()))
                .replace("{page}", String.valueOf(page))
                .replace("{max_page}", String.valueOf(maxPage));

        title = title.replaceAll("(?i)\\bspawners?\\s+spawners?\\b", "Spawners");
        return title.length() > 32 ? title.substring(0, 32) : title;
    }

    public int getStorageSize() {
        return storageSize;
    }

    public int getStorageItemsPerPage() {
        return storageItemsPerPage;
    }

    public String getPanelTitle(String worldName) {
        String title = panelTitle.replace("{world}", describeWorld(worldName));
        return title.length() > 32 ? title.substring(0, 32) : title;
    }

    public int getPanelSize() {
        return panelSize;
    }

    public String getWorldListTitle() {
        return worldListTitle;
    }

    public int getWorldListSize() {
        return worldListSize;
    }


    public List<SpawnerLootEntry> getSortedLootEntries(SpawnerInstance instance) {
        if (instance == null) {
            return List.of();
        }

        List<SpawnerLootEntry> entries = new ArrayList<>();
        for (SpawnerLootEntry entry : instance.getStoredLootEntries()) {
            if (entry != null && entry.getAmount() > 0L) {
                entries.add(entry);
            }
        }

        entries.sort((a, b) -> {
            int cmp = Long.compare(b.getAmount(), a.getAmount());
            if (cmp != 0) {
                return cmp;
            }
            boolean aDisabled = instance.isLootDisabled(a.getKey());
            boolean bDisabled = instance.isLootDisabled(b.getKey());
            if (aDisabled != bDisabled) {
                return aDisabled ? 1 : -1;
            }
            return a.getMaterial().name().compareTo(b.getMaterial().name());
        });
        return entries;
    }

    public ActionResult collectLootEntry(Player player, SpawnerInstance instance, String lootKey, boolean collectAll) {
        if (player == null || instance == null) {
            return fail("SPAWNER.NOT_FOUND");
        }
        if (!canOpen(player, instance)) {
            return fail("SPAWNER.NO_ACCESS");
        }

        SpawnerLootEntry entry = instance.getStoredLoot(lootKey);
        if (entry == null || entry.getAmount() <= 0L) {
            return fail("SPAWNER.LOOT_EMPTY");
        }

        long requested = collectAll
                ? entry.getAmount()
                : Math.min(entry.getAmount(), entry.getMaterial().getMaxStackSize());
        long moved = moveMaterialToInventory(player.getInventory(), entry.getMaterial(), requested);
        if (moved <= 0L) {
            return fail("SPAWNER.INVENTORY_FULL");
        }

        instance.removeStoredLoot(entry.getKey(), moved);
        instance.setUpdatedAt(System.currentTimeMillis());
        saveLoot(instance);
        playCollectLootSound(player);

        return ok("SPAWNER.COLLECT_SUCCESS",
                "amount", NumberUtils.format(moved),
                "material", plugin.getWorthManager().prettifyMaterial(entry.getMaterial()));
    }

    public ActionResult collectAllLoot(Player player, SpawnerInstance instance) {
        if (player == null || instance == null) {
            return fail("SPAWNER.NOT_FOUND");
        }
        if (!canOpen(player, instance)) {
            return fail("SPAWNER.NO_ACCESS");
        }

        long totalMoved = 0L;
        for (SpawnerLootEntry entry : new ArrayList<>(instance.getStoredLootEntries())) {
            if (instance.isLootDisabled(entry.getKey())) {
                continue;
            }
            long moved = moveMaterialToInventory(player.getInventory(), entry.getMaterial(), entry.getAmount());
            if (moved <= 0L) {
                continue;
            }
            instance.removeStoredLoot(entry.getKey(), moved);
            totalMoved += moved;
        }

        if (totalMoved <= 0L) {
            return fail("SPAWNER.COLLECT_ALL_NO_SPACE");
        }

        instance.setUpdatedAt(System.currentTimeMillis());
        saveLoot(instance);
        playCollectLootSound(player);
        return ok("SPAWNER.COLLECT_ALL_SUCCESS", "amount", NumberUtils.format(totalMoved));
    }

    public ActionResult dropAllLoot(Player player, SpawnerInstance instance) {
        if (player == null || instance == null) {
            return fail("SPAWNER.NOT_FOUND");
        }
        if (!canOpen(player, instance)) {
            return fail("SPAWNER.NO_ACCESS");
        }

        Location dropLocation = getSpawnerCenter(instance).add(0, 0.5D, 0);
        long dropped = 0L;
        for (SpawnerLootEntry entry : new ArrayList<>(instance.getStoredLootEntries())) {
            if (instance.isLootDisabled(entry.getKey())) {
                continue;
            }
            long droppedForEntry = dropMaterial(player, dropLocation, entry.getMaterial(), entry.getAmount());
            if (droppedForEntry > 0L) {
                instance.removeStoredLoot(entry.getKey(), droppedForEntry);
                dropped += droppedForEntry;
            }
        }

        if (dropped <= 0L) {
            return fail("SPAWNER.DROP_NO_LOOT");
        }

        instance.setUpdatedAt(System.currentTimeMillis());
        saveLoot(instance);
        playDropLootSound(player);
        return ok("SPAWNER.DROP_SUCCESS", "amount", NumberUtils.format(dropped));
    }

    public SpawnerSellPreview calculateLootSellPreview(Player player, SpawnerInstance instance) {
        if (player == null || instance == null) {
            return new SpawnerSellPreview(0D, 0L, 1.0D);
        }

        WorthManager worth = plugin.getWorthManager();
        double multiplier = worth.getSellMultiplier(player);
        double totalPayout = 0D;
        long sellableItems = 0L;

        for (SpawnerLootEntry entry : new ArrayList<>(instance.getStoredLootEntries())) {
            if (instance.isLootDisabled(entry.getKey()) || entry.getAmount() <= 0L) {
                continue;
            }
            WorthManager.WorthResult result = worth.resolveWorth(entry.getMaterial());
            if (!result.sellable()) {
                continue;
            }
            totalPayout += result.unitWorth() * entry.getAmount() * multiplier;
            sellableItems += entry.getAmount();
        }

        return new SpawnerSellPreview(totalPayout, sellableItems, multiplier);
    }

    public SellLootResult sellAllLoot(Player player, SpawnerInstance instance) {
        if (player == null || instance == null) {
            return failSell("SPAWNER.NOT_FOUND");
        }
        if (!canOpen(player, instance)) {
            return failSell("SPAWNER.NO_ACCESS");
        }
        if (!plugin.getWorthManager().isSellEnabled()) {
            return failSell("SPAWNER.SELL_DISABLED");
        }

        WorthManager worth = plugin.getWorthManager();
        double multiplier = worth.getSellMultiplier(player);
        double totalPayout = 0D;
        long soldItems = 0L;

        Map<String, Long> toRemove = new LinkedHashMap<>();
        for (SpawnerLootEntry entry : new ArrayList<>(instance.getStoredLootEntries())) {
            if (instance.isLootDisabled(entry.getKey()) || entry.getAmount() <= 0L) {
                continue;
            }
            WorthManager.WorthResult result = worth.resolveWorth(entry.getMaterial());
            if (!result.sellable()) {
                continue;
            }
            totalPayout += result.unitWorth() * entry.getAmount() * multiplier;
            soldItems += entry.getAmount();
            toRemove.put(entry.getKey(), entry.getAmount());
        }

        if (totalPayout <= 0D || soldItems <= 0L) {
            return failSell("SPAWNER.SELL_NO_ITEMS");
        }

        EconomyManager.DepositResult deposit = plugin.getEconomyManager().deposit(player, totalPayout);
        if (!deposit.success()) {
            return failSell("SPAWNER.SELL_PAYOUT_FAILED");
        }

        for (Map.Entry<String, Long> removal : toRemove.entrySet()) {
            instance.removeStoredLoot(removal.getKey(), removal.getValue());
        }

        instance.setUpdatedAt(System.currentTimeMillis());
        saveLoot(instance);
        playSellSuccessSound(player);

        return new SellLootResult(true, message("SPAWNER.SELL_SUCCESS",
                "amount", NumberUtils.format(soldItems),
                "money", plugin.getEconomyManager().formatMoneyCompact(totalPayout)),
                totalPayout, soldItems);
    }

    public ActionResult collectXp(Player player, SpawnerInstance instance) {
        if (!xpEnabled) {
            return fail("SPAWNER.XP_DISABLED");
        }
        if (player == null || instance == null) {
            return fail("SPAWNER.NOT_FOUND");
        }
        if (!canOpen(player, instance)) {
            return fail("SPAWNER.NO_ACCESS");
        }

        double xp = instance.getStoredXp();
        if (xp <= 0.0) {
            return fail("SPAWNER.XP_EMPTY");
        }

        instance.setStoredXp(0.0);
        instance.setUpdatedAt(System.currentTimeMillis());
        if (!isTemporarySpawner(instance)) {
            saveSpawnerAsync(instance);
        }

        player.giveExp((int) Math.round(xp));
        playCollectXpSound(player);
        return ok("SPAWNER.XP_COLLECTED", "xp", String.format(Locale.US, "%.1f", xp));
    }

    public ActionResult sellAndCollectXp(Player player, SpawnerInstance instance) {
        if (player == null || instance == null) {
            return fail("SPAWNER.NOT_FOUND");
        }
        if (!canOpen(player, instance)) {
            return fail("SPAWNER.NO_ACCESS");
        }

        SellLootResult sellResult = sellAllLoot(player, instance);
        ActionResult xpResult = xpEnabled ? collectXp(player, instance) : new ActionResult(false, "");

        if (!sellResult.success() && !xpResult.success()) {
            return fail("SPAWNER.SELL_NO_ITEMS");
        }

        StringBuilder combined = new StringBuilder();
        if (sellResult.success()) {
            combined.append(sellResult.message());
        }
        if (xpResult.success()) {
            if (!combined.isEmpty()) {
                combined.append(' ');
            }
            combined.append(xpResult.message());
        }
        return new ActionResult(true, combined.toString());
    }


    public void processGeneration() {
        if (!enabled) {
            return;
        }

        List<SpawnerInstance> copy;
        synchronized (lock) {
            if (spawnersById.isEmpty()) {
                return;
            }
            copy = new ArrayList<>(spawnersById.values());
        }

        long now = System.currentTimeMillis();
        long intervalMillis = generationIntervalSeconds * 1000L;
        if (intervalMillis <= 0L) {
            return;
        }

        for (SpawnerInstance instance : copy) {
            try {
                Location loc = getSpawnerCenter(instance);
                plugin.getSpigotScheduler().runRegion(loc, () -> {
                    try {
                        processSpawnerGeneration(instance, now, intervalMillis);
                    } catch (Exception exception) {
                        plugin.getLogger().log(Level.WARNING,
                                "Failed to process spawner generation for " + instance.getLocationKey(), exception);
                    }
                });
            } catch (Exception exception) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to schedule spawner generation for " + instance.getLocationKey(), exception);
            }
        }
        refreshOpenStorageMenus();
    }

    private void processSpawnerGeneration(SpawnerInstance instance, long now, long intervalMillis) {
        SpawnerTypeDefinition definition = getTypeDefinition(instance.getMobTypeKey());
        if (definition == null) {
            return;
        }

        World world = Bukkit.getWorld(instance.getWorld());
        if (world == null) {
            return;
        }

        int chunkX = instance.getX() >> 4;
        int chunkZ = instance.getZ() >> 4;
        if (processOnlyLoadedChunks && !world.isChunkLoaded(chunkX, chunkZ)) {
            return;
        }
        if (requirePlayerNearby && !hasNearbyPlayer(world, instance, playerNearbyRadius)) {
            return;
        }

        Block block = world.getBlockAt(instance.getX(), instance.getY(), instance.getZ());
        if (block.getType() != Material.SPAWNER) {
            return;
        }

        if (hopperExtractionEnabled) {
            tryHopperExtraction(instance, block);
        }

        long elapsed = now - instance.getLastProcessedAt();
        if (elapsed < intervalMillis) {
            return;
        }

        long cycles = elapsed / intervalMillis;
        if (cycles <= 0L) {
            return;
        }

        long totalRolls = cycles * definition.baseItemsPerCycle() * Math.max(1L, instance.getStackAmount());
        boolean changed = false;

        if (xpEnabled) {
            double xpGenerated = cycles * definition.xpPerCycle() * Math.max(1L, instance.getStackAmount());
            if (xpGenerated > 0.0) {
                instance.addStoredXp(xpGenerated);
                changed = true;
            }
        }

        if (totalRolls <= 0L) {
            instance.setLastProcessedAt(now);
            if (!isTemporarySpawner(instance)) {
                saveSpawnerAsync(instance);
            }
            return;
        }

        for (SpawnerTypeDefinition.DropDefinition drop : definition.drops()) {
            if (instance.isLootDisabled(drop.key()) || instance.isLootDisabled(drop.material().name())) {
                continue;
            }

            double expected = totalRolls * drop.chance() * drop.averageDropAmount();
            long generated = (long) Math.floor(expected);
            double remainder = expected - generated;
            if (remainder > 0D && ThreadLocalRandom.current().nextDouble() < remainder) {
                generated++;
            }
            if (generated <= 0L) {
                continue;
            }

            instance.addAutoMobDrop(drop.material(), generated, storageCapPerLootKey);
            changed = true;
        }

        instance.setLastProcessedAt(instance.getLastProcessedAt() + (cycles * intervalMillis));
        instance.setUpdatedAt(now);
        if (!isTemporarySpawner(instance)) {
            saveSpawnerAsync(instance);
        }
        if (changed) {
            saveLoot(instance);
        }
    }

    private void tryHopperExtraction(SpawnerInstance instance, Block block) {
        Block blockBelow = block.getRelative(BlockFace.DOWN);
        if (blockBelow.getType() != Material.HOPPER) {
            return;
        }
        if (!(blockBelow.getState() instanceof Hopper hopper)) {
            return;
        }

        Inventory hopperInventory = hopper.getInventory();
        boolean changed = false;

        for (SpawnerLootEntry entry : new ArrayList<>(instance.getStoredLootEntries())) {
            if (instance.isLootDisabled(entry.getKey())) {
                continue;
            }
            long storedAmount = entry.getAmount();
            if (storedAmount <= 0L) {
                continue;
            }

            Material material = entry.getMaterial();
            int maxStack = material.getMaxStackSize();
            long amountToTransfer = Math.min(storedAmount, Math.min(maxStack, hopperExtractionAmountPerCycle));
            if (amountToTransfer <= 0) {
                continue;
            }

            ItemStack itemToAdd = new ItemStack(material, (int) amountToTransfer);
            Map<Integer, ItemStack> remaining = hopperInventory.addItem(itemToAdd);
            int addedAmount = (int) amountToTransfer;
            for (ItemStack leftover : remaining.values()) {
                addedAmount -= leftover.getAmount();
            }

            if (addedAmount > 0) {
                instance.removeStoredLoot(entry.getKey(), addedAmount);
                changed = true;
            }
            if (hopperInventory.firstEmpty() == -1 && !remaining.isEmpty()) {
                break;
            }
        }

        if (changed) {
            if (!isTemporarySpawner(instance)) {
                saveSpawnerAsync(instance);
            }
            saveLoot(instance);
        }
    }


    public void registerOpenStorageMenu(Player player, SpawnerStorageMenu menu) {
        if (player == null || menu == null) {
            return;
        }
        openStorageMenus.put(player.getUniqueId(), menu);
    }

    public void unregisterOpenStorageMenu(Player player, SpawnerStorageMenu menu) {
        if (player == null || menu == null) {
            return;
        }
        openStorageMenus.remove(player.getUniqueId(), menu);
    }

    public void refreshOpenStorageMenus() {
        if (openStorageMenus.isEmpty()) {
            return;
        }

        for (Map.Entry<UUID, SpawnerStorageMenu> entry : openStorageMenus.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                openStorageMenus.remove(entry.getKey(), entry.getValue());
                continue;
            }

            SpawnerStorageMenu menu = entry.getValue();
            plugin.getSpigotScheduler().runEntity(player, () -> {
                if (player.isOnline()) {
                    menu.refresh(player);
                }
            });
        }
    }


    public Location getSpawnerCenter(SpawnerInstance instance) {
        World world = Bukkit.getWorld(instance.getWorld());
        return world == null
                ? new Location(Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0), 0, 0, 0)
                : new Location(world, instance.getX() + 0.5D, instance.getY() + 0.5D, instance.getZ() + 0.5D);
    }

    public void sendSpawnerVisual(Player player, SpawnerInstance instance) {
        if (player == null || !player.isOnline() || instance == null) {
            return;
        }

        World world = player.getWorld();
        if (world == null || !world.getName().equalsIgnoreCase(instance.getWorld())) {
            return;
        }

        Block block = world.getBlockAt(instance.getX(), instance.getY(), instance.getZ());
        if (block.getType() != Material.SPAWNER) {
            return;
        }

        if (!(block.getState() instanceof CreatureSpawner spawnerState)) {
            player.sendBlockChange(block.getLocation(), block.getBlockData());
            return;
        }

        SpawnerTypeDefinition definition = getTypeDefinition(instance.getMobTypeKey());
        if (definition == null) {
            player.sendBlockChange(block.getLocation(), block.getBlockData());
            return;
        }

        boolean updated = false;
        if (spawnerState.getSpawnedType() != definition.entityType()) {
            spawnerState.setSpawnedType(definition.entityType());
            updated = true;
        }
        if (applyVanillaSpawnPolicy(spawnerState)) {
            updated = true;
        }
        if (updated) {
            spawnerState.update(true, false);
        }

        player.sendBlockChange(block.getLocation(), block.getBlockData());
        try {
            player.sendBlockUpdate(block.getLocation(), spawnerState);
        } catch (IllegalArgumentException | NoSuchMethodError ignored) {
        }
    }

    public void syncSpawnerBlockState(SpawnerInstance instance) {
        if (instance == null) {
            return;
        }

        World world = Bukkit.getWorld(instance.getWorld());
        if (world == null) {
            return;
        }

        int chunkX = instance.getX() >> 4;
        int chunkZ = instance.getZ() >> 4;
        if (!plugin.getSpigotScheduler().isFolia() && Bukkit.isPrimaryThread()) {
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                return;
            }
            syncSpawnerBlockStateImmediate(instance);
            return;
        }

        plugin.getSpigotScheduler().runRegion(world, chunkX, chunkZ, () -> syncSpawnerBlockStateImmediate(instance));
    }

    private void syncSpawnerBlockStateImmediate(SpawnerInstance instance) {
        if (instance == null) {
            return;
        }

        World world = Bukkit.getWorld(instance.getWorld());
        if (world == null || !world.isChunkLoaded(instance.getX() >> 4, instance.getZ() >> 4)) {
            return;
        }

        SpawnerTypeDefinition definition = getTypeDefinition(instance.getMobTypeKey());
        if (definition == null) {
            return;
        }

        Block block = world.getBlockAt(instance.getX(), instance.getY(), instance.getZ());
        if (block.getType() != Material.SPAWNER || !(block.getState() instanceof CreatureSpawner spawnerState)) {
            return;
        }

        boolean updated = false;
        if (spawnerState.getSpawnedType() != definition.entityType()) {
            spawnerState.setSpawnedType(definition.entityType());
            updated = true;
        }
        if (spawnerState.getDelay() > 800 || spawnerState.getDelay() < 200) {
            spawnerState.setMinSpawnDelay(200);
            spawnerState.setMaxSpawnDelay(800);
            spawnerState.setDelay(200);
            updated = true;
        }
        if (applyVanillaSpawnPolicy(spawnerState)) {
            updated = true;
        }
        if (stampSpawnerIdentity(spawnerState, instance)) {
            updated = true;
        }
        if (updated) {
            spawnerState.update(true, true);
        }
    }

    private boolean stampSpawnerIdentity(CreatureSpawner spawnerState, SpawnerInstance instance) {
        PersistentDataContainer container = spawnerState.getPersistentDataContainer();
        boolean changed = false;

        Byte marker = container.get(blockMarkerKey, PersistentDataType.BYTE);
        if (marker == null || marker != (byte) 1) {
            container.set(blockMarkerKey, PersistentDataType.BYTE, (byte) 1);
            changed = true;
        }
        if (!instance.getMobTypeKey().equals(container.get(blockTypeKey, PersistentDataType.STRING))) {
            container.set(blockTypeKey, PersistentDataType.STRING, instance.getMobTypeKey());
            changed = true;
        }
        Long amount = container.get(blockAmountKey, PersistentDataType.LONG);
        if (amount == null || amount != instance.getStackAmount()) {
            container.set(blockAmountKey, PersistentDataType.LONG, instance.getStackAmount());
            changed = true;
        }
        String owner = instance.getOwnerUuid() == null ? "" : instance.getOwnerUuid().toString();
        if (!owner.equals(container.get(blockOwnerKey, PersistentDataType.STRING))) {
            container.set(blockOwnerKey, PersistentDataType.STRING, owner);
            changed = true;
        }
        if (!instance.getOwnerNameSnapshot().equals(container.get(blockOwnerNameKey, PersistentDataType.STRING))) {
            container.set(blockOwnerNameKey, PersistentDataType.STRING, instance.getOwnerNameSnapshot());
            changed = true;
        }
        if (!instance.getAccessMode().name().equals(container.get(blockAccessKey, PersistentDataType.STRING))) {
            container.set(blockAccessKey, PersistentDataType.STRING, instance.getAccessMode().name());
            changed = true;
        }
        return changed;
    }

    public SpawnerInstance restoreOrphanedSpawner(Block block) {
        if (!enabled || block == null || block.getType() != Material.SPAWNER) {
            return null;
        }
        if (getSpawner(block) != null) {
            return null;
        }
        if (!(block.getState() instanceof CreatureSpawner spawnerState)) {
            return null;
        }

        PersistentDataContainer container = spawnerState.getPersistentDataContainer();
        Byte marker = container.get(blockMarkerKey, PersistentDataType.BYTE);
        if (marker == null || marker != (byte) 1) {
            return null;
        }

        SpawnerTypeDefinition definition = getTypeDefinition(container.get(blockTypeKey, PersistentDataType.STRING));
        if (definition == null) {
            return null;
        }

        UUID ownerUuid;
        try {
            ownerUuid = UUID.fromString(Objects.requireNonNullElse(
                    container.get(blockOwnerKey, PersistentDataType.STRING), ""));
        } catch (IllegalArgumentException exception) {
            ownerUuid = new UUID(0L, 0L);
        }

        Long storedAmount = container.get(blockAmountKey, PersistentDataType.LONG);
        long now = System.currentTimeMillis();
        SpawnerInstance instance = new SpawnerInstance(
                0L,
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ(),
                ownerUuid,
                Objects.requireNonNullElse(container.get(blockOwnerNameKey, PersistentDataType.STRING), ""),
                definition.key(),
                storedAmount == null ? 1L : Math.max(1L, storedAmount),
                SpawnerInstance.AccessMode.fromString(
                        container.get(blockAccessKey, PersistentDataType.STRING), defaultAccessMode),
                now,
                now,
                now
        );

        long id = plugin.getDatabaseManager().createSpawner(instance);
        if (id <= 0L) {
            plugin.getLogger().warning("[SpawnerManager] Found a stray managed spawner at "
                    + instance.getLocationKey() + " but could not write it back to the database.");
            return null;
        }

        instance.setId(id);
        registerSpawner(instance);
        syncSpawnerBlockStateImmediate(instance);
        refreshAntiEspNearby(block.getLocation());
        plugin.getLogger().info("[SpawnerManager] Restored a stray " + definition.key() + " spawner at "
                + instance.getLocationKey() + " from its block data; its stored loot could not be recovered.");
        return instance;
    }

    private boolean applyVanillaSpawnPolicy(CreatureSpawner spawnerState) {
        boolean changed = false;
        if (cancelMobSpawn) {
            if (spawnerState.getMaxNearbyEntities() != 0) {
                spawnerState.setMaxNearbyEntities(0);
                changed = true;
            }
            if (spawnerState.getRequiredPlayerRange() <= 0) {
                spawnerState.setRequiredPlayerRange(16);
                changed = true;
            }
            if (spawnerState.getSpawnCount() <= 0) {
                spawnerState.setSpawnCount(4);
                changed = true;
            }
            return changed;
        }

        if (spawnerState.getRequiredPlayerRange() <= 0) {
            spawnerState.setRequiredPlayerRange(16);
            changed = true;
        }
        if (spawnerState.getSpawnCount() <= 0) {
            spawnerState.setSpawnCount(4);
            changed = true;
        }
        if (spawnerState.getMaxNearbyEntities() <= 0) {
            spawnerState.setMaxNearbyEntities(6);
            changed = true;
        }
        return changed;
    }

    public void syncSpawnersInChunk(World world, int chunkX, int chunkZ) {
        if (!enabled || world == null) {
            return;
        }

        List<SpawnerInstance> instances = getSpawnersInChunk(world.getName(), chunkX, chunkZ);
        for (SpawnerInstance instance : instances) {
            syncSpawnerBlockStateImmediate(instance);
        }
    }

    private void releaseSpawnerBlock(SpawnerInstance instance) {
        if (instance == null) {
            return;
        }

        World world = Bukkit.getWorld(instance.getWorld());
        if (world == null) {
            return;
        }

        int chunkX = instance.getX() >> 4;
        int chunkZ = instance.getZ() >> 4;
        if (!plugin.getSpigotScheduler().isFolia() && Bukkit.isPrimaryThread()) {
            if (world.isChunkLoaded(chunkX, chunkZ)) {
                releaseSpawnerBlockImmediate(instance);
            }
            return;
        }

        plugin.getSpigotScheduler().runRegion(world, chunkX, chunkZ, () -> releaseSpawnerBlockImmediate(instance));
    }

    private void releaseSpawnerBlockImmediate(SpawnerInstance instance) {
        World world = Bukkit.getWorld(instance.getWorld());
        if (world == null || !world.isChunkLoaded(instance.getX() >> 4, instance.getZ() >> 4)) {
            return;
        }

        Block block = world.getBlockAt(instance.getX(), instance.getY(), instance.getZ());
        if (block.getType() != Material.SPAWNER || !(block.getState() instanceof CreatureSpawner spawnerState)) {
            return;
        }

        spawnerState.setRequiredPlayerRange(0);
        spawnerState.setSpawnCount(0);
        spawnerState.setMaxNearbyEntities(0);
        spawnerState.update(true, false);
    }

    private void refreshAntiEspNearby(Location location) {
        AntiEspManager antiEsp = plugin.getAntiEspManager();
        if (antiEsp != null) {
            antiEsp.refreshNearby(location);
        }
    }


    public boolean canOpen(Player player, SpawnerInstance instance) {
        if (player == null || instance == null) {
            return false;
        }
        if (allowSpawnerSteal || PermissionUtils.has(player, ADMIN_PERMISSION)) {
            return true;
        }
        if (player.getUniqueId().equals(instance.getOwnerUuid())) {
            return true;
        }

        return switch (instance.getAccessMode()) {
            case PUBLIC -> true;
            case OWNER_AND_TEAM, OWNER_ONLY -> false;
        };
    }

    public boolean canBreak(Player player, SpawnerInstance instance) {
        return canOpen(player, instance);
    }

    public boolean canModify(Player player, SpawnerInstance instance) {
        return canOpen(player, instance);
    }


    public void deleteSpawnerAsync(long spawnerId) {
        if (spawnerId <= 0L) {
            return;
        }
        plugin.getDatabaseManager().executeAsync(() -> plugin.getDatabaseManager().deleteSpawner(spawnerId));
    }

    public void saveSpawnerAsync(SpawnerInstance instance) {
        if (instance == null || isTemporarySpawner(instance)) {
            return;
        }
        plugin.getDatabaseManager().executeAsync(() -> plugin.getDatabaseManager().saveSpawner(instance));
    }

    public void saveLoot(SpawnerInstance instance) {
        if (instance == null || isTemporarySpawner(instance)) {
            return;
        }

        long spawnerId = instance.getId();
        List<SpawnerLootEntry> latestLoot = new ArrayList<>(instance.getStoredLootEntries());
        boolean isFirst = pendingLootMap.put(spawnerId, latestLoot) == null;
        if (isFirst) {
            plugin.getDatabaseManager().executeAsync(() -> {
                List<SpawnerLootEntry> toSave = pendingLootMap.remove(spawnerId);
                if (toSave != null) {
                    plugin.getDatabaseManager().replaceSpawnerLoot(spawnerId, toSave);
                }
            });
        }
    }

    public void saveSpawnerAndLoot(SpawnerInstance instance) {
        if (instance == null || isTemporarySpawner(instance)) {
            return;
        }
        saveSpawnerAsync(instance);
        saveLoot(instance);
    }

    public void shutdown() {
        List<SpawnerInstance> copy;
        synchronized (lock) {
            copy = new ArrayList<>(spawnersById.values());
        }
        for (SpawnerInstance instance : copy) {
            if (isTemporarySpawner(instance)) {
                continue;
            }
            plugin.getDatabaseManager().saveSpawner(instance);
            plugin.getDatabaseManager().replaceSpawnerLoot(instance.getId(),
                    new ArrayList<>(instance.getStoredLootEntries()));
        }
    }

    private void registerSpawner(SpawnerInstance instance) {
        synchronized (lock) {
            spawnersById.put(instance.getId(), instance);
            locationIndex.put(instance.getLocationKey(), instance.getId());
            worldIndex.computeIfAbsent(instance.getWorld().toLowerCase(Locale.US), ignored -> new LinkedHashSet<>())
                    .add(instance.getId());
            chunkIndex.computeIfAbsent(buildChunkKey(instance.getWorld(), instance.getX() >> 4, instance.getZ() >> 4),
                    ignored -> new LinkedHashSet<>()).add(instance.getId());
        }
    }

    private void unregisterSpawner(SpawnerInstance instance) {
        synchronized (lock) {
            spawnersById.remove(instance.getId());
            locationIndex.remove(instance.getLocationKey());
            String worldKey = instance.getWorld().toLowerCase(Locale.US);
            LinkedHashSet<Long> ids = worldIndex.get(worldKey);
            if (ids != null) {
                ids.remove(instance.getId());
                if (ids.isEmpty()) {
                    worldIndex.remove(worldKey);
                }
            }

            String chunkKey = buildChunkKey(instance.getWorld(), instance.getX() >> 4, instance.getZ() >> 4);
            LinkedHashSet<Long> chunkIds = chunkIndex.get(chunkKey);
            if (chunkIds != null) {
                chunkIds.remove(instance.getId());
                if (chunkIds.isEmpty()) {
                    chunkIndex.remove(chunkKey);
                }
            }
        }
    }

    private static String buildChunkKey(String world, int chunkX, int chunkZ) {
        return (world == null ? "" : world.toLowerCase(Locale.US)) + ":" + chunkX + ":" + chunkZ;
    }

    public List<SpawnerInstance> getSpawnersInChunk(String worldName, int chunkX, int chunkZ) {
        synchronized (lock) {
            LinkedHashSet<Long> ids = chunkIndex.get(buildChunkKey(worldName, chunkX, chunkZ));
            if (ids == null || ids.isEmpty()) {
                return List.of();
            }

            List<SpawnerInstance> spawners = new ArrayList<>(ids.size());
            for (Long id : ids) {
                SpawnerInstance instance = spawnersById.get(id);
                if (instance != null) {
                    spawners.add(instance);
                }
            }
            return spawners;
        }
    }


    private long moveMaterialToInventory(PlayerInventory inventory, Material material, long amount) {
        if (inventory == null || material == null || amount <= 0L) {
            return 0L;
        }

        long remaining = amount;
        long moved = 0L;
        int maxStack = material.getMaxStackSize();

        while (remaining > 0L) {
            int stackAmount = (int) Math.min(maxStack, remaining);
            Map<Integer, ItemStack> leftovers = inventory.addItem(new ItemStack(material, stackAmount));
            if (leftovers.isEmpty()) {
                moved += stackAmount;
                remaining -= stackAmount;
                continue;
            }

            int leftoverAmount = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
            moved += Math.max(0L, stackAmount - leftoverAmount);
            break;
        }
        return moved;
    }

    private long dropMaterial(Player player, Location location, Material material, long amount) {
        if (material == null || amount <= 0L) {
            return 0L;
        }

        World world;
        Location spawnLoc;
        org.bukkit.util.Vector velocity = null;

        if (player != null && player.isOnline()) {
            world = player.getWorld();
            spawnLoc = player.getLocation().add(0, 1.2, 0);
            velocity = player.getLocation().getDirection().normalize().multiply(0.35D);
        } else if (location != null && location.getWorld() != null) {
            world = location.getWorld();
            spawnLoc = location;
        } else {
            return 0L;
        }

        long remaining = amount;
        long dropped = 0L;
        int maxStack = material.getMaxStackSize();

        while (remaining > 0L) {
            int stackAmount = (int) Math.min(maxStack, remaining);
            org.bukkit.entity.Item droppedItem = world.dropItem(spawnLoc, new ItemStack(material, stackAmount));
            if (velocity != null) {
                droppedItem.setVelocity(velocity);
            }
            dropped += stackAmount;
            remaining -= stackAmount;
        }
        return dropped;
    }

    private boolean hasNearbyPlayer(World world, SpawnerInstance instance, double radius) {
        double radiusSquared = radius * radius;
        Location center = new Location(world, instance.getX() + 0.5D, instance.getY() + 0.5D, instance.getZ() + 0.5D);
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(center) <= radiusSquared) {
                return true;
            }
        }
        return false;
    }

    public void consumeHeldSpawnerItem(Player player, boolean all) {
        if (player == null || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            return;
        }
        consumeHeldSpawnerItem(player, all ? hand.getAmount() : 1);
    }

    public void consumeHeldSpawnerItem(Player player, int amount) {
        if (player == null || player.getGameMode() == GameMode.CREATIVE || amount <= 0) {
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            return;
        }

        int nextAmount = hand.getAmount() - amount;
        if (nextAmount <= 0) {
            player.getInventory().setItemInMainHand(null);
            return;
        }
        hand.setAmount(nextAmount);
        player.getInventory().setItemInMainHand(hand);
    }


    public String describeWorld(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return "Unknown";
        }

        return switch (worldName.trim().toLowerCase(Locale.US)) {
            case "world", "overworld" -> "Overworld";
            case "world_nether", "nether" -> "Nether";
            case "world_the_end", "the_end", "the-end", "end" -> "The End";
            default -> WorthManager.prettify(worldName);
        };
    }

    public Material getWorldIcon(World world) {
        if (world == null) {
            return Material.GRASS_BLOCK;
        }
        return switch (world.getEnvironment()) {
            case NETHER -> Material.NETHERRACK;
            case THE_END -> Material.END_STONE;
            default -> Material.GRASS_BLOCK;
        };
    }

    private int getWorldSortIndex(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return Integer.MAX_VALUE;
        }
        return switch (worldName.trim().toLowerCase(Locale.US)) {
            case "world", "overworld" -> 0;
            case "world_nether", "nether" -> 1;
            case "world_the_end", "the_end", "the-end", "end" -> 2;
            default -> 10;
        };
    }

    public String prettifyKey(String key) {
        return key == null || key.isBlank() ? "Spawner" : WorthManager.prettify(key);
    }

    public int normalizeSize(int size) {
        int normalized = Math.max(9, ((size + 8) / 9) * 9);
        return Math.min(54, normalized);
    }


    private String message(String key, Object... replacements) {
        return plugin.getMessageManager().get(key, replacements);
    }

    private ActionResult ok(String key, Object... replacements) {
        return new ActionResult(true, message(key, replacements));
    }

    private ActionResult fail(String key, Object... replacements) {
        return new ActionResult(false, message(key, replacements));
    }

    private SellLootResult failSell(String key, Object... replacements) {
        return new SellLootResult(false, message(key, replacements), 0D, 0L);
    }
}
