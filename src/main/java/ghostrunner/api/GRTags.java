package ghostrunner.api;

import ghostrunner.Ghostrunner;
import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

public final class GRTags {

    private GRTags() {}

    /** 可以跑墙的方块白名单。默认内容见 data/ghostrunner/tags/blocks/wallrunnable.json */
    public static final TagKey<Block> WALL_RUNNABLE = TagKey.of(
            RegistryKeys.BLOCK,
            new Identifier(Ghostrunner.MOD_ID, "wallrunnable"));
}