package me.zoom.xannax.module.modules.render;

import java.awt.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zoom.xannax.Xannax;
import me.zoom.xannax.event.events.PlayerJoinEvent;
import me.zoom.xannax.event.events.PlayerLeaveEvent;
import me.zoom.xannax.event.events.RenderEvent;
import me.zoom.xannax.command.Command;
import me.zoom.xannax.module.Module;
import me.zoom.xannax.module.ModuleManager;
import me.zoom.xannax.setting.Setting;
import me.zoom.xannax.util.ColorUtil;
import me.zoom.xannax.util.MathUtil;
import me.zoom.xannax.util.RenderUtil;
import me.zoom.xannax.util.font.FontUtils;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

public class LogoutSpots extends Module
{
    Setting.Double range;
    Setting.Integer red;
    Setting.Integer green;
    Setting.Integer blue;
    Setting.Integer alpha;
    Setting.Boolean scaleing;
    Setting.Double scaling;
    Setting.Double factor;
    Setting.Boolean smartScale;
    Setting.Boolean coords;
    Setting.Boolean message;
    List<LogoutPos> spots;

    AxisAlignedBB bb;
    double x;
    double y;
    double z;

    public LogoutSpots() {
        super("LogoutSpots", "Renders LogoutSpots", Category.Render);
        this.range = registerDouble("Range", "Range", 300.0f, 50.0f, 500.0f);
        this.red = registerInteger("Red", "Red", 255, 0, 255);
        this.green = registerInteger("Green", "Green", 0, 0, 255);
        this.blue = registerInteger("Blue", "Blue", 0, 0, 255);
        this.alpha = registerInteger("Alpha", "Alpha", 255, 0, 255);
        this.scaleing = registerBoolean("Scale", "Scale", false);
        this.scaling = registerDouble("Size", "Size", 4.0f, 0.1f, 20.0f);
        this.factor = registerDouble("Factor", "Factor", 0.3f, 0.1f, 1.0f);
        this.smartScale = registerBoolean("SmartScale", "SmartScale", false);
        this.coords = registerBoolean("Coords", "Coords", true);
        this.message = registerBoolean("Message", "Message", false);
        this.spots = new CopyOnWriteArrayList<LogoutPos>();
    }

    @Override
    public void onDisable() {
        this.spots.clear();
        Xannax.EVENT_BUS.unsubscribe(this);
    }

    @Override
    public void onEnable() {
        Xannax.EVENT_BUS.subscribe(this);
    }

    @Override
    public void onWorldRender(final RenderEvent event) {
        if (!this.spots.isEmpty()) {
            synchronized (this.spots) {
                this.spots.forEach(spot -> {
                    if (spot.getEntity() != null) {
                        bb = RenderUtil.interpolateAxis(spot.getEntity().getEntityBoundingBox());
                        RenderUtil.drawBlockOutline(bb, new Color(this.red.getValue(), this.green.getValue(), this.blue.getValue(), this.alpha.getValue()), 1.0f);
                        x = this.interpolate(spot.getEntity().lastTickPosX, spot.getEntity().posX, event.getPartialTicks()) - LogoutSpots.mc.getRenderManager().renderPosX;
                        y = this.interpolate(spot.getEntity().lastTickPosY, spot.getEntity().posY, event.getPartialTicks()) - LogoutSpots.mc.getRenderManager().renderPosY;
                        z = this.interpolate(spot.getEntity().lastTickPosZ, spot.getEntity().posZ, event.getPartialTicks()) - LogoutSpots.mc.getRenderManager().renderPosZ;
                        this.renderNameTag(spot.getName(), x, y, z, event.getPartialTicks(), spot.getX(), spot.getY(), spot.getZ());
                    }
                });
            }
        }
    }

    @Override
    public void onUpdate() {
            this.spots.removeIf(spot -> LogoutSpots.mc.player.getDistanceSq((Entity)spot.getEntity()) >= MathUtil.square(this.range.getValue()));
    }

    @EventHandler
    private final Listener<PlayerJoinEvent> listener1 = new Listener<>(event -> {
            final UUID uuid = event.getUuid();
            final EntityPlayer entity = LogoutSpots.mc.world.getPlayerEntityByUUID(uuid);
            if (entity != null && this.message.getValue()) {
                Command.sendClientMessage("§a" + entity.getName() + " just logged in" + (this.coords.getValue() ? (" at (" + (int) entity.posX + ", " + (int) entity.posY + ", " + (int) entity.posZ + ")!") : "!"));
            }
            this.spots.removeIf(pos -> pos.getName().equalsIgnoreCase(event.getName()));
    });

    @EventHandler
    private final Listener<PlayerLeaveEvent> listener2 = new Listener<>(event -> {
        final EntityPlayer entity2 = event.getEntity();
        final UUID uuid2 = event.getUuid();
        final String name = event.getName();
        if (this.message.getValue()) {
            Command.sendClientMessage("§c" + event.getName() + " just logged out" + (this.coords.getValue() ? (" at (" + (int) entity2.posX + ", " + (int) entity2.posY + ", " + (int) entity2.posZ + ")!") : "!"));
        }
        if (name != null && entity2 != null && uuid2 != null) {
            this.spots.add(new LogoutPos(name, uuid2, entity2));
        }
    });

    private void renderNameTag(final String name, final double x, final double yi, final double z, final float delta, final double xPos, final double yPos, final double zPos) {
        final double y = yi + 0.7;
        final Entity camera = LogoutSpots.mc.getRenderViewEntity();
        assert camera != null;
        final double originalPositionX = camera.posX;
        final double originalPositionY = camera.posY;
        final double originalPositionZ = camera.posZ;
        camera.posX = this.interpolate(camera.prevPosX, camera.posX, delta);
        camera.posY = this.interpolate(camera.prevPosY, camera.posY, delta);
        camera.posZ = this.interpolate(camera.prevPosZ, camera.posZ, delta);
        final String displayTag = name + " XYZ: " + (int)xPos + ", " + (int)yPos + ", " + (int)zPos;
        final double distance = camera.getDistance(x + LogoutSpots.mc.getRenderManager().viewerPosX, y + LogoutSpots.mc.getRenderManager().viewerPosY, z + LogoutSpots.mc.getRenderManager().viewerPosZ);
        final int width = FontUtils.getStringWidth(ModuleManager.isModuleEnabled("CustomFont"), displayTag) / 2;
        double scale = (0.0018 + this.scaling.getValue() * (distance * this.factor.getValue())) / 1000.0;
        if (distance <= 8.0 && this.smartScale.getValue()) {
            scale = 0.0245;
        }
        if (!this.scaleing.getValue()) {
            scale = this.scaling.getValue() / 100.0;
        }
        GlStateManager.pushMatrix();
        RenderHelper.enableStandardItemLighting();
        GlStateManager.enablePolygonOffset();
        GlStateManager.doPolygonOffset(1.0f, -1500000.0f);
        GlStateManager.disableLighting();
        GlStateManager.translate((float)x, (float)y + 1.4f, (float)z);
        GlStateManager.rotate(-LogoutSpots.mc.getRenderManager().playerViewY, 0.0f, 1.0f, 0.0f);
        GlStateManager.rotate(LogoutSpots.mc.getRenderManager().playerViewX, (LogoutSpots.mc.gameSettings.thirdPersonView == 2) ? -1.0f : 1.0f, 0.0f, 0.0f);
        GlStateManager.scale(-scale, -scale, scale);
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.enableBlend();
        drawBorderedRectReliant((float)(-width - 2), (float)(-(FontUtils.getFontHeight(ModuleManager.isModuleEnabled("CustomFont")) + 1)), width + 2.0f, 1.5f, 1.8f,1426064384, 855638016);
        GlStateManager.disableBlend();
        FontUtils.drawStringWithShadow(ModuleManager.isModuleEnabled("CustomFont"), displayTag, (-width), (-(FontUtils.getFontHeight(ModuleManager.isModuleEnabled("CustomFont")) - 1)), new Color(this.red.getValue(), this.green.getValue(), this.blue.getValue(), this.alpha.getValue()).getRGB());
        camera.posX = originalPositionX;
        camera.posY = originalPositionY;
        camera.posZ = originalPositionZ;
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.disablePolygonOffset();
        GlStateManager.doPolygonOffset(1.0f, 1500000.0f);
        GlStateManager.popMatrix();
    }

    private double interpolate(final double previous, final double current, final float delta) {
        return previous + (current - previous) * delta;
    }

    private static class LogoutPos
    {
        private final String name;
        private final UUID uuid;
        private final EntityPlayer entity;
        private final double x;
        private final double y;
        private final double z;

        public LogoutPos(final String name, final UUID uuid, final EntityPlayer entity) {
            this.name = name;
            this.uuid = uuid;
            this.entity = entity;
            this.x = entity.posX;
            this.y = entity.posY;
            this.z = entity.posZ;
        }

        public String getName() {
            return this.name;
        }

        public UUID getUuid() {
            return this.uuid;
        }

        public EntityPlayer getEntity() {
            return this.entity;
        }

        public double getX() {
            return this.x;
        }

        public double getY() {
            return this.y;
        }

        public double getZ() {
            return this.z;
        }
    }

    public static void drawBorderedRectReliant(final float x, final float y, final float x1, final float y1, final float lineWidth, final int inside, final int border) {
        enableGL2D();
        drawRect(x, y, x1, y1, inside);
        glColor(border);
        GL11.glEnable(3042);
        GL11.glDisable(3553);
        GL11.glBlendFunc(770, 771);
        GL11.glLineWidth(lineWidth);
        GL11.glBegin(3);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x, y1);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x1, y);
        GL11.glVertex2f(x, y);
        GL11.glEnd();
        GL11.glEnable(3553);
        GL11.glDisable(3042);
        disableGL2D();
    }

    public static void drawRect(final Rectangle rectangle, final int color) {
        drawRect((float)rectangle.x, (float)rectangle.y, (float)(rectangle.x + rectangle.width), (float)(rectangle.y + rectangle.height), color);
    }

    public static void drawRect(final float x, final float y, final float x1, final float y1, final int color) {
        enableGL2D();
        glColor(color);
        drawRect(x, y, x1, y1);
        disableGL2D();
    }

    public static void drawRect(final float x, final float y, final float x1, final float y1, final float r, final float g, final float b, final float a) {
        enableGL2D();
        GL11.glColor4f(r, g, b, a);
        drawRect(x, y, x1, y1);
        disableGL2D();
    }

    public static void drawRect(final float x, final float y, final float x1, final float y1) {
        GL11.glBegin(7);
        GL11.glVertex2f(x, y1);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x1, y);
        GL11.glVertex2f(x, y);
        GL11.glEnd();
    }

    public static void enableGL2D() {
        GL11.glDisable(2929);
        GL11.glEnable(3042);
        GL11.glDisable(3553);
        GL11.glBlendFunc(770, 771);
        GL11.glDepthMask(true);
        GL11.glEnable(2848);
        GL11.glHint(3154, 4354);
        GL11.glHint(3155, 4354);
    }

    public static void disableGL2D() {
        GL11.glEnable(3553);
        GL11.glDisable(3042);
        GL11.glEnable(2929);
        GL11.glDisable(2848);
        GL11.glHint(3154, 4352);
        GL11.glHint(3155, 4352);
    }

    public static void glColor(final int hex) {
        final float alpha = (hex >> 24 & 0xFF) / 255.0f;
        final float red = (hex >> 16 & 0xFF) / 255.0f;
        final float green = (hex >> 8 & 0xFF) / 255.0f;
        final float blue = (hex & 0xFF) / 255.0f;
        GL11.glColor4f(red, green, blue, alpha);
    }
}

