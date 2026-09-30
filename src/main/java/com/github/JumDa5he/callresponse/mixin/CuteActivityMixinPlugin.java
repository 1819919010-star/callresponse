package com.github.JumDa5he.callresponse.mixin;

import net.neoforged.fml.loading.FMLLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.List;
import java.util.Set;

/** Bytecode-only signature gate: optional target classes are never initialized just to probe them. */
public final class CuteActivityMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogManager.getLogger("callresponse-cute-activity");
    private static final String BASE = "cn/autoforged/maid_cute_activity/";
    private static final String MAID = "Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;";
    private static final String TICK = "Lcom/github/tartaricacid/touhoulittlemaid/api/event/MaidTickEvent;";
    private static final String DAMAGE = "Lnet/neoforged/neoforge/event/entity/living/LivingDamageEvent$Pre;";
    private static final String LONELY_STATE = "L" + BASE + "LonelinessHandler$LonelinessState;";
    private static final String GUARD_STATE = "L" + BASE + "FoodGuardHandler$FoodGuardState;";
    private static final String FLEE_STATE = "L" + BASE + "FleeHandler$FleeState;";
    private static final String ESCAPE_STATE = "L" + BASE + "EscapeAttackHandler$EscapeAttackState;";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains(".revengecompat.")) return revengeAddonCompatible(targetClassName, mixinClassName);
        if (mixinClassName.endsWith(".client.ConfiguredNeoForgeValueMixin")) {
            try {
                ClassNode configured = MixinService.getService().getBytecodeProvider().getClassNode(targetClassName);
                return configured != null
                        && has(configured, "getComment", "()Lnet/minecraft/network/chat/Component;")
                        && has(configured, "getTranslationKey", "()Ljava/lang/String;");
            } catch (Exception | LinkageError absent) {
                return false;
            }
        }
        if (!mixinClassName.contains(".cuteactivity.")) return true;
        ClassNode node;
        try {
            node = MixinService.getService().getBytecodeProvider().getClassNode(targetClassName);
        } catch (Exception | LinkageError absent) {
            if (cuteActivityDiscovered()) LOGGER.warn("Cute Activity target {} unavailable; skipping {} integration",
                    targetClassName, mixinClassName);
            return false;
        }
        if (node == null) return false;

        String simple = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        boolean compatible = switch (simple) {
            case "CuteScareServerMixin" -> has(node, "onLivingDamage", "(" + DAMAGE + ")V");
            case "CuteScareClientMixin" -> has(node, "isScareActive", "(" + MAID + ")Z")
                    && hasOther("cn.autoforged.maid_cute_activity.AnimBookData",
                    "isExcluded", "(" + MAID + ")Z");
            case "CuteLaowuPoseMixin" -> has(node, "onLivingDamage", "(" + DAMAGE + ")V")
                    && has(node, "onMaidTick", "(" + TICK + ")V")
                    && has(node, "cancelPose", "(" + MAID + ")V");
            case "CuteFoodGuardMixin" -> has(node, "triggerGuard", "(" + MAID
                    + "Lnet/minecraft/world/entity/LivingEntity;)V")
                    && has(node, "onMaidTick", "(" + TICK + ")V")
                    && has(node, "cancelGuard", "(" + MAID + ")V")
                    && has(node, "restoreWorkMode", "(" + MAID + GUARD_STATE + ")V");
            case "CuteTailPullMixin" -> has(node, "onTailPulled", "(" + MAID + ")V")
                    && has(node, "onMaidTick", "(" + TICK + ")V")
                    && has(node, "isInteractionActive", "(" + MAID + ")Z")
                    && has(node, "cancelInteraction", "(" + MAID + ")V");
            case "CuteFleeMixin" -> has(node, "canTrigger", "(" + MAID + FLEE_STATE + "J)Z")
                    && has(node, "onMaidTick", "(" + TICK + ")V")
                    && has(node, "cancelFlee", "(" + MAID + ")V");
            case "CuteEscapeAttackMixin" -> has(node, "onLivingDamage", "(" + DAMAGE + ")V")
                    && has(node, "canTrigger", "(" + MAID + ESCAPE_STATE + "J)Z")
                    && has(node, "onMaidTick", "(" + TICK + ")V")
                    && has(node, "cancelEscapeAttack", "(" + MAID + ")V");
            case "CuteLowHealthMixin" -> has(node, "onMaidTick", "(" + TICK + ")V")
                    && invokes(node, "onMaidTick", "isBelowHealthPercent", 1);
            case "CuteMercyPoseMixin" -> has(node, "onMaidTick", "(" + TICK + ")V")
                    && invokes(node, "onMaidTick", "isBelowHealthPercent", 1);
            case "CuteLonelinessCombatMixin" -> has(node, "handleWildMaidRetaliation", "(" + MAID + DAMAGE + ")V")
                    && has(node, "scanWildMaidCombatTarget", "(" + MAID + LONELY_STATE + "J)V")
                    && has(node, "manageWildMaidCombat", "(" + MAID + LONELY_STATE + "J)V")
                    && has(node, "enterWildMaidAttackMode", "(" + MAID + LONELY_STATE + ")V")
                    && has(node, "restoreWildMaidAttackMode", "(" + MAID + LONELY_STATE + ")V")
                    && has(node, "tryTriggerBegForMercyTalk", "(" + MAID + LONELY_STATE + "J)V");
            case "CuteLonelinessBaoTouMixin" -> has(node, "onLivingDamage", "(" + DAMAGE + ")V")
                    && invokes(node, "onLivingDamage", "handleWildMaidRetaliation", 6);
            default -> false;
        };
        if (!compatible) LOGGER.warn("Cute Activity {} signature differs; skipping {} integration",
                targetClassName, simple);
        return compatible;
    }

    private static boolean cuteActivityDiscovered() {
        try {
            var loading = FMLLoader.getLoadingModList();
            return loading != null && loading.getModFileById("maid_cute_activity") != null;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean hasOther(String target, String name, String descriptor) {
        try {
            ClassNode node = MixinService.getService().getBytecodeProvider().getClassNode(target);
            return node != null && has(node, name, descriptor);
        } catch (Exception | LinkageError ignored) {
            return false;
        }
    }

    private static boolean has(ClassNode node, String name, String descriptor) {
        return node.methods.stream().anyMatch(method -> method.name.equals(name) && method.desc.equals(descriptor));
    }

    private static boolean invokes(ClassNode node, String methodName, String invokedName, int count) {
        for (MethodNode method : node.methods) {
            if (!method.name.equals(methodName)) continue;
            int found = 0;
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call
                        && call.owner.equals(node.name) && call.name.equals(invokedName)) found++;
            }
            return found == count;
        }
        return false;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
    private static boolean revengeAddonCompatible(String target, String mixin) {
        ClassNode node;
        try {
            node = MixinService.getService().getBytecodeProvider().getClassNode(target);
        } catch (Exception | LinkageError absent) { return false; }
        if (node == null) return false;
        String name = mixin.substring(mixin.lastIndexOf('.') + 1);
        String maidVoid = "(" + MAID + ")V";
        String maidBool = "(" + MAID + ")Z";
        boolean ok = switch (name) {
            case "CuteAnimationEligibilityMixin" -> has(node, "isExcluded", maidBool);
            case "CuteExtraBehaviorMixin" -> has(node, "onMaidTick", "(" + TICK + ")V");
            case "MoreAnimationDataMixin" -> has(node, "start", "(" + MAID + "Ljava/lang/String;IIZ)Z")
                    && has(node, "clientStart", "(" + MAID + "Ljava/lang/String;IIZ)V")
                    && has(node, "clientStartAt", "(" + MAID + "Ljava/lang/String;JII)V")
                    && has(node, "clearLocal", maidVoid) && has(node, "clearTransientExpression", maidVoid)
                    && List.of("serverTick", "freeze").stream().allMatch(n -> has(node, n, maidVoid))
                    && List.of("shouldForceHuman", "shouldForceFox", "injuredAuto", "autoPet", "autoHug", "randomSleepPose",
                            "isActive", "isTailInteractionActive", "isFaceInteractionActive")
                        .stream().allMatch(n -> has(node, n, maidBool))
                    && List.of("activeAction", "effectiveExpression").stream().allMatch(n -> has(node, n, "(" + MAID + ")Ljava/lang/String;"))
                    && has(node, "enabledActions", "(" + MAID + "Ljava/lang/String;)Ljava/util/List;");
            case "MoreAnimationDecisionMixin" -> has(node, "clear", maidVoid) && has(node, "serverTick", maidVoid)
                    && has(node, "canClaim", "(" + MAID + "Ljava/lang/String;)Z");
            case "MoreAnimationInteractionMixin" -> has(node, "requestInteraction",
                    "(" + MAID + "Ljava/lang/String;Lnet/minecraft/world/entity/Entity;)Z")
                    && has(node, "onDeath", "(Lnet/neoforged/neoforge/event/entity/living/LivingDeathEvent;)V");
            case "MoreAnimationInjuryMixin" -> has(node, "begin", maidVoid) && has(node, "finish", maidVoid)
                    && has(node, "serverTick", maidVoid);
            case "MoreAnimationManualMixin" -> List.of("triggerEarPull", "triggerTailPull", "begin").stream()
                    .anyMatch(n -> has(node, n, "(Lnet/minecraft/server/level/ServerPlayer;" + MAID + ")V"));
            case "MoreAnimationFaceMixin" -> has(node, "begin", "(Lnet/minecraft/server/level/ServerPlayer;" + MAID + "Z)V");
            case "MoreAnimationStandingMixin" -> has(node, "begin", "(Lnet/minecraft/server/level/ServerPlayer;" + MAID + ")Z");
            case "MoreAnimationPrayMixin" -> has(node, "loadedMaids", "(Lnet/minecraft/server/level/ServerLevel;)Ljava/util/List;")
                    && has(node, "freezeAndFace", "(" + MAID + "Lnet/minecraft/core/BlockPos;)V");
            case "AddonYsmOverlayMixin" -> has(node, "after", "(Ljava/lang/Object;F)V")
                    && node.methods.stream().filter(m -> m.name.equals("after")).anyMatch(m -> {
                        for (var insn : m.instructions) {
                            if (insn instanceof MethodInsnNode call && call.owner.equals("java/lang/reflect/Method")
                                    && call.name.equals("invoke")) {
                                // Only the reviewed entity-extraction shape; don't hook an unrelated reflection call.
                                var next = insn.getNext();
                                while (next != null && next.getOpcode() < 0) next = next.getNext();
                                if (next instanceof org.objectweb.asm.tree.VarInsnNode store
                                        && store.getOpcode() == org.objectweb.asm.Opcodes.ASTORE) {
                                    next = next.getNext();
                                    while (next != null && next.getOpcode() < 0) next = next.getNext();
                                    if (!(next instanceof org.objectweb.asm.tree.VarInsnNode load)
                                            || load.getOpcode() != org.objectweb.asm.Opcodes.ALOAD || load.var != store.var)
                                        return false;
                                    next = next.getNext();
                                    while (next != null && next.getOpcode() < 0) next = next.getNext();
                                }
                                return next instanceof org.objectweb.asm.tree.TypeInsnNode type
                                        && type.getOpcode() == org.objectweb.asm.Opcodes.INSTANCEOF
                                        && type.desc.equals("com/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid");
                            }
                        }
                        return false;
                    });
            default -> false;
        };
        if (!ok) LOGGER.warn("Optional revenge-maid isolation skipped: {} has incompatible signature for {}", target, name);
        return ok;
    }
}
