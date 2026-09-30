package mosslib.util.tooltip;

import mosslib.MossLib;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.List;

public class TooltipHelper {

    public static void addWrappedTooltip(List<Component> tooltip, Component originalText) {
        try {
            String raw = originalText.getString();

            TextColor color = originalText.getStyle().getColor();

            String colorCode = "";
            if (color == null) {
                int index = raw.indexOf('§');
                if (index != -1 && index + 1 < raw.length()) {
                    char code = raw.charAt(index + 1);
                    colorCode = "§" + code;
                }
            }

            String[] lines = raw.split("\\*");
            for (String line : lines) {
                if (!line.isEmpty()) {
                    MutableComponent text = Component.literal(line);
                    if (color != null) {
                        text = text.setStyle(Style.EMPTY.withColor(color));
                    } else if (!colorCode.isEmpty()) {
                        text = Component.literal(colorCode + line);
                    }
                    tooltip.add(text);
                }
            }
        } catch (Exception e) {
            // 不刷屏：只在异常时记一次
            MossLib.LOGGER.warn("[TooltipHelper] Failed to wrap tooltip: {}", e.getMessage());
        }
    }
}