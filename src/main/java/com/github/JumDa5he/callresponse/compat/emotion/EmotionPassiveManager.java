package com.github.JumDa5he.callresponse.compat.emotion;

import com.github.JumDa5he.callresponse.compat.api.event.emotion.MaidEmotionEvent;
import com.github.JumDa5he.callresponse.compat.broadcast.MaidResponder;
import com.github.JumDa5he.callresponse.compat.talk.TalkEventManager;
import com.github.JumDa5he.callresponse.config.EmotionPassiveConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EmotionPassiveManager {

    // ===== 配置参数 =====
    private static final int NATURAL_GROWTH_INTERVAL = 12000;   // 10 分钟
    private static final int WORK_CHECK_INTERVAL = 4800;        // 4 分钟
    private static final int SLEEP_COOLDOWN = 12000;            // 10 分钟冷却
    private static final int OWNER_INTERACTION_CHECK = 3600;    // 3 分钟
    private static final int ARMOR_FULL_COOLDOWN = 12000;       // 10 分钟冷却
    private static final int POSITIVE_EFFECT_COOLDOWN = 3600;   // 3 分钟冷却
    private static final int DEATH_RADIUS = 16;                 // 生物死亡检测半径

    // ===== 新增注视/拴绳/亮度/水中参数 =====
    private static final int FOOD_GAZE_DURATION = 100;          // 5 秒
    private static final int WEAPON_GAZE_DURATION = 100;        // 5 秒
    private static final int GAZE_COOLDOWN = 1200;              // 1 分钟冷却
    private static final int LEASH_FISHING_INTERVAL = 2400;      // 120 秒
    private static final int LEASH_WITNESS_INTERVAL = 2400;      // 120 秒
    private static final int LEASH_DIALOGUE_COOLDOWN = 3600;    // 3 分钟冷却
    private static final int SLOT_CHANGE_COOLDOWN = 6000;       // 5 分钟冷却
    private static final int DARKNESS_THRESHOLD = 6;            // 亮度低于此值视为黑暗
    private static final int DARKNESS_ACCUMULATE_TICKS = 1200;  // 累计 1 分钟触发
    private static final int DARKNESS_INTERVAL = 600;           // 触发后 30 秒冷却
    private static final int WATER_INTERVAL = 6000;             // 5 分钟
    private static final int FIREWORK_RADIUS = 30;              // 烟花检测半径
    private static final int FIREWORK_COOLDOWN = 1200;          // 1 分钟冷却
    private static final int COMPANION_INTERVAL = 2400;         // 2 分钟（120秒）
    private static final int COMPANION_COOLDOWN = 12000;        // 10 分钟冷却
    private static final int COMPANION_RADIUS = 8;              // 同伴检测半径

    // ===== 对话冷却 =====
    private static final Map<UUID, Long> lastWitnessDialogueTime = new ConcurrentHashMap<>();
    private static final int WITNESS_DIALOGUE_COOLDOWN = 600;   // 30 秒冷却

    // ===== 状态存储 =====
    private static final Map<UUID, Long> lastNaturalGrowth = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastWorkCheck = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastSleepTrigger = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> lastSleepState = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastInteractionCheck = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> lastInteractionFlag = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastArmorFullTrigger = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastPositiveEffectTrigger = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> wasArmorFull = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> lastSitState = new ConcurrentHashMap<>();

    // ===== 新增状态存储 =====
    private static final Map<UUID, Integer> foodGazeTimer = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> weaponGazeTimer = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastGazeTrigger = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastLeashTrigger = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastLeashWitnessTrigger = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastLeashDialogue = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastDarknessTrigger = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> darknessTimer = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastWaterTrigger = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> waterTimer = new ConcurrentHashMap<>();
    private static final Map<UUID, ItemStack> lastSlotSixStack = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastSlotChange = new ConcurrentHashMap<>();

    private static final Map<UUID, Long> lastFireworkTrigger = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> companionTimer = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastCompanionTrigger = new ConcurrentHashMap<>();
    private static final Map<UUID, ResourceLocation> lastDimension = new ConcurrentHashMap<>();

    // ===== 维度名称转中文 =====
    private static String getDimensionDisplayName(ResourceLocation dimId) {
        String ns = dimId.getNamespace();
        String path = dimId.getPath();
        return switch (dimId.toString()) {
            case "minecraft:overworld" -> "主世界";
            case "minecraft:the_nether" -> "下界";
            case "minecraft:the_end" -> "末地";
            default -> {
                // mod维度：转为可读名称
                String readable = path.replace('_', ' ').replace('/', ' ');
                // 首字母大写
                StringBuilder sb = new StringBuilder();
                boolean cap = true;
                for (char c : readable.toCharArray()) {
                    if (c == ' ') {
                        cap = true;
                        sb.append(c);
                    } else if (cap) {
                        sb.append(Character.toUpperCase(c));
                        cap = false;
                    } else {
                        sb.append(c);
                    }
                }
                yield sb + "（" + ns + "）";
            }
        };
    }

    // ===== 辅助方法 =====
    private static boolean isOwnerLookingAtMaid(ServerPlayer owner, EntityMaid maid) {
        Vec3 lookVec = owner.getLookAngle();
        Vec3 eyePos = owner.getEyePosition();
        Vec3 toMaid = maid.getEyePosition().subtract(eyePos);
        double distance = toMaid.length();
        if (distance > 8.0) return false;
        Vec3 normalized = toMaid.normalize();
        double dot = lookVec.dot(normalized);
        return dot > 0.85;
    }

    private static boolean isFoodBlock(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem() == Items.CAKE;
    }

    private static boolean isWeaponItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item instanceof SwordItem ||
                item instanceof AxeItem ||
                item instanceof BowItem ||
                item instanceof CrossbowItem ||
                item instanceof TridentItem;
    }

    private static UUID getOwnerUUID(EntityMaid maid) {
        LivingEntity owner = maid.getOwner();
        return owner != null ? owner.getUUID() : null;
    }

    private static boolean isArmorFull(EntityMaid maid) {
        return !maid.getItemBySlot(EquipmentSlot.HEAD).isEmpty() &&
                !maid.getItemBySlot(EquipmentSlot.CHEST).isEmpty() &&
                !maid.getItemBySlot(EquipmentSlot.LEGS).isEmpty() &&
                !maid.getItemBySlot(EquipmentSlot.FEET).isEmpty();
    }

    private static boolean hasPositiveEffect(EntityMaid maid) {
        for (MobEffectInstance effect : maid.getActiveEffects()) {
            if (effect.getEffect().value().getCategory() == MobEffectCategory.BENEFICIAL) {
                return true;
            }
        }
        return false;
    }
    // ===== 检测女仆是否接触水（碰撞箱与水方块相交） =====
    private static boolean isMaidInWater(EntityMaid maid) {
        // 检查女仆脚下的方块是否是水
        BlockPos footPos = maid.blockPosition();
        if (maid.level().getBlockState(footPos).getFluidState().is(FluidTags.WATER)) {
            return true;
        }
        // 检查女仆身体所在的方块（多检测几个位置）
        for (int y = 0; y < 2; y++) {
            BlockPos checkPos = footPos.above(y);
            if (maid.level().getBlockState(checkPos).getFluidState().is(FluidTags.WATER)) {
                return true;
            }
        }
        // 检查女仆的碰撞箱是否与水方块相交
        AABB box = maid.getBoundingBox();
        BlockPos minPos = new BlockPos((int) Math.floor(box.minX), (int) Math.floor(box.minY), (int) Math.floor(box.minZ));
        BlockPos maxPos = new BlockPos((int) Math.floor(box.maxX), (int) Math.floor(box.maxY), (int) Math.floor(box.maxZ));
        for (int x = minPos.getX(); x <= maxPos.getX(); x++) {
            for (int y = minPos.getY(); y <= maxPos.getY(); y++) {
                for (int z = minPos.getZ(); z <= maxPos.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (maid.level().getBlockState(pos).getFluidState().is(FluidTags.WATER)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ===== 定时检查 =====
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            player.level().getEntitiesOfClass(EntityMaid.class,
                            player.getBoundingBox().inflate(32))
                    .forEach(maid -> {
                        if (!maid.isTame() || maid.getOwner() == null) return;
                        if (EmotionBetrayalManager.isBetraying(maid)) return;

                        long tick = maid.level().getGameTime();
                        UUID maidId = maid.getUUID();
                        UUID ownerId = getOwnerUUID(maid);
                        if (ownerId == null) return;

                        // ===== 睡眠检测 =====
                        boolean isSleeping = maid.isSleeping();
                        Boolean lastSleep = lastSleepState.get(maidId);
                        if (lastSleep == null) lastSleep = false;
                        boolean canceled = NeoForge.EVENT_BUS.post(new MaidEmotionEvent.MaidPassiveEvent(maid, MaidEmotionEvent.MaidPassiveEvent.Type.SLEEP)).isCanceled();
                        if (!canceled && !lastSleep && isSleeping) {
                            Long lastTrigger = lastSleepTrigger.get(maidId);
                            if (lastTrigger == null || tick - lastTrigger >= SLEEP_COOLDOWN) {
                                EmotionData.addTrust(maid, ownerId, 1);
                                lastSleepTrigger.put(maidId, tick);
                                if (maid.getOwner() instanceof ServerPlayer owner) {
                                    MaidResponder.debug(owner,
                                            Component.literal("§e[被动] ")
                                                    .append(maid.getName())
                                                    .append(Component.literal(" 睡觉：信任+1"))
                                    );
                                }
                            }
                        }
                        lastSleepState.put(maidId, isSleeping);

                        // ===== 自然增长 =====
                        Long lastNatural = lastNaturalGrowth.get(maidId);
                        canceled = NeoForge.EVENT_BUS.post(new MaidEmotionEvent.MaidPassiveEvent(maid, MaidEmotionEvent.MaidPassiveEvent.Type.NATURAL_GROWTH)).isCanceled();
                        if (!canceled && lastNatural == null || tick - lastNatural >= NATURAL_GROWTH_INTERVAL) {
                            EmotionData.addTrust(maid, ownerId, 1);
                            EmotionData.addFear(maid, ownerId, -1);
                            lastNaturalGrowth.put(maidId, tick);
                            if (maid.getOwner() instanceof ServerPlayer owner) {
                                MaidResponder.debug(owner,
                                        Component.literal("§e[被动] ")
                                                .append(maid.getName())
                                                .append(Component.literal(" 自然增长：信任+1，恐惧-1"))
                                );
                            }
                        }

                        // ===== 工作/休息检查 =====
                        Long lastWork = lastWorkCheck.get(maidId);
                        if (lastWork == null || tick - lastWork >= WORK_CHECK_INTERVAL) {
                            boolean isWorking = maid.getScheduleDetail() == Activity.WORK;
                            canceled = NeoForge.EVENT_BUS.post(new MaidEmotionEvent.MaidPassiveEvent(maid, MaidEmotionEvent.MaidPassiveEvent.Type.WORK)).isCanceled();
                            if (!canceled && isWorking) {
                                EmotionData.addFear(maid, ownerId, -1);
                                if (maid.getOwner() instanceof ServerPlayer owner) {
                                    MaidResponder.debug(owner,
                                            Component.literal("§e[被动] ")
                                                    .append(maid.getName())
                                                    .append(Component.literal(" 工作中：恐惧-1"))
                                    );
                                }
                            } else if(!NeoForge.EVENT_BUS.post(new MaidEmotionEvent.MaidPassiveEvent(maid, MaidEmotionEvent.MaidPassiveEvent.Type.BREAK)).isCanceled()) {
                                EmotionData.addTrust(maid, ownerId, 1);
                                if (maid.getOwner() instanceof ServerPlayer owner) {
                                    MaidResponder.debug(owner,
                                            Component.literal("§e[被动] ")
                                                    .append(maid.getName())
                                                    .append(Component.literal(" 休息中：信任+1"))
                                    );
                                }
                            }
                            lastWorkCheck.put(maidId, tick);
                        }

                        // ===== 主人无交互检测 =====
                        Long lastInteraction = lastInteractionCheck.get(maidId);
                        canceled = NeoForge.EVENT_BUS.post(new MaidEmotionEvent.MaidPassiveEvent(maid, MaidEmotionEvent.MaidPassiveEvent.Type.NO_INTERACTION)).isCanceled();
                        if (!canceled && (lastInteraction == null || tick - lastInteraction >= OWNER_INTERACTION_CHECK)) {
                            Boolean hasInteracted = lastInteractionFlag.getOrDefault(maidId, false);
                            if (!hasInteracted) {
                                EmotionData.addTrust(maid, ownerId, -1);
                                EmotionData.addFear(maid, ownerId, -1);
                                if (maid.getOwner() instanceof ServerPlayer owner) {
                                    MaidResponder.debug(owner,
                                            Component.literal("§e[被动] ")
                                                    .append(maid.getName())
                                                    .append(Component.literal(" 主人3分钟无交互：信任-1，恐惧-1"))
                                    );
                                }
                            }
                            lastInteractionFlag.put(maidId, false);
                            lastInteractionCheck.put(maidId, tick);
                        }

                        // ===== 盔甲满检测 =====
                        boolean isArmorFull = isArmorFull(maid);
                        Boolean wasFull = wasArmorFull.get(maidId);
                        if (wasFull == null) wasFull = false;
                        canceled = NeoForge.EVENT_BUS.post(new MaidEmotionEvent.MaidPassiveEvent(maid, MaidEmotionEvent.MaidPassiveEvent.Type.ARMOR_FULL)).isCanceled();
                        if (!canceled && isArmorFull && !wasFull) {
                            Long lastArmor = lastArmorFullTrigger.get(maidId);
                            if (lastArmor == null || tick - lastArmor >= ARMOR_FULL_COOLDOWN) {
                                EmotionData.addTrust(maid, ownerId, 3);
                                EmotionData.addFear(maid, ownerId, -1);
                                lastArmorFullTrigger.put(maidId, tick);
                                if (maid.getOwner() instanceof ServerPlayer owner) {
                                    MaidResponder.debug(owner,
                                            Component.literal("§e[被动] ")
                                                    .append(maid.getName())
                                                    .append(Component.literal(" 盔甲穿满：信任+3，恐惧-1"))
                                    );
                                }
                            }
                        }
                        wasArmorFull.put(maidId, isArmorFull);

                        // ===== 正面效果检测 =====
                        canceled = NeoForge.EVENT_BUS.post(new MaidEmotionEvent.MaidPassiveEvent(maid, MaidEmotionEvent.MaidPassiveEvent.Type.POSITIVE_EFFECT)).isCanceled();
                        if (!canceled && hasPositiveEffect(maid)) {
                            Long lastEffect = lastPositiveEffectTrigger.get(maidId);
                            if (lastEffect == null || tick - lastEffect >= POSITIVE_EFFECT_COOLDOWN) {
                                EmotionData.addTrust(maid, ownerId, 1);
                                lastPositiveEffectTrigger.put(maidId, tick);
                                if (maid.getOwner() instanceof ServerPlayer owner) {
                                    MaidResponder.debug(owner,
                                            Component.literal("§e[被动] ")
                                                    .append(maid.getName())
                                                    .append(Component.literal(" 获得正面效果：信任+1"))
                                    );
                                }
                            }
                        }

                        // ===== 注视检测（食物/武器） =====
                        // 在注视检测的开头，添加以下调试输出（每 20 tick 输出一次，避免刷屏）
                        if (maid.getOwner() instanceof ServerPlayer serverOwner) {
                            boolean isLooking = isOwnerLookingAtMaid(serverOwner, maid);
                            if (isLooking) {
                                ItemStack mainHand = serverOwner.getMainHandItem();
                                boolean isFood = mainHand.getFoodProperties(maid) != null || isFoodBlock(mainHand);
                                boolean isWeapon = isWeaponItem(mainHand);

                                if (isFood) {
                                    int timer = foodGazeTimer.getOrDefault(maidId, 0) + 1;
                                    foodGazeTimer.put(maidId, timer);
                                    if (timer >= FOOD_GAZE_DURATION) {
                                        Long lastTrigger = lastGazeTrigger.get(maidId);
                                        canceled = NeoForge.EVENT_BUS.post(new MaidEmotionEvent.MaidPassiveEvent(maid, MaidEmotionEvent.MaidPassiveEvent.Type.SEEING_FOOD)).isCanceled();
                                        if (!canceled && (lastTrigger == null || tick - lastTrigger >= GAZE_COOLDOWN)) {
                                            EmotionData.addTrust(maid, ownerId, -1);
                                            lastGazeTrigger.put(maidId, tick);
                                            foodGazeTimer.put(maidId, 0);
                                            if (maid.getOwner() instanceof ServerPlayer owner) {
                                                MaidResponder.debug(owner,
                                                        Component.literal("§e[被动] ")
                                                                .append(maid.getName())
                                                                .append(Component.literal(" 主人拿食物看着女仆：信任+1"))
                                                );
                                                String tendencyDesc = EmotionData.getTendencyPromptSuffix(maid, ownerId);
                                                String prompt = "主人手上拿着食物，一直看着你。" + tendencyDesc + " 根据你当前的情感状态，说一段20字左右的话表达你的反应";
                                                MaidResponder.processBroadcast(owner, Collections.singletonList(maid), prompt, false);
                                            }
                                        }
                                    }
                                } else if (isWeapon) {
                                    int timer = weaponGazeTimer.getOrDefault(maidId, 0) + 1;
                                    weaponGazeTimer.put(maidId, timer);
                                    if (timer >= WEAPON_GAZE_DURATION) {
                                        Long lastTrigger = lastGazeTrigger.get(maidId);
                                        canceled = NeoForge.EVENT_BUS.post(new MaidEmotionEvent.MaidPassiveEvent(maid, MaidEmotionEvent.MaidPassiveEvent.Type.SEEING_WEAPON)).isCanceled();
                                        if (!canceled && (lastTrigger == null || tick - lastTrigger >= GAZE_COOLDOWN)) {
                                            EmotionData.addFear(maid, ownerId, 1);
                                            lastGazeTrigger.put(maidId, tick);
                                            weaponGazeTimer.put(maidId, 0);
                                            if (maid.getOwner() instanceof ServerPlayer owner) {
                                                MaidResponder.debug(owner,
                                                        Component.literal("§c[被动] ")
                                                                .append(maid.getName())
                                                                .append(Component.literal(" 被主人盯着武器看：恐惧+1"))
                                                );
                                                String tendencyDesc = EmotionData.getTendencyPromptSuffix(maid, ownerId);
                                                String prompt = "主人拿着武器盯着你，不知道他是什么意思。" + tendencyDesc + " 根据你当前的情感状态，说一段20字左右的话表达你的不安。";
                                                MaidResponder.processBroadcast(owner, Collections.singletonList(maid), prompt, false);
                                            }
                                        }
                                    }
                                } else {
                                    weaponGazeTimer.put(maidId, 0);
                                    foodGazeTimer.put(maidId, 0);
                                }
                            } else {
                                weaponGazeTimer.put(maidId, 0);
                                foodGazeTimer.put(maidId, 0);
                            }
                        }
                        // ===== 物品栏第6格（索引5）检测 =====
                        ItemStack slotSix = maid.getMaidInv().getStackInSlot(5);
                        ItemStack lastSlotSix = lastSlotSixStack.getOrDefault(maidId, ItemStack.EMPTY);

                        boolean slotChanged = false;
                        if (lastSlotSix.isEmpty() && !slotSix.isEmpty()) {
                            slotChanged = true;
                        } else if (!lastSlotSix.isEmpty() && !slotSix.isEmpty() && !ItemStack.isSameItem(lastSlotSix, slotSix)) {
                            slotChanged = true;
                        }

                        canceled = NeoForge.EVENT_BUS.post(new MaidEmotionEvent.MaidPassiveEvent(maid, MaidEmotionEvent.MaidPassiveEvent.Type.HEAD_ITEM)).isCanceled();
                        if (!canceled && slotChanged) {
                            Long lastChange = lastSlotChange.get(maidId);
                            if (lastChange == null || tick - lastChange >= SLOT_CHANGE_COOLDOWN) {
                                // 信任 +1，恐惧 -2
                                EmotionData.addTrust(maid, ownerId, 1);
                                EmotionData.addFear(maid, ownerId, -2);
                                lastSlotChange.put(maidId, tick);

                                if (maid.getOwner() instanceof ServerPlayer owner) {
                                    String itemName = slotSix.getDisplayName().getString();
                                    MaidResponder.debug(owner,
                                            Component.literal("§e[被动] ")
                                                    .append(maid.getName())
                                                    .append(Component.literal(" 第6格放入 "))
                                                    .append(Component.literal(itemName)) // itemName 为 String
                                                    .append(Component.literal("：信任+1，恐惧-2"))
                                    );
                                    // 触发 AI 对话
                                    String tendencyDesc = EmotionData.getTendencyPromptSuffix(maid, ownerId);
                                    String prompt = "主人在你头上第6格放了个 " + itemName + "。" + tendencyDesc + " 根据你当前的情感状态，对主人给你的这个礼物（或是随便放的东西）说一句话表达你的心情。";
                                    MaidResponder.processBroadcast(owner, Collections.singletonList(maid), prompt, false);
                                }
                            }
                        }
                        lastSlotSixStack.put(maidId, slotSix.copy());

                        // ===== 拴绳/鱼钩检测 =====
                        boolean hasFishingHook = maid.level().getEntitiesOfClass(
                                FishingHook.class, maid.getBoundingBox().inflate(2))
                                .stream()
                                .anyMatch(hook -> hook.getHookedIn() == maid && hook.getOwner() != null && hook.getOwner().equals(maid.getOwner()));
                        canceled = NeoForge.EVENT_BUS.post(new MaidEmotionEvent.MaidPassiveEvent(maid, MaidEmotionEvent.MaidPassiveEvent.Type.LEASHING)).isCanceled();
                        if (!canceled && (maid.isLeashed() || hasFishingHook)) {
                            Long lastLeash = lastLeashTrigger.get(maidId);
                            if (lastLeash == null || tick - lastLeash >= LEASH_FISHING_INTERVAL) {
                                EmotionData.addTrust(maid, ownerId, -1);
                                lastLeashTrigger.put(maidId, tick);
                                if (maid.getOwner() instanceof ServerPlayer owner) {
                                    MaidResponder.debug(owner,
                                            Component.literal("§e[被动] ")
                                                    .append(maid.getName())
                                                    .append(Component.literal(" 被拴绳/鱼钩拴住：信任-2"))
                                    );
                                }
                                Long lastDialogue = lastLeashDialogue.get(maidId);
                                if (lastDialogue == null || tick - lastDialogue >= LEASH_DIALOGUE_COOLDOWN) {
                                    String tendencyDesc = EmotionData.getTendencyPromptSuffix(maid, ownerId);
                                    String prompt = "你被主人像动物一样拴着或吊着，动弹不得。" + tendencyDesc + " 根据你当前的情感状态，说一段30字左右的话表达你的心情——是觉得屈辱、难过，还是觉得只要主人在身边怎样都好？";
                                    if (maid.getOwner() instanceof ServerPlayer owner) {
                                        MaidResponder.processBroadcast(owner, Collections.singletonList(maid), prompt, false);
                                    }
                                    lastLeashDialogue.put(maidId, tick);
                                }
                            }

                            // 旁观女仆恐惧+1
                            maid.level().getEntitiesOfClass(EntityMaid.class, maid.getBoundingBox().inflate(8))
                                    .forEach(witness -> {
                                        if (witness == maid || !witness.isTame() || witness.getOwner() == null) return;
                                        if (EmotionBetrayalManager.isBetraying(witness)) return;
                                        UUID witnessOwnerId = getOwnerUUID(witness);
                                        if (witnessOwnerId == null) return;
                                        Long lastWitness = lastLeashWitnessTrigger.get(witness.getUUID());
                                        var c = NeoForge.EVENT_BUS.post(new MaidEmotionEvent.MaidPassiveEvent(witness, MaidEmotionEvent.MaidPassiveEvent.Type.SEEING_LEASHING)).isCanceled();
                                        if (!c && (lastWitness == null || tick - lastWitness >= LEASH_WITNESS_INTERVAL)) {
                                            EmotionData.addFear(witness, witnessOwnerId, 1);
                                            lastLeashWitnessTrigger.put(witness.getUUID(), tick);
                                            if (witness.getOwner() instanceof ServerPlayer owner) {
                                                MaidResponder.debug(owner,
                                                        Component.literal("§c[被动] ")
                                                                .append(witness.getName())
                                                                .append(Component.literal(" 目睹同伴被拴住：恐惧+2"))
                                                );
                                            }
                                            Long witnessDialogue = lastWitnessDialogueTime.get(witness.getUUID());
                                            if (witnessDialogue == null || tick - witnessDialogue >= LEASH_DIALOGUE_COOLDOWN) {
                                                String tendencyDesc = EmotionData.getTendencyPromptSuffix(witness, witnessOwnerId);
                                                String prompt = "你看到同伴被主人用拴绳或鱼钩拴着。" + tendencyDesc + " 根据你当前的情感状态，说一段30字左右的话表达你的心情——你的同伴正在受苦，你感到不安，愤恨，还是无动于衷？";
                                                if (witness.getOwner() instanceof ServerPlayer owner) {
                                                    MaidResponder.processBroadcast(owner, Collections.singletonList(witness), prompt, false);
                                                }
                                                lastWitnessDialogueTime.put(witness.getUUID(), tick);
                                            }
                                        }
                                    });
                        } else {
                            lastLeashTrigger.remove(maidId);
                        }

                        // ===== 亮度检测 =====
                        int rawBrightness = maid.level().getRawBrightness(maid.blockPosition(), 0);
                        if (rawBrightness < DARKNESS_THRESHOLD) {
                            int darkTime = darknessTimer.getOrDefault(maidId, 0) + 1;
                            darknessTimer.put(maidId, darkTime);
                            if (darkTime >= DARKNESS_ACCUMULATE_TICKS) {
                                Long lastDarkness = lastDarknessTrigger.get(maidId);
                                canceled = NeoForge.EVENT_BUS.post(new MaidEmotionEvent.MaidPassiveEvent(maid, MaidEmotionEvent.MaidPassiveEvent.Type.BRIGHT)).isCanceled();
                                if (!canceled && (lastDarkness == null || tick - lastDarkness >= DARKNESS_INTERVAL)) {
                                    EmotionData.addFear(maid, ownerId, 1);
                                    lastDarknessTrigger.put(maidId, tick);
                                    darknessTimer.put(maidId, 0);
                                    if (maid.getOwner() instanceof ServerPlayer owner) {
                                        MaidResponder.debug(owner,
                                                Component.literal("§c[被动] ")
                                                        .append(maid.getName())
                                                        .append(Component.literal(" 身处黑暗中：恐惧+1"))
                                        );                                        String tendencyDesc = EmotionData.getTendencyPromptSuffix(maid, ownerId);
                                        String prompt = "周围太暗了，你几乎什么都看不见。" + tendencyDesc + " 根据你当前的情感状态，说一句话表达你的不安——你是害怕黑暗中的未知，还是渴望主人来保护你？";
                                        MaidResponder.processBroadcast(owner, Collections.singletonList(maid), prompt, false);
                                    }
                                }
                            }
                        } else {
                            int darkTime = darknessTimer.getOrDefault(maidId, 0);
                            if (darkTime > 0) {
                                darknessTimer.put(maidId, Math.max(0, darkTime - 2));
                            }
                        }

                        // ===== 水中检测 =====
                        // ===== 检测女仆是否接触水（碰撞箱与水方块相交） =====
                        boolean isTouchingWater = isMaidInWater(maid);

                        if (isTouchingWater) {
                            int waterTime = waterTimer.getOrDefault(maidId, 0) + 1;
                            waterTimer.put(maidId, waterTime);
                            if (waterTime >= WATER_INTERVAL) {
                                Long lastWater = lastWaterTrigger.get(maidId);
                                canceled = NeoForge.EVENT_BUS.post(new MaidEmotionEvent.MaidPassiveEvent(maid, MaidEmotionEvent.MaidPassiveEvent.Type.WATER)).isCanceled();
                                if (!canceled && (lastWater == null || tick - lastWater >= WATER_INTERVAL)) {
                                    EmotionData.addTrust(maid, ownerId, -1);
                                    lastWaterTrigger.put(maidId, tick);
                                    waterTimer.put(maidId, 0);
                                    if (maid.getOwner() instanceof ServerPlayer owner) {
                                        MaidResponder.debug(owner,
                                                Component.literal("§e[被动] ")
                                                        .append(maid.getName())
                                                        .append(Component.literal(" 泡在水中过久：信任-1"))
                                        );                                        String tendencyDesc = EmotionData.getTendencyPromptSuffix(maid, ownerId);
                                        String prompt = "你在水里泡了很久，浑身湿透了，主人却没有注意到。" + tendencyDesc + " 根据你当前的情感状态，说一段30字左右的话表达你的感受——是委屈、凉透心，还是觉得主人迟早会来的？";
                                        MaidResponder.processBroadcast(owner, Collections.singletonList(maid), prompt, false);
                                    }
                                }
                            }
                        } else {
                            waterTimer.remove(maidId);
                        }

                        // ===== 烟花检测（融入情感状态） =====
                        {
                            Long lastFirework = lastFireworkTrigger.get(maidId);
                            if (lastFirework == null || tick - lastFirework >= FIREWORK_COOLDOWN) {
                                boolean hasFirework = !maid.level().getEntitiesOfClass(
                                        FireworkRocketEntity.class, maid.getBoundingBox().inflate(FIREWORK_RADIUS))
                                        .isEmpty();
                                canceled = NeoForge.EVENT_BUS.post(new MaidEmotionEvent.MaidPassiveEvent(maid, MaidEmotionEvent.MaidPassiveEvent.Type.FIREWORK)).isCanceled();
                                if (!canceled && hasFirework) {
                                    EmotionData.addTrust(maid, ownerId, 1);
                                    lastFireworkTrigger.put(maidId, tick);
                                    if (maid.getOwner() instanceof ServerPlayer owner) {
                                        MaidResponder.debug(owner,
                                                Component.literal("§e[被动] ")
                                                        .append(maid.getName())
                                                        .append(Component.literal(" 看到烟花：信任+1"))
                                        );                                        EmotionData.EmotionTendency tendency = EmotionData.getTendency(maid, ownerId);
                                        EmotionData.EmotionValues v = EmotionData.get(maid, ownerId);
                                        String prompt = switch (tendency) {
                                            case BOND -> "夜空中绽放着绚丽的烟花，五彩的光芒照亮了你的脸庞。你兴奋地转头看向主人，想和他分享这份美好。请对主人说一句20字左右的话，表达你看到烟花的喜悦和想和他一起看的心情。";
                                            case FRIENDLY -> "夜空中绽放着绚丽的烟花，五彩的光芒照亮了你的脸庞。你心情很好，想和主人说说话。请对主人说一句20字左右的话，分享你看到烟花的开心心情。";
                                            case STRANGER -> "夜空中绽放着绚丽的烟花，五彩的光芒照亮了你的脸庞。你和主人之间还有些生疏，但烟花让你想试着说点什么。请对主人礼貌地说一句20字左右的话，表达你对烟花的感受。";
                                            case FEARFUL -> "夜空中突然炸开一声巨响，烟花的光亮照得四周忽明忽暗。你被这突如其来的声音吓得缩了缩脖子。请用害怕的语气对主人说一句20字左右的话，表达你对这巨响的不安。";
                                            case TERRIFIED -> "夜空中突然炸开一声巨响，你吓得浑身一抖！那刺眼的光和轰鸣声让你想起了不好的事。请用颤抖的声音对主人说一句20字左右的话，表达你的恐惧和想要逃开的冲动。";
                                            case CONFLICTED -> "夜空中绽放着绚丽的烟花，漂亮得让你移不开眼，但每次炸响又让你心头一紧。请对主人说一句20字左右的话，表达你对烟花又喜欢又害怕的矛盾心情。";
                                            case NEUTRAL -> "夜空中绽放着绚丽的烟花，五彩的光芒照亮了你的脸庞。请对主人说一句20字左右的话，平静地描述你看到的烟花。";
                                        };
                                        MaidResponder.processBroadcast(owner, Collections.singletonList(maid), prompt, false);
                                    }
                                }
                            }
                        }

                        // ===== 同伴陪伴检测 =====
                        {
                            boolean hasCompanion = maid.level().getEntitiesOfClass(EntityMaid.class,
                                    maid.getBoundingBox().inflate(COMPANION_RADIUS))
                                    .stream().anyMatch(other -> other != maid && other.isTame()
                                            && other.getOwner() != null && other.getOwner().equals(maid.getOwner())
                                            && !EmotionBetrayalManager.isBetraying(other));
                            canceled = NeoForge.EVENT_BUS.post(new MaidEmotionEvent.MaidPassiveEvent(maid, MaidEmotionEvent.MaidPassiveEvent.Type.FRIENDS)).isCanceled();
                            if (!canceled && hasCompanion) {
                                int timerTicks = companionTimer.getOrDefault(maidId, 0) + 1;
                                companionTimer.put(maidId, timerTicks);
                                if (timerTicks >= COMPANION_INTERVAL) {
                                    Long lastCompanion = lastCompanionTrigger.get(maidId);
                                    if (lastCompanion == null || tick - lastCompanion >= COMPANION_COOLDOWN) {
                                        EmotionData.addFear(maid, ownerId, -1);
                                        lastCompanionTrigger.put(maidId, tick);
                                        companionTimer.put(maidId, 0);
                                        if (maid.getOwner() instanceof ServerPlayer owner) {
                                            MaidResponder.debug(owner,
                                                    Component.literal("§e[被动] ")
                                                            .append(maid.getName())
                                                            .append(Component.literal(" 同伴陪伴：恐惧-1"))
                                            );                                        }
                                    }
                                }
                            } else {
                                companionTimer.remove(maidId);
                            }
                        }

                        // ===== 维度切换检测 =====
                        {
                            ResourceLocation currentDim = maid.level().dimension().location();
                            ResourceLocation lastDim = lastDimension.get(maidId);
                            canceled = NeoForge.EVENT_BUS.post(new MaidEmotionEvent.MaidPassiveEvent(maid, MaidEmotionEvent.MaidPassiveEvent.Type.DIMENSION)).isCanceled();
                            if (!canceled && lastDim != null && !lastDim.equals(currentDim)) {
                                // 重置冷却
                                companionTimer.remove(maidId);
                                lastCompanionTrigger.remove(maidId);
                                if (maid.getOwner() instanceof ServerPlayer owner) {
                                    String dimName = getDimensionDisplayName(currentDim);
                                    EmotionData.EmotionTendency tendency = EmotionData.getTendency(maid, ownerId);
                                    String prompt = switch (tendency) {
                                        case BOND -> "你跟着主人一起来到了" + dimName + "，这里的景象完全不同于之前的世界。你虽然好奇地打量着周围，但只要有主人在身边就不怕。请对主人说一句20~30字的话，描述你看到的" + dimName + "环境，并表达只要有主人在就很安心的心情。";
                                        case FRIENDLY -> "你跟着主人一起来到了" + dimName + "，这个地方你从没见过，充满了新鲜感。请对主人说一句20~30字的话，好奇地询问或描述这个新世界的样子。";
                                        case STRANGER -> "你跟着主人一起来到了" + dimName + "，这个地方让你感到陌生。你保持着礼貌的距离观察四周，不太确定该说什么。请对主人客气地说一句20字左右的话，表达你对这个新地方的第一印象。";
                                        case FEARFUL -> "你跟着主人一起来到了" + dimName + "，四周的环境让你感到不安和紧张。你不自觉地靠近主人，想寻求一些安全感。请用带着担心的语气对主人说一句20~30字的话，描述你看到的" + dimName + "景象，表达你的不安。";
                                        case TERRIFIED -> "你被带到了" + dimName + "！这里阴森恐怖的气息让你浑身发抖，你想要逃离这个地方。请用颤抖的语气对主人说一句20~30字的话，表达你对这个可怕地方的恐惧和想要离开的哀求。";
                                        case CONFLICTED -> "你跟着主人来到了" + dimName + "，这个地方既让你好奇又让你感到害怕。你的内心在探索的欲望和退缩的本能之间摇摆。请对主人说一句20~30字的话，表达你对这个新世界纠结的心情。";
                                        case NEUTRAL -> "你跟着主人一起来到了" + dimName + "。你平静地观察着这个新环境，等待主人的指示。请对主人说一句20字左右的话，简单地描述你看到的环境。";
                                    };
                                    MaidResponder.processBroadcast(owner, Collections.singletonList(maid), prompt, false);
                                    MaidResponder.debug(owner,
                                            Component.literal("§b[被动] ")
                                                    .append(maid.getName())
                                                    .append(Component.literal(" 进入"))
                                                    .append(Component.literal(dimName)) // dimName 为 String
                                                    .append(Component.literal("，触发对话"))
                                    );
                                }
                            }
                            lastDimension.put(maidId, currentDim);
                        }

                    });
        }
    }

    // ===== 生物死亡监听 =====
    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide) return;

        LivingEntity killer = null;
        if (event.getSource().getEntity() instanceof LivingEntity) {
            killer = (LivingEntity) event.getSource().getEntity();
        }

        boolean isMaidDeath = dead instanceof EntityMaid;
        boolean isKilledByPlayer = killer instanceof Player;

        for (ServerPlayer player : dead.level().getServer().getPlayerList().getPlayers()) {
            boolean isKillerPlayer = killer != null && killer.equals(player);

            dead.level().getEntitiesOfClass(EntityMaid.class,
                            player.getBoundingBox().inflate(DEATH_RADIUS))
                    .forEach(maid -> {
                        if (!maid.isTame() || maid.getOwner() == null) return;
                        if (EmotionBetrayalManager.isBetraying(maid)) return;
                        if (TalkEventManager.isParticipant(maid)) return;
                        if (maid.distanceTo(dead) > DEATH_RADIUS) return;

                        UUID ownerId = getOwnerUUID(maid);
                        if (ownerId == null) return;

                        var canceled = NeoForge.EVENT_BUS.post(new MaidEmotionEvent.MaidPassiveEvent(maid, MaidEmotionEvent.MaidPassiveEvent.Type.OTHER_DEATH)).isCanceled();
                        if(canceled)return;

                        if (isMaidDeath) {
                            EmotionData.addFear(maid, ownerId, 8);
                            EmotionData.addTrust(maid, ownerId, -4);
                        } else {
                            EmotionData.addFearFloat(maid, ownerId, 0.1f);
                        }

                        if (isMaidDeath && isKilledByPlayer && isKillerPlayer) {
                            triggerWitnessDialogue(maid, player, dead);
                        }
                    });
        }
    }

    @SubscribeEvent
    public void onMaidHurt(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) return;
        if (maid.level().isClientSide) return;
        if (!maid.isTame() || maid.getOwner() == null) return;

        var canceled = NeoForge.EVENT_BUS.post(new MaidEmotionEvent.MaidPassiveEvent(maid, MaidEmotionEvent.MaidPassiveEvent.Type.HURT)).isCanceled();
        if(canceled)return;

        if (EmotionBetrayalManager.isBetraying(maid)) return;

        UUID ownerId = getOwnerUUID(maid);
        if (ownerId == null) return;

        // 小数累积：每 10 次受伤恐惧 +1、信任 -1（参考击杀事件 addFearFloat）
        EmotionData.addFearFloat(maid, ownerId, 0.1f);
        EmotionData.addTrustFloat(maid, ownerId, -0.1f);

        if (maid.getOwner() instanceof ServerPlayer owner) {
            MaidResponder.debug(owner,
                    Component.literal("§e[被动] ")
                            .append(maid.getName())
                            .append(Component.literal(" 受伤：恐惧+0.1，信任-0.1"))
            );
        }
    }

    // ===== 触发目睹主人杀女仆的 AI 对话 =====
    private static void triggerWitnessDialogue(EntityMaid maid, ServerPlayer owner, LivingEntity dead) {
        UUID maidId = maid.getUUID();
        long now = maid.level().getGameTime();

        Long lastTime = lastWitnessDialogueTime.get(maidId);
        if (lastTime != null && now - lastTime < WITNESS_DIALOGUE_COOLDOWN) {
            return;
        }

        EmotionData.EmotionValues values = EmotionData.get(maid, owner);
        int trust = values.trust();
        int fear = values.fear();

        String tendencyDesc = EmotionData.getTendencyPromptSuffix(maid, owner.getUUID());
        String prompt;
        if (trust <= 30 && fear >= 70) {
            prompt = "你目睹主人亲手杀死了一个女仆！那个女仆倒下的瞬间，你的血液都凝固了。" + tendencyDesc + " 你生怕自己也会成为下一个。请用充满颤抖和讨好的语气说一句话，表示你会更听话、更顺从，绝不会惹主人生气——卑微到尘土里的求生欲。";
        } else if (trust >= 70 && fear <= 30) {
            prompt = "你目睹主人杀死了一个女仆，但你的内心并无恐惧——你信任主人所做的一切。" + tendencyDesc + " 你相信主人一定有他的理由，也许那个女仆做错了什么。请用充满理解和忠诚的语气说一句话，表示你永远站在主人这一边，哪怕与世界为敌。";
        } else if (trust >= 70 && fear >= 70) {
            prompt = "你目睹主人杀死了一个女仆，你的心脏狂跳不止——一半是恐惧，一半是某种说不清的感觉。" + tendencyDesc + " 主人残忍而强大，你既害怕又想靠近。请用一句矛盾的话表达你复杂的心情，让主人感受到你混乱的内心。";
        } else {
            prompt = "你目睹主人杀死了一个女仆。" + tendencyDesc + " 你的心里五味杂陈，不知道该怎么面对刚刚发生的事。请用一句话表达你此刻复杂的心情，也许是对主人的询问，也许是沉默的接受。";
        }

        MaidResponder.processBroadcast(owner, Collections.singletonList(maid), prompt, false);
        lastWitnessDialogueTime.put(maidId, now);

        MaidResponder.debug(owner,
                Component.literal("§c[被动] ")
                        .append(maid.getName())
                        .append(Component.literal(" 目睹主人杀死女仆，触发对话"))
        );    }

    // ===== 玩家交互标记（NeoForge 禁止注册抽象事件） =====
    private void markPlayerInteraction(Player player) {
        if (player.level().isClientSide) return;
        player.level().getEntitiesOfClass(EntityMaid.class,
                        player.getBoundingBox().inflate(32))
                .forEach(maid -> {
                    if (!maid.isTame() || maid.getOwner() == null) return;
                    if (!maid.getOwner().equals(player)) return;
                    if (EmotionBetrayalManager.isBetraying(maid)) return;
                    lastInteractionFlag.put(maid.getUUID(), true);
                });
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        markPlayerInteraction(event.getEntity());
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        markPlayerInteraction(event.getEntity());
    }

    @SubscribeEvent
    public void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        markPlayerInteraction(event.getEntity());
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        markPlayerInteraction(event.getEntity());
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        markPlayerInteraction(event.getEntity());
    }

    // ===== 坐姿检测（可配置） =====
    @SubscribeEvent
    public void onMaidTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) return;
        if (maid.level().isClientSide) return;
        if (!maid.isTame() || maid.getOwner() == null) return;
        if (EmotionBetrayalManager.isBetraying(maid)) return;
        if (!EmotionPassiveConfig.SIT_DETECTION_ENABLED.get()) return;

        UUID maidId = maid.getUUID();
        boolean isSitting = maid.isInSittingPose();
        Boolean wasSitting = lastSitState.get(maidId);
        if (wasSitting == null) wasSitting = false;

        if (!wasSitting && isSitting) {
            UUID ownerId = getOwnerUUID(maid);
            if (ownerId != null) {
                int trustChange = EmotionPassiveConfig.SIT_TRUST_CHANGE.get();
                int fearChange = EmotionPassiveConfig.SIT_FEAR_CHANGE.get();
                EmotionData.addTrust(maid, ownerId, trustChange);
                EmotionData.addFear(maid, ownerId, fearChange);
                if (maid.getOwner() instanceof ServerPlayer owner) {
                    String trustSign = trustChange >= 0 ? "+" : "";
                    String fearSign = fearChange >= 0 ? "+" : "";
                    MaidResponder.debug(owner,
                            Component.literal("§a[坐姿] ")
                                    .append(maid.getName())
                                    .append(Component.literal(" 坐下：信任" + trustSign + trustChange + "，恐惧" + fearSign + fearChange))
                    );                }
            }
        }
        lastSitState.put(maidId, isSitting);
    }
}
