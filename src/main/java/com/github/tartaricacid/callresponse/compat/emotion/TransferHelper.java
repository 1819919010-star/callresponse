package com.github.tartaricacid.callresponse.compat.emotion;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

public class TransferHelper {
    /**
     * 等效于旧版 IItemHandler.extractItem。
     * 从指定槽位提取最多 amount 个物品，返回实际提取到的 ItemStack。
     *
     * @param handler  目标容器（ResourceHandler<ItemResource>）
     * @param slot     要提取的槽位索引
     * @param amount   期望提取的最大数量
     * @param simulate true 表示仅模拟（不实际修改），false 表示实际执行
     * @return 实际提取到的物品堆栈（可能数量少于请求），空栈表示未提取到任何物品
     */
    public static ItemStack extractItem(ResourceHandler<ItemResource> handler, int slot, int amount, boolean simulate) {
        if (handler == null || amount <= 0) {
            return ItemStack.EMPTY;
        }

        // 检查槽位是否有效
        if (slot < 0 || slot >= handler.size()) {
            return ItemStack.EMPTY;
        }

        // 获取槽位中的资源
        ItemResource resource = handler.getResource(slot);
        if (resource.isEmpty()) {
            return ItemStack.EMPTY;
        }

        // 开启事务
        try (Transaction tx = Transaction.openRoot()) {
            // 执行提取操作
            int extracted = handler.extract(slot, resource, amount, tx);

            // 如果不是模拟，提交事务使修改生效
            if (!simulate) {
                tx.commit();
            }
            // 模拟模式下，事务会在 try 块结束时自动回滚，不修改任何内容

            if (extracted <= 0) {
                return ItemStack.EMPTY;
            } else {
                // 将提取到的资源转换为 ItemStack（带实际数量）
                return resource.toStack(extracted);
            }
        }
    }

    /**
     * 等效于旧版 ItemHandlerHelper.insertItemStacked。
     * 尝试将物品堆栈插入到 handler 中，优先填充已有同类物品，再放入空槽。
     *
     * @param handler  目标容器（ResourceHandler<ItemResource>）
     * @param stack    要插入的物品堆栈
     * @param simulate true 表示仅模拟（不实际修改），false 表示实际执行
     * @return 未能插入的剩余物品堆栈（空栈表示全部插入成功）
     */
    public static ItemStack insertItemStacked(ResourceHandler<ItemResource> handler, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || handler == null) {
            return stack.copy(); // 直接返回原堆栈（或 ItemStack.EMPTY？原版行为是返回原堆栈）
        }

        ItemResource resource = ItemResource.of(stack);
        int amount = stack.getCount();

        // 开启根事务
        try (Transaction tx = Transaction.openRoot()) {
            // 使用 insertStacking 自动处理堆叠逻辑
            int inserted = ResourceHandlerUtil.insertStacking(handler, resource, amount, tx);

            // 如果非模拟模式，提交事务使修改生效
            if (!simulate) {
                tx.commit();
            }
            // 模拟模式下，事务会在 try 块结束时自动回滚，不会修改任何内容

            // 计算剩余数量
            int remaining = amount - inserted;
            if (remaining <= 0) {
                return ItemStack.EMPTY;
            } else {
                return resource.toStack(remaining);
            }
        }
    }

    public static ItemStack getItemInSlot(ResourceHandler<ItemResource> handler, int slot){
        return handler.getResource(slot).toStack(handler.getAmountAsInt(slot));
    }
}
