package com.github.JumDa5he.callresponse.compat.cage;

import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.MaidModelInfo;
import com.github.tartaricacid.touhoulittlemaid.entity.info.ServerCustomPackLoader;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;
import java.util.Optional;

/** 铁笼信息统一使用的实体名称选择逻辑。 */
public final class CageEntityNameHelper {
    private CageEntityNameHelper() {
    }

    public static Component displayName(Entity entity) {
        if (entity instanceof Player) {
            return entity.getDisplayName();
        }
        if (entity.hasCustomName()) {
            return entity.getCustomName();
        }
        Component ysmName = findYsmModelName(entity);
        if (ysmName != null) {
            return ysmName;
        }
        if (entity instanceof EntityMaid maid) {
            Optional<MaidModelInfo> info = ServerCustomPackLoader.SERVER_MAID_MODELS.getInfo(maid.getModelId());
            if (info.isPresent()) {
                String raw = info.get().getName();
                if (raw.startsWith("{") && raw.endsWith("}")) {
                    return Component.translatable(raw.substring(1, raw.length() - 1));
                }
                if (!raw.isBlank()) {
                    return Component.literal(raw);
                }
            }
        }
        return entity.getDisplayName();
    }

    /** YSM 是可选模组，只通过反射读取常见的公开模型名称接口。 */
    private static Component findYsmModelName(Entity entity) {
        String namespace = entity.getType().builtInRegistryHolder().key().location().getNamespace();
        if (!"yes_steve_model".equals(namespace) && !"ysm".equals(namespace)) {
            return null;
        }
        for (String methodName : new String[]{"getModelName", "getModelId"}) {
            try {
                Method method = entity.getClass().getMethod(methodName);
                Object value = method.invoke(entity);
                if (value != null && !value.toString().isBlank()) {
                    return Component.literal(value.toString());
                }
            } catch (ReflectiveOperationException ignored) {
                // 不强依赖 YSM；接口版本不匹配时回退到实体显示名。
            }
        }
        return null;
    }
}
