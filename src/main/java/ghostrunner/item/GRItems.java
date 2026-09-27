package ghostrunner.item;

import ghostrunner.Ghostrunner;
import mosslib.api.AutoRegister;
import net.minecraft.item.Item;
import net.minecraft.util.Rarity;

public final class GRItems {

    private GRItems() {}

    public static final Item GHOSTRUNNER_TAG = new GhostrunnerTagItem(
            new Item.Settings().maxCount(1).rarity(Rarity.EPIC));

    /**
     * 注册所有 public static final Item 字段。
     * <p>AutoRegister 按字段名 toLowerCase() 生成 id：
     * {@code GHOSTRUNNER_TAG} → {@code ghostrunner:ghostrunner_tag}
     */
    public static void register() {
        AutoRegister.items(GRItems.class, Ghostrunner.MOD_ID);
    }
}