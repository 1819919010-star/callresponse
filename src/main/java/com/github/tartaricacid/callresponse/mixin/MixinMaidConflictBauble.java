package com.github.tartaricacid.callresponse.mixin;

import com.github.tartaricacid.callresponse.compat.bauble.BaubleDetector;
import com.github.tartaricacid.callresponse.compat.broadcast.MaidResponder;
import com.github.tartaricacid.callresponse.compat.emotion.EmotionPrompt;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(EntityMaid.class)
public abstract class MixinMaidConflictBauble extends Mob {

    private static final int CONFLICT_DAMAGE_INTERVAL = 10;
    private static final int CONFLICT_DIALOGUE_COOLDOWN = 600;
    private static final Map<UUID, Long> LAST_DIALOGUE_TICKS = new ConcurrentHashMap<>();

    private MixinMaidConflictBauble() {
        super(null, null);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void callresponse$conflictBaubleTick(CallbackInfo ci) {
        EntityMaid maid = (EntityMaid) (Object) this;
        if (maid.level().isClientSide) {
            return;
        }
        if (maid.tickCount % CONFLICT_DAMAGE_INTERVAL != 0) {
            return;
        }
        if (!BaubleDetector.hasNoEat(maid) || !BaubleDetector.hasMoreEat(maid)) {
            return;
        }

        // 无来源伤害，绕过一切保护与减免
        super.hurt(maid.damageSources().genericKill(), 10.0F);

        // AI 对话（带冷却，避免刷爆 LLM 请求）
        long gameTime = maid.level().getGameTime();
        UUID maidId = maid.getUUID();
        if (LAST_DIALOGUE_TICKS.getOrDefault(maidId, 0L) + CONFLICT_DIALOGUE_COOLDOWN > gameTime) {
            return;
        }
        LAST_DIALOGUE_TICKS.put(maidId, gameTime);
        if (maid.getOwner() instanceof ServerPlayer player) {
            String command = "你体内又两股力量在疯狂撕扯着你，一个让你什么都不能吃，一个让你疯狂的想要吃，你感到头晕目眩，身上不断地受到伤害，感觉整个人要被撕扯成了碎片，你感到异常的痛苦与烦躁。请按照目前的情感发泄一下。\n\n【当前情感】\n"
                    + EmotionPrompt.buildEmotionContext(maid, player);
            MaidResponder.processBroadcast(player, Collections.singletonList(maid), command, false);
        }
    }
}
