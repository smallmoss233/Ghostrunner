package mosslib.util;

import com.mojang.blaze3d.platform.InputConstants;
import mosslib.util.tooltip.TooltipHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

public class ShiftTooltipHelper {

    @Environment(EnvType.CLIENT)
    public static void addShiftTooltip(List<Component> tooltip, Component longText) {
        boolean shiftPressed = InputConstants.isKeyDown(InputConstants.KEY_LSHIFT)
                || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);

        if (shiftPressed) {
            TooltipHelper.addWrappedTooltip(tooltip, longText);
        } else {
            tooltip.add(Component.translatable("tooltip.ghostrunner.hold_shift")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }
}