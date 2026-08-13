package com.github.JumDa5he.callresponse.compat.hunger;

import com.github.JumDa5he.callresponse.compat.gui.MaidStatusContainerGui;
import com.github.JumDa5he.callresponse.compat.gui.MaidStatusTabButton;
import com.github.JumDa5he.callresponse.compat.menu.OpenMaidStatusC2SPacket;
import com.github.tartaricacid.touhoulittlemaid.api.event.client.MaidContainerGuiEvent;
import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.AbstractMaidContainerGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.PacketDistributor;


public class MaidHungerGuiDisplay {
    private static final String STATUS_TAB = "callresponse_status_tab";

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onMaidGuiInit(MaidContainerGuiEvent.Init event) {
        boolean selected = event.getGui() instanceof MaidStatusContainerGui;
        TabPosition position = findFreeTabPosition(event);
        MaidStatusTabButton tab = new MaidStatusTabButton(position.x(), position.y(), selected, position.sideLayout(),
                ignored -> {
                    if (!selected) {
                        openStatusPage(event.getGui());
                    }
                });
        event.addButton(STATUS_TAB, tab);
    }

    private TabPosition findFreeTabPosition(MaidContainerGuiEvent.Init event) {
        int left = event.getLeftPos();
        int top = event.getTopPos();

        // Continue the normal top tab row when possible. LOWEST priority lets us see
        // buttons already reserved by most other add-ons before choosing a position.
        int[] topOffsets = {169, 194, 219};
        for (int offset : topOffsets) {
            if (isAreaFree(event.getGui(), left + offset, top + 5, 24, 26)) {
                return new TabPosition(left + offset, top + 5, false);
            }
        }

        // If the top row is full, continue below TLM's own right-side buttons.
        int[] sideOffsets = {87, 112, 137, 162};
        for (int offset : sideOffsets) {
            if (isAreaFree(event.getGui(), left + 251, top + offset, 26, 24)) {
                return new TabPosition(left + 251, top + offset, true);
            }
        }
        return new TabPosition(left + 251, top + 187, true);
    }

    private boolean isAreaFree(AbstractMaidContainerGui<?> gui, int x, int y, int width, int height) {
        for (GuiEventListener child : gui.children()) {
            if (child instanceof AbstractWidget widget && overlaps(x, y, width, height, widget)) {
                return false;
            }
        }
        for (AbstractWidget widget : gui.getEventAddButtons().values()) {
            if (overlaps(x, y, width, height, widget)) {
                return false;
            }
        }
        return true;
    }

    private boolean overlaps(int x, int y, int width, int height, AbstractWidget widget) {
        return x < widget.getX() + widget.getWidth()
                && x + width > widget.getX()
                && y < widget.getY() + widget.getHeight()
                && y + height > widget.getY();
    }


    private void openStatusPage(AbstractMaidContainerGui<?> parent) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || parent.getMaid() == null) {
            return;
        }
        PacketDistributor.sendToServer(new OpenMaidStatusC2SPacket(parent.getMaid().getId()));
    }

    private record TabPosition(int x, int y, boolean sideLayout) {
    }
}
