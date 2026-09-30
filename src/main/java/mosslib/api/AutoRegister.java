package mosslib.api;

import mosslib.MossLib;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;

public final class AutoRegister {

    private AutoRegister() {}

    // ==================== 注解 ====================

    /** 标记在 Block 字段上：注册 Block 但不自动创建 BlockItem。 */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface NoItem {}

    // ==================== ID 追踪 ====================

    /** object -> Identifier 映射，用于扫描时反查 id。 */
    private static final Map<Object, Identifier> ID_BY_OBJECT = new IdentityHashMap<>();

    // ==================== 工厂方法（26.3 必须在构造前 setId） ====================

    /**
     * 创建一个带注册 id 的 Item。
     * <p>26.3 起 {@link Item.Properties} 必须在构造前通过 {@code setId} 声明注册键，
     * 否则会抛 {@code NullPointerException: Item id not set}。
     *
     * @param modId   命名空间，如 {@code Ghostrunner.MOD_ID}
     * @param id      路径，如 {@code "ghostrunner_tag"}
     * @param factory 接收已经带 id 的 {@link Item.Properties}，返回具体 Item 实例
     */
    public static Item item(String modId, String id, Function<Item.Properties, Item> factory) {
        Identifier ident = Identifier.fromNamespaceAndPath(modId, id);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, ident);
        Item item = factory.apply(new Item.Properties().setId(key));
        ID_BY_OBJECT.put(item, ident);
        return item;
    }

    /** 创建一个带注册 id 的 Block。 */
    public static Block block(String modId, String id, Function<Block.Properties, Block> factory) {
        Identifier ident = Identifier.fromNamespaceAndPath(modId, id);
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, ident);
        Block block = factory.apply(Block.Properties.of().setId(key));
        ID_BY_OBJECT.put(block, ident);
        return block;
    }

    /**
     * 为已构造的 {@link EntityType} 登记 id。
     * <p>{@code EntityType.Builder#build(ResourceKey)} 已经处理了 setId，
     * 这里只负责记录 id 供扫描时反查。
     */
    public static <T extends EntityType<?>> T entity(String modId, String id, T type) {
        ID_BY_OBJECT.put(type, Identifier.fromNamespaceAndPath(modId, id));
        return type;
    }

    /** 为已构造的 {@link BlockEntityType} 登记 id。 */
    public static <T extends BlockEntityType<?>> T blockEntity(String modId, String id, T type) {
        ID_BY_OBJECT.put(type, Identifier.fromNamespaceAndPath(modId, id));
        return type;
    }

    // ==================== 公开注册入口 ====================

    /** 注册类里所有 public static final Item 字段。 */
    public static void items(Class<?> clazz) {
        int count = scan(clazz, Item.class, BuiltInRegistries.ITEM);
        MossLib.LOGGER.info("[AutoRegister] {} items from {}", count, clazz.getSimpleName());
        tryInvokePostRegister(clazz);
    }

    /** 注册类里所有 public static final Block 字段。 */
    public static void blocks(Class<?> clazz) {
        int count = scan(clazz, Block.class, BuiltInRegistries.BLOCK);
        MossLib.LOGGER.info("[AutoRegister] {} blocks from {}", count, clazz.getSimpleName());
    }

    /**
     * 注册所有 Block，并为每个 Block 创建一个带同样 id 的 BlockItem。
     * <p>被 {@link NoItem} 注解标记的字段会跳过 BlockItem 创建。
     */
    public static void blocksWithItems(Class<?> clazz) {
        int blocks = 0;
        int items = 0;

        for (Field field : clazz.getDeclaredFields()) {
            if (!isValid(field, Block.class)) continue;
            Block block = getStaticField(field, Block.class);

            Identifier blockId = ID_BY_OBJECT.get(block);
            if (blockId == null) {
                MossLib.LOGGER.error(
                        "[AutoRegister] Block {} was not created via AutoRegister.block(), skipping",
                        field.getName());
                continue;
            }

            Registry.register(BuiltInRegistries.BLOCK, blockId, block);
            blocks++;

            if (!field.isAnnotationPresent(NoItem.class)) {
                ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, blockId);
                BlockItem blockItem = new BlockItem(block, new Item.Properties().setId(itemKey));
                Registry.register(BuiltInRegistries.ITEM, blockId, blockItem);
                items++;
            }
        }

        MossLib.LOGGER.info("[AutoRegister] {} blocks + {} items from {}",
                blocks, items, clazz.getSimpleName());
    }

    /** 注册所有 EntityType 字段。 */
    public static void entities(Class<?> clazz) {
        int count = scan(clazz, EntityType.class, BuiltInRegistries.ENTITY_TYPE);
        MossLib.LOGGER.info("[AutoRegister] {} entities from {}", count, clazz.getSimpleName());
    }

    /** 注册所有 BlockEntityType 字段。 */
    public static void blockEntities(Class<?> clazz) {
        int count = scan(clazz, BlockEntityType.class, BuiltInRegistries.BLOCK_ENTITY_TYPE);
        MossLib.LOGGER.info("[AutoRegister] {} block entities from {}", count, clazz.getSimpleName());
    }

    // ==================== 私有工具 ====================

    /** 通用扫描注册。id 从 {@link #ID_BY_OBJECT} 查，查不到就跳过并报错。 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int scan(Class<?> clazz, Class<?> type, Registry registry) {
        int count = 0;
        for (Field field : clazz.getDeclaredFields()) {
            if (!isValid(field, type)) continue;
            Object instance = getStaticField(field, type);

            Identifier id = ID_BY_OBJECT.get(instance);
            if (id == null) {
                MossLib.LOGGER.error(
                        "[AutoRegister] {} was not created via AutoRegister factory, skipping",
                        field.getName());
                continue;
            }

            Registry.register(registry, id, instance);
            MossLib.LOGGER.debug("[AutoRegister] Registered {} -> {}", id, clazz.getSimpleName());
            count++;
        }
        return count;
    }

    private static <T> T getStaticField(Field field, Class<T> type) {
        try {
            Object value = field.get(null);
            if (value == null) {
                MossLib.LOGGER.error("[AutoRegister] Field {} is null. " +
                        "AutoRegister must be called AFTER field initialization.", field.getName());
                throw new IllegalStateException("Field " + field.getName() + " is null.");
            }
            return type.cast(value);
        } catch (IllegalAccessException e) {
            MossLib.LOGGER.error("[AutoRegister] Failed to read field: {}", field.getName(), e);
            throw new RuntimeException("Failed to read field: " + field.getName(), e);
        }
    }

    private static void tryInvokePostRegister(Class<?> clazz) {
        Method method;
        try {
            method = clazz.getDeclaredMethod("registerAbilities");
        } catch (NoSuchMethodException e) {
            return;
        }

        if (!Modifier.isStatic(method.getModifiers())) {
            MossLib.LOGGER.error("[AutoRegister] registerAbilities() must be static in {}",
                    clazz.getName());
            throw new IllegalStateException(
                    "registerAbilities() must be static in " + clazz.getName());
        }

        try {
            method.setAccessible(true);
            method.invoke(null);
            MossLib.LOGGER.debug("[AutoRegister] Invoked registerAbilities() on {}",
                    clazz.getSimpleName());
        } catch (Exception e) {
            MossLib.LOGGER.error("[AutoRegister] Failed to invoke registerAbilities() on {}",
                    clazz.getName(), e);
            throw new RuntimeException(
                    "Failed to invoke registerAbilities() on " + clazz.getName(), e);
        }
    }

    private static boolean isValid(Field field, Class<?> expectedType) {
        int mod = field.getModifiers();
        return Modifier.isPublic(mod)
                && Modifier.isStatic(mod)
                && Modifier.isFinal(mod)
                && expectedType.isAssignableFrom(field.getType());
    }
}