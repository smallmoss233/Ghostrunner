package ghostrunner.api;

import ghostrunner.Ghostrunner;
import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

public final class GRTags {

    private GRTags() {}

    /**
     * 跑墙黑名单。这些方块**不能**跑墙，其余所有有碰撞体的方块都能。
     * <p>默认内容见 data/ghostrunner/tags/blocks/wall_run_blacklist.json
     */
    public static final TagKey<Block> WALL_RUN_BLACKLIST = TagKey.of(
            RegistryKeys.BLOCK,
            new Identifier(Ghostrunner.MOD_ID, "wall_run_blacklist"));
}