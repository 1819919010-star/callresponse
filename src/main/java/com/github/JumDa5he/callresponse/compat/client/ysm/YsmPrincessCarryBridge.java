package com.github.JumDa5he.callresponse.compat.client.ysm;

import com.github.JumDa5he.callresponse.compat.task.PrincessCarryManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraftforge.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * YSM 2.6.5 的最小软兼容桥，只叠加静态的 carryon:princess 姿势。
 * 不保存公主抱业务状态；实体 passenger 关系解除后，下一帧自然恢复 YSM 原姿势。
 */
public final class YsmPrincessCarryBridge {
    private static final Logger LOG = LogManager.getLogger();
    private static final String PACKAGE = "com.elfmcys.yesstevemodel.";
    private static final String[] COMPONENTS = {
            "Oo0Oo0o00O00Oo0OOoOOoooo", "o0OOooo0o0OO00OoOOOo0o0O", "O00OOOooOoooOoo0o0o0oO0O",
            "oOOOo0OOO0ooooo0O00OO0o0", "OOOOo0O0oO0OOo0O0O0Oo0O0", "Ooooo0oooO0oooOOOoO0000O",
            "oo0OoO00oOoo000O0000o0oo", "oooooooOOoOOoO00OooOo00O", "Oo00o0OooOOo0ooOoo0oO0o0"
    };
    /** carryon.animation.json 中 carryon:princess 的完整静态骨骼数据。 */
    private static final Pose[] PRINCESS_POSE = {
            Pose.rotation("Root", 0, 0, 0),
            // YSM 原始公主抱姿势偏低；只抬高被抱模型，不影响普通 TLM 渲染路径。
            Pose.both("AllBody", 0, -90, -115, -5, -7, 6),
            Pose.both("UpBody", 50, 0, 0, 0, 1, 0.75F),
            Pose.rotation("Head", 27.5F, 0, 0),
            Pose.rotation("LongHair", 37.5F, 0, 0),
            Pose.rotation("LeftArm", -16.77609F, 44.19145F, -7.81185F),
            Pose.rotation("LeftForeArm", -97.5F, 0, 0),
            Pose.rotation("RightArm", -16.36648F, -42.10287F, -4.20805F),
            Pose.rotation("RightForeArm", -127.5F, 0, 0),
            Pose.rotation("LeftLeg", -7.61435F, 9.91358F, -1.31845F),
            Pose.rotation("LeftLowerLeg", 65, 0, 0),
            Pose.rotation("LeftFoot", 27.5F, 0, 0),
            Pose.rotation("RightLeg", -2.17539F, -10.01806F, -1.21764F),
            Pose.rotation("RightLowerLeg", 65, 0, 0),
            Pose.rotation("RightFoot", 7.5F, 0, 0),
            Pose.rotation("RightHand", 0, 0, 0),
            Pose.rotation("LeftHand", 0, 0, 0),
            Pose.both("Tail", 20, 0, 0, 0, 0, -3),
            Pose.rotationScale("clothe", -37.5F, 0, 0, 1.065F, 1, 1)
    };

    private static final Map<Object, List<Saved>> SAVED = new WeakHashMap<>();
    private static Method entityAccessor;
    private static Method runtimeAccessor;
    private static Method bonesAccessor;
    private static Method nameAccessor;
    private static Method bindRotationAccessor;
    private static final Method[] GET = new Method[9];
    private static final Method[] SET = new Method[9];
    private static boolean initialized;
    private static boolean disabled;

    private YsmPrincessCarryBridge() {
    }

    private static boolean initialize() {
        if (disabled) return false;
        if (initialized) return true;
        try {
            String version = ModList.get().getModContainerById("yes_steve_model")
                    .map(container -> container.getModInfo().getVersion().toString()).orElse("");
            if (!"2.6.5-forge+mc1.20.1".equals(version)) {
                disabled = true;
                if (!version.isEmpty()) {
                    LOG.warn("YSM princess carry animation disabled for unverified version {}", version);
                }
                return false;
            }
            Class<?> base = Class.forName(PACKAGE + "o0000OoOooO0oo0o0oooo0Oo");
            Class<?> runtime = Class.forName(PACKAGE + "OOOO0O0O000O000000oOOO0o");
            Class<?> bone = Class.forName(PACKAGE + "Oo0o00oOOo0OO000000O0oO0");
            entityAccessor = base.getMethod("OO00OOOOo0Ooo0oo0o0Oo0OO");
            runtimeAccessor = base.getMethod("OOOoOO000000o0o0oOooo0o0");
            bonesAccessor = runtime.getMethod("O00OOOooOoooOoo0o0o0oO0O");
            nameAccessor = bone.getMethod("oOOo0Ooo0oOoo0O0OOOOo0oo");
            bindRotationAccessor = bone.getMethod("OO0ooO00OoO00o0OO0OOooO0");
            for (int i = 0; i < COMPONENTS.length; i++) {
                GET[i] = bone.getMethod(COMPONENTS[i]);
                SET[i] = bone.getMethod(COMPONENTS[i], float.class);
            }
            initialized = true;
            return true;
        } catch (ReflectiveOperationException | LinkageError error) {
            disable(error);
            return false;
        }
    }

    /** YSM 计算本帧原始姿势前，撤销本桥上一帧写入的临时分量。 */
    public static void before(Object animatable) {
        List<Saved> saved = SAVED.remove(animatable);
        if (saved == null) return;
        try {
            for (int i = saved.size() - 1; i >= 0; i--) {
                Saved value = saved.get(i);
                SET[value.component].invoke(value.bone, value.value);
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            disable(error);
        }
    }

    /** 只在当前 YSM 女仆确实被另一只公主抱工作女仆骑乘时叠加 carryon:princess。 */
    public static void after(Object animatable) {
        if (!initialize()) return;
        try {
            if (!(entityAccessor.invoke(animatable) instanceof EntityMaid carried)
                    || !(carried.getVehicle() instanceof EntityMaid carrier)
                    || !PrincessCarryManager.isMaidCarrySession(carrier, carried)) {
                return;
            }
            Object runtime = runtimeAccessor.invoke(animatable);
            if (runtime == null) return;

            Map<String, Object> exact = new HashMap<>();
            Map<String, Object> normalized = new HashMap<>();
            for (Object bone : ((Map<?, ?>) bonesAccessor.invoke(runtime)).values()) {
                String name = (String) nameAccessor.invoke(bone);
                exact.put(name, bone);
                normalized.putIfAbsent(name.toLowerCase(Locale.ROOT), bone);
            }
            List<Saved> saved = new ArrayList<>();
            SAVED.put(animatable, saved);
            for (Pose pose : PRINCESS_POSE) {
                Object bone = exact.get(pose.bone);
                if (bone == null) bone = normalized.get(pose.bone.toLowerCase(Locale.ROOT));
                if (bone == null) continue;
                if (pose.rotation != null) applyRotation(bone, pose.rotation, saved);
                if (pose.position != null) applyDirect(bone, 3, pose.position, saved);
                if (pose.scale != null) applyDirect(bone, 6, pose.scale, saved);
            }
        } catch (Exception | LinkageError error) {
            before(animatable);
            disable(error);
        }
    }

    private static void applyRotation(Object bone, float[] values, List<Saved> saved)
            throws ReflectiveOperationException {
        Vector3f initial = (Vector3f) bindRotationAccessor.invoke(bone);
        for (int axis = 0; axis < 3; axis++) {
            saved.add(new Saved(bone, axis, ((Number) GET[axis].invoke(bone)).floatValue()));
            float value = initial.get(axis) + (float) Math.toRadians(values[axis]) * (axis == 2 ? 1 : -1);
            SET[axis].invoke(bone, value);
        }
    }

    private static void applyDirect(Object bone, int offset, float[] values, List<Saved> saved)
            throws ReflectiveOperationException {
        for (int axis = 0; axis < 3; axis++) {
            int component = offset + axis;
            saved.add(new Saved(bone, component, ((Number) GET[component].invoke(bone)).floatValue()));
            SET[component].invoke(bone, values[axis]);
        }
    }

    private static void disable(Throwable error) {
        if (!disabled) {
            LOG.error("Disabling YSM princess carry animation bridge; retaining original YSM rendering", error);
        }
        disabled = true;
    }

    private record Saved(Object bone, int component, float value) {
    }

    private record Pose(String bone, float[] rotation, float[] position, float[] scale) {
        private static Pose rotation(String bone, float x, float y, float z) {
            return new Pose(bone, new float[]{x, y, z}, null, null);
        }

        private static Pose both(String bone, float rx, float ry, float rz, float px, float py, float pz) {
            return new Pose(bone, new float[]{rx, ry, rz}, new float[]{px, py, pz}, null);
        }

        private static Pose rotationScale(String bone, float rx, float ry, float rz,
                                          float sx, float sy, float sz) {
            return new Pose(bone, new float[]{rx, ry, rz}, null, new float[]{sx, sy, sz});
        }
    }
}
