package ghostrunner.item;

import ghostrunner.Ghostrunner;
import mosslib.api.AutoRegister;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public final class GRItems {

    private GRItems() {}

    public static final Item GHOSTRUNNER_TAG = AutoRegister.item(
            Ghostrunner.MOD_ID,
            "ghostrunner_tag",
            props -> new GhostrunnerTagItem(props.stacksTo(1).rarity(Rarity.EPIC)));

    public static void register() {
        AutoRegister.items(GRItems.class);
    }
}