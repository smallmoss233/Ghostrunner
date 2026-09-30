package mosslib.util.tooltip;

import mosslib.MossLib;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;
import java.util.List;

public class ShiftTooltipInvoker {

    private static final String HELPER_CLASS = "mosslib.util.ShiftTooltipHelper";
    private static final String METHOD_NAME = "addShiftTooltip";

    /** 缓存"这个类不存在"的状态，避免每次调用都尝试反射 + 打日志。 */
    private static boolean helperUnavailable = false;

    public static void addShiftTooltip(List<Component> tooltip, Component longText) {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            return;
        }

        if (helperUnavailable) return;

        try {
            Class<?> clazz = Class.forName(HELPER_CLASS);
            Method method = clazz.getDeclaredMethod(METHOD_NAME, List.class, Component.class);
            method.invoke(null, tooltip, longText);
        } catch (ClassNotFoundException e) {
            // 客户端未安装 helper —— 只警告一次
            MossLib.LOGGER.warn("[ShiftTooltipInvoker] {} not found on client. " +
                    "Shift tooltips disabled.", HELPER_CLASS);
            helperUnavailable = true;
        } catch (Exception e) {
            MossLib.LOGGER.error("[ShiftTooltipInvoker] Failed to invoke {}: {}",
                    METHOD_NAME, e.getMessage(), e);
        }
    }
}