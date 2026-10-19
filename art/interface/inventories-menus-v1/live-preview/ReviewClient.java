package preview;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import org.lwjgl.glfw.GLFW;

/** Isolated visual-review harness. Never included in the production mod. */
public final class ReviewClient implements ClientModInitializer {
    private static final boolean INTEGRATION = Boolean.getBoolean("mysteries.menu.integration");
    private boolean opened, setup;
    private int wait, stage;
    private String previous = "", pending = "";
    private String scene = "day";
    private int scale;
    private int pointerX, pointerY;
    private Path directory;
    private static final Map<String, String> BLOCKS = Map.ofEntries(
        Map.entry("crafting", "crafting_table"), Map.entry("crafting-page2", "crafting_table"),
        Map.entry("furnace", "furnace"), Map.entry("blast-furnace", "blast_furnace"), Map.entry("smoker", "smoker"),
        Map.entry("anvil", "anvil"), Map.entry("enchanting", "enchanting_table"), Map.entry("smithing", "smithing_table"),
        Map.entry("grindstone", "grindstone"), Map.entry("loom", "loom"), Map.entry("cartography", "cartography_table"),
        Map.entry("stonecutter", "stonecutter"), Map.entry("brewing", "brewing_stand"), Map.entry("beacon", "beacon"),
        Map.entry("hopper", "hopper"), Map.entry("dispenser", "dispenser"), Map.entry("dropper", "dropper"),
        Map.entry("shulker", "shulker_box"), Map.entry("barrel", "barrel"), Map.entry("ender-chest", "ender_chest"),
        Map.entry("crafter", "crafter"));

    public static boolean eligible() {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen == null) return false;
        String name = screen.getClass().getName();
        return name.startsWith("net.minecraft.client.gui.screens.inventory.") ||
            name.startsWith("net.minecraft.client.gui.screens.options.") || screen.getClass() == PauseScreen.class;
    }

    public static Identifier skin(Identifier id) {
        if (INTEGRATION) return id;
        if (eligible() && id.getNamespace().equals("minecraft") &&
            (id.getPath().startsWith("widget/button") || id.getPath().startsWith("widget/slider") ||
             id.getPath().equals("tooltip/frame") || id.getPath().equals("tooltip/background")))
            return Identifier.fromNamespaceAndPath("mysteries_preview", id.getPath());
        return id;
    }

    @Override public void onInitializeClient() {
        if (!INTEGRATION) ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
            if (screen.getClass() == PauseScreen.class) {
                int first = screen.children().stream().filter(v -> v instanceof Button).map(v -> (Button)v)
                    .mapToInt(AbstractWidget::getY).min().orElse(90);
                for (var child : screen.children()) if (child instanceof StringWidget title) title.setY(first - 22);
                ScreenEvents.afterBackground(screen).register((s, g, mx, my, delta) -> {
                    g.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.parse("mysteries_preview:panel/dark"),
                        (s.width - 204) / 2, first - 28, 204, 20);
                });
            }
            ScreenEvents.afterExtract(screen).register((s, g, mx, my, delta) -> {
                if (!eligible()) return;
                for (var child : s.children()) if (child instanceof AbstractButton button && button.isFocused()) {
                    int x = button.getX() + 3, y = button.getY() + 3, w = button.getWidth() - 7, h = button.getHeight() - 7;
                    for (int sx : new int[]{0, w}) for (int sy : new int[]{0, h}) {
                        int dx = sx == 0 ? 1 : -1, dy = sy == 0 ? 1 : -1;
                        for (int n = 0; n < 3; n++) {
                            g.fill(x + sx + dx*n, y + sy, x + sx + dx*n + 1, y + sy + 1, 0xFFDFC28B);
                            g.fill(x + sx, y + sy + dy*n, x + sx + 1, y + sy + dy*n + 1, 0xFFDFC28B);
                        }
                    }
                }
            });
        });
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void command(Minecraft mc, String line) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), line));
    }

    private void write(String file, String value) {
        try { Files.writeString(directory.resolve(file), value); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private void pointer(Minecraft mc) throws Exception {
        GLFW.glfwSetCursorPos(mc.getWindow().handle(), pointerX * (double)scale, pointerY * (double)scale);
        for (String axis : new String[]{"xpos", "ypos"}) {
            var field = mc.mouseHandler.getClass().getDeclaredField(axis);
            field.setAccessible(true);
            field.setDouble(mc.mouseHandler, (axis.equals("xpos") ? pointerX : pointerY) * (double)scale);
        }
    }

    private void pagination(Minecraft mc) throws Exception {
        var bookField = AbstractRecipeBookScreen.class.getDeclaredField("recipeBookComponent");
        bookField.setAccessible(true);
        var book = bookField.get(mc.gui.screen());
        var pageField = RecipeBookComponent.class.getDeclaredField("recipeBookPage");
        pageField.setAccessible(true);
        var page = pageField.get(book);
        var fields = new HashMap<String, Object>();
        for (String name : List.of("backButton", "forwardButton", "currentPage", "totalPages")) {
            var field = page.getClass().getDeclaredField(name);
            field.setAccessible(true);
            fields.put(name, field.get(page));
        }
        var back = (ImageButton)fields.get("backButton");
        var forward = (ImageButton)fields.get("forwardButton");
        int current = (int)fields.get("currentPage") + 1, total = (int)fields.get("totalPages");
        int ox = back.getX() - 38, oy = back.getY() - 137;
        var label = Component.translatable("gui.recipebook.page", current, total);
        int textWidth = mc.font.width(label);
        int[] geometry = {ox, oy, ox + 73 - textWidth / 2, oy + 141, textWidth + 1, mc.font.lineHeight,
            back.getX(), back.getY(), back.getWidth(), back.getHeight(),
            forward.getX(), forward.getY(), forward.getWidth(), forward.getHeight(), current, total};
        write("pagination.txt", Arrays.toString(geometry));
    }

    private void style(Minecraft mc, String name) {
        var settings = new me.vangoo.mysteries.client.screen.HudSettingsScreen(new PauseScreen(true));
        mc.gui.setScreen(settings);
        String label = Component.translatable("hud.mysteries_above.interface." + name).getString();
        var button = settings.children().stream().filter(v -> v instanceof AbstractWidget)
            .map(v -> (AbstractWidget)v).filter(v -> v.getMessage().getString().equals(label)).findFirst().orElseThrow();
        var event = new MouseButtonEvent(button.getX() + 4, button.getY() + 4, new MouseButtonInfo(0, 0));
        settings.mouseClicked(event, false);
        settings.mouseReleased(event);
        var actual = me.vangoo.mysteries.client.ClientOptions.INSTANCE.getHudSettings().getInterfaceStyle();
        if (!actual.name().equalsIgnoreCase(name)) throw new IllegalStateException("Style toggle failed: " + actual);
        // Unknown subclasses remain unchanged; all native chest sizes now use the approved skin.
        var inventory = Identifier.parse("minecraft:textures/gui/container/inventory.png");
        mc.gui.setScreen(new ContainerScreen(net.minecraft.world.inventory.ChestMenu.sixRows(0, mc.player.getInventory()),
            mc.player.getInventory(), Component.literal("Mod subclass scope check")) {});
        if (!me.vangoo.mysteries.client.render.InventorySkin.texture(inventory).equals(inventory))
            throw new IllegalStateException("Mod inventory subclass was reskinned");
        var chest = Identifier.parse("minecraft:textures/gui/container/generic_54.png");
        mc.gui.setScreen(new ContainerScreen(net.minecraft.world.inventory.ChestMenu.sixRows(0, mc.player.getInventory()),
            mc.player.getInventory(), Component.literal("Scope check")));
        if (me.vangoo.mysteries.client.render.InventorySkin.texture(chest).equals(chest) == name.equals("mod"))
            throw new IllegalStateException("Six-row container style is incorrect");
        mc.gui.setScreen(new PauseScreen(true));
        if (!me.vangoo.mysteries.client.render.InventorySkin.texture(inventory).equals(inventory))
            throw new IllegalStateException("Pause screen was reskinned in stage 2");
        if (me.vangoo.mysteries.client.render.InventorySkin.INSTANCE.allows(new TitleScreen()) ||
            me.vangoo.mysteries.client.render.InventorySkin.INSTANCE.allows(settings))
            throw new IllegalStateException("Title or mod settings screen entered the native skin scope");
        write("style-check.txt", "Native HUD settings click: " + actual + "; mod subclass unchanged; six-row container follows style");
    }

    private void tick(Minecraft mc) {
        try {
            if (directory == null) directory = mc.gameDirectory.toPath();
            if (!opened && mc.gui.screen() instanceof TitleScreen) {
                opened = true;
                mc.getWindow().setWindowed(1920, 1080);
                mc.createWorldOpenFlows().openWorld("Menu Review", () -> write("failed.txt", "Could not open copied world"));
                return;
            }
            if (mc.player == null || mc.gui.screen() instanceof LevelLoadingScreen) return;
            if (!pending.isEmpty()) pointer(mc);
            if (!setup) {
                setup = true;
                mc.options.pauseOnLostFocus = false;
                command(mc, "difficulty peaceful");
                command(mc, "gamemode survival @a");
                command(mc, "time set noon");
                command(mc, "weather clear");
                command(mc, "fill -24 -61 -24 24 -61 24 minecraft:grass_block");
                command(mc, "fill -12 -60 -12 12 -55 12 minecraft:air");
                command(mc, "tp @a 0.5 -60 0.5 0 8");
                command(mc, "clear @a");
                String[] gold = {"golden_sword", "golden_pickaxe", "golden_axe", "golden_shovel", "golden_hoe", "golden_apple", "golden_carrot", "gold_ingot", "raw_gold"};
                for (int i = 0; i < gold.length; i++) command(mc, "item replace entity @a hotbar."+i+" with minecraft:"+gold[i]);
                String[] other = {"diamond", "coal", "iron_ingot", "bread", "ender_pearl", "golden_apple", "gold_ingot"};
                for (int i = 0; i < other.length; i++) command(mc, "item replace entity @a inventory."+i+" with minecraft:"+other[i]+" 16");
                command(mc, "item replace entity @a weapon.offhand with minecraft:golden_apple");
                for (String armor : new String[]{"head", "chest", "legs", "feet"}) {
                    String item = switch(armor) { case "head" -> "helmet"; case "chest" -> "chestplate"; case "legs" -> "leggings"; default -> "boots"; };
                    command(mc, "item replace entity @a armor."+armor+" with minecraft:iron_"+item);
                }
                command(mc, "setblock 2 -60 2 minecraft:chest");
                command(mc, "setblock 3 -60 2 minecraft:crafting_table");
                for (int i = 0; i < gold.length; i++) command(mc, "item replace block 2 -60 2 container."+i+" with minecraft:"+gold[i]+(i < 5 ? " 1" : " 16"));
                command(mc, "recipe give @a *");
                mc.getSingleplayerServer().execute(() -> {
                    var rules = mc.getSingleplayerServer().getGameRules();
                    rules.set(net.minecraft.world.level.gamerules.GameRules.ADVANCE_TIME, false, mc.getSingleplayerServer());
                    rules.set(net.minecraft.world.level.gamerules.GameRules.SHOW_ADVANCEMENT_MESSAGES, false, mc.getSingleplayerServer());
                    for (var player : mc.getSingleplayerServer().getPlayerList().getPlayers()) {
                        player.resetFallDistance();
                        player.setHealth(player.getMaxHealth());
                        player.getAbilities().invulnerable = true;
                    }
                });
                wait = 100;
                return;
            }
            if (wait > 0) { wait--; return; }
            if (!pending.isEmpty()) {
                // A delayed server close packet can arrive after the local inventory was opened.
                if (stage > 0 && mc.gui.screen() == null && (pending.equals("inventory") || pending.equals("tooltip"))) {
                    mc.gui.setScreen(new InventoryScreen(mc.player));
                    stage = 1;
                    wait = 30;
                    return;
                }
                if (stage == 0) {
                    if (pending.equals("inventory") || pending.equals("tooltip")) {
                        if (mc.player.isCreative()) { wait = 10; return; }
                        mc.gui.setScreen(new InventoryScreen(mc.player));
                        stage = 1; wait = 30; return;
                    }
                    if (pending.startsWith("creative")) {
                        if (!mc.player.isCreative()) { wait = 10; return; }
                        mc.gui.setScreen(new CreativeModeInventoryScreen(mc.player, mc.level.enabledFeatures(), false));
                        stage = 1;
                        wait = 30;
                        return;
                    }
                    BlockPos pos = new BlockPos(pending.equals("chest") || pending.equals("double-chest") ? 2 : 3, -60, 2);
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
                    stage = 1;
                    wait = 30;
                    return;
                }
                if (stage == 1) {
                    if (mc.gui.screen() instanceof CreativeModeInventoryScreen screen) {
                        var tab = pending.equals("creative-search") ? CreativeModeTabs.searchTab() :
                            CreativeModeTabs.tabs().stream().filter(t -> t.getType() ==
                                (pending.equals("creative-inventory") ? CreativeModeTab.Type.INVENTORY : CreativeModeTab.Type.CATEGORY))
                                .findFirst().orElseThrow();
                        var gx = CreativeModeInventoryScreen.class.getDeclaredMethod("getTabX", CreativeModeTab.class);
                        var gy = CreativeModeInventoryScreen.class.getDeclaredMethod("getTabY", CreativeModeTab.class);
                        gx.setAccessible(true); gy.setAccessible(true);
                        var field = AbstractContainerScreen.class.getDeclaredField("leftPos"); field.setAccessible(true);
                        int ox = field.getInt(screen);
                        field = AbstractContainerScreen.class.getDeclaredField("topPos"); field.setAccessible(true);
                        int oy = field.getInt(screen);
                        var click = new MouseButtonEvent(ox + (int)gx.invoke(screen, tab) + 10,
                            oy + (int)gy.invoke(screen, tab) + 10, new MouseButtonInfo(0, 0));
                        screen.mouseClicked(click, false); screen.mouseReleased(click);
                        var selected = CreativeModeInventoryScreen.class.getDeclaredField("selectedTab"); selected.setAccessible(true);
                        if (selected.get(null) != tab) throw new IllegalStateException("Native creative tab click failed");
                        if (pending.equals("creative-search")) {
                            for (char c : "золот".toCharArray()) screen.charTyped(new net.minecraft.client.input.CharacterEvent(c));
                            var search = CreativeModeInventoryScreen.class.getDeclaredField("searchBox"); search.setAccessible(true);
                            if (!((EditBox)search.get(screen)).getValue().equals("золот")) throw new IllegalStateException("Creative search input failed");
                        } else if (pending.equals("creative")) {
                            var offset = CreativeModeInventoryScreen.class.getDeclaredField("scrollOffs"); offset.setAccessible(true);
                            screen.mouseScrolled(ox + 180, oy + 40, 0, -3);
                            if (offset.getFloat(screen) <= 0) throw new IllegalStateException("Creative scrollbar did not scroll");
                        }
                        write("creative-input.txt", "Native tab click, search typing and scroll: " + pending);
                    }
                    if (mc.gui.screen() instanceof AbstractRecipeBookScreen<?> screen) {
                        var field = AbstractRecipeBookScreen.class.getDeclaredField("recipeBookComponent");
                        field.setAccessible(true);
                        var book = (RecipeBookComponent<?>)field.get(screen);
                        if (book.isVisible() != pending.startsWith("crafting")) {
                            var button = screen.children().stream().filter(v -> v instanceof ImageButton)
                                .map(v -> (ImageButton)v).findFirst().orElseThrow();
                            var event = new MouseButtonEvent(button.getX() + 5, button.getY() + 5, new MouseButtonInfo(0, 0));
                            button.mouseClicked(event, false);
                            button.mouseReleased(event);
                        }
                        if (pending.equals("crafting-page2")) {
                            var pageField = RecipeBookComponent.class.getDeclaredField("recipeBookPage");
                            pageField.setAccessible(true);
                            var page = pageField.get(book);
                            var forwardField = page.getClass().getDeclaredField("forwardButton");
                            forwardField.setAccessible(true);
                            var button = (ImageButton)forwardField.get(page);
                            var event = new MouseButtonEvent(button.getX() + 5, button.getY() + 5, new MouseButtonInfo(0, 0));
                            screen.mouseClicked(event, false);
                            screen.mouseReleased(event);
                            var current = page.getClass().getDeclaredField("currentPage");
                            current.setAccessible(true);
                            if (current.getInt(page) != 1) throw new IllegalStateException("Native next-page click did not reach page 2");
                            write("page-input.txt", "Native next-page button: page 2");
                        }
                        mc.getSingleplayerServer().execute(() -> {
                            for (var player : mc.getSingleplayerServer().getPlayerList().getPlayers())
                                if (player.containerMenu instanceof CraftingMenu menu) {
                                    for (int i = 1; i <= 9; i++) menu.getSlot(i).set(new ItemStack(i == 5 ? Items.APPLE : Items.GOLD_INGOT));
                                    menu.broadcastChanges();
                                }
                        });
                    }
                    if (pending.equals("tooltip")) {
                        pointerX = (mc.gui.screen().width - 176) / 2 + 8 + 5*18 + 8;
                        pointerY = (mc.gui.screen().height - 166) / 2 + 142 + 8;
                        pointer(mc);
                    }
                    if (pending.equals("pause-focus")) {
                        mc.setLastInputType(net.minecraft.client.InputType.KEYBOARD_TAB);
                        Screen screen = mc.gui.screen();
                        var button = screen.children().stream().filter(v -> v instanceof Button).map(v -> (Button)v)
                            .min(Comparator.comparingInt(AbstractWidget::getY)).orElseThrow();
                        screen.setFocused(button);
                        button.setFocused(true);
                    }
                    if (pending.equals("settings-pressed")) {
                        var queue = new ArrayDeque<GuiEventListener>(mc.gui.screen().children());
                        AbstractButton button = null;
                        while (!queue.isEmpty()) {
                            var child = queue.remove();
                            if (child instanceof AbstractButton b && b.getMessage().getString().startsWith("Субтитри")) button = b;
                            if (child instanceof ContainerEventHandler group) queue.addAll(group.children());
                        }
                        if (button == null) throw new IllegalStateException("Native subtitle option missing");
                        pointerX = button.getX() + button.getWidth() / 2;
                        pointerY = button.getY() + button.getHeight() / 2;
                        pointer(mc);
                        mc.gui.screen().mouseClicked(new MouseButtonEvent(pointerX, pointerY, new MouseButtonInfo(0, 0)), false);
                        var left = mc.mouseHandler.getClass().getDeclaredField("isLeftPressed");
                        left.setAccessible(true);
                        left.setBoolean(mc.mouseHandler, true);
                    }
                    if (pending.equals("settings-hover")) {
                        Screen screen = mc.gui.screen();
                        var queue = new ArrayDeque<GuiEventListener>(screen.children());
                        AbstractSliderButton master = null;
                        while (!queue.isEmpty()) {
                            var child = queue.remove();
                            if (child instanceof AbstractSliderButton slider && (master == null || slider.getY() < master.getY())) master = slider;
                            if (child instanceof ContainerEventHandler group) queue.addAll(group.children());
                        }
                        if (master == null) throw new IllegalStateException("No native sound slider");
                        double before = mc.options.getSoundSourceVolume(SoundSource.MASTER);
                        int clickX = master.getX() + 4 + (master.getWidth() - 8) * 42 / 100;
                        pointerX = master.getX() + 4 + (master.getWidth() - 8) * 62 / 100;
                        pointerY = master.getY() + 10;
                        var press = new MouseButtonEvent(clickX, pointerY, new MouseButtonInfo(0, 0));
                        screen.mouseClicked(press, false);
                        var drag = new MouseButtonEvent(pointerX, pointerY, new MouseButtonInfo(0, 0));
                        screen.mouseDragged(drag, pointerX - clickX, 0);
                        screen.mouseReleased(drag);
                        double after = mc.options.getSoundSourceVolume(SoundSource.MASTER);
                        if (Math.abs(after - .62) > .02 || before == after) throw new IllegalStateException("Native slider drag failed");
                        write("slider-input.txt", "Native click/drag/release: " + before + " -> " + after);
                        pointer(mc);
                    }
                    stage = 2;
                    wait = 40;
                } else {
                    if (pending.startsWith("crafting")) pagination(mc);
                    if (mc.gui.screen() instanceof AbstractContainerScreen<?> container) {
                        int[] geometry = new int[4];
                        String[] names = {"leftPos", "topPos", "imageWidth", "imageHeight"};
                        for (int n = 0; n < 4; n++) {
                            var field = AbstractContainerScreen.class.getDeclaredField(names[n]);
                            field.setAccessible(true);
                            geometry[n] = field.getInt(container);
                        }
                        write("screen-geometry.txt", Arrays.toString(geometry));
                    }
                    String name = "gold-" + pending + (scene.equals("cave") ? "-cave" : "") + "-gs" + scale + ".png";
                    Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 1,
                        message -> write("last-shot.txt", name));
                    write("last-screen.txt", mc.gui.screen().getClass().getName()+" "+mc.gui.screen().width+"x"+mc.gui.screen().height);
                    pending = "";
                }
                return;
            }
            if (!Files.exists(directory.resolve("ready.txt"))) write("ready.txt", "ready");
            Path request = directory.resolve("request.txt");
            if (!Files.exists(request)) return;
            String input = Files.readString(request).trim();
            if (input.isEmpty() || input.equals(previous)) return;
            previous = input;
            String[] parts = input.split(" ");
            if (parts[0].equals("stop")) { mc.stop(); return; }
            if (parts[0].equals("scene")) {
                scene = parts[1];
                mc.gui.setScreen(null);
                command(mc, "gamemode survival @a");
                command(mc, "time set noon");
                String material = scene.equals("cave") ? "minecraft:stone" : "minecraft:air";
                for (String box : new String[]{"-12 -54 -12 12 -54 12", "-12 -60 -12 -12 -55 12", "12 -60 -12 12 -55 12",
                    "-12 -60 -12 12 -55 -12", "-12 -60 12 12 -55 12"}) command(mc, "fill " + box + " " + material);
                command(mc, "setblock -3 -59 0 " + (scene.equals("cave") ? "minecraft:lantern" : "minecraft:air"));
                mc.getSingleplayerServer().execute(() -> write("scene-ready.txt", input));
                wait = 30;
                return;
            }
            if (parts[0].equals("reload")) { mc.reloadResourcePacks(); wait = 80; return; }
            if (INTEGRATION) style(mc, parts[3]);
            pending = parts[0];
            scale = Integer.parseInt(parts[1]);
            pointerX = pointerY = 0;
            mc.setLastInputType(net.minecraft.client.InputType.MOUSE);
            var left = mc.mouseHandler.getClass().getDeclaredField("isLeftPressed");
            left.setAccessible(true);
            left.setBoolean(mc.mouseHandler, false);
            mc.options.guiScale().set(scale);
            mc.resizeGui();
            mc.gui.setScreen(null);
            GLFW.glfwSetCursorPos(mc.getWindow().handle(), 0, 0);
            if (pending.startsWith("pause")) mc.gui.setScreen(new PauseScreen(true));
            else if (pending.equals("options")) mc.gui.setScreen(new net.minecraft.client.gui.screens.options.OptionsScreen(new PauseScreen(true), mc.options, false));
            else if (pending.equals("language")) mc.gui.setScreen(new net.minecraft.client.gui.screens.options.LanguageSelectScreen(new PauseScreen(true), mc.options, mc.getLanguageManager()));
            else if (pending.startsWith("settings")) {
                mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(.72);
                mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(.35);
                mc.options.getSoundSourceOptionInstance(SoundSource.BLOCKS).set(.60);
                mc.gui.setScreen(new SoundOptionsScreen(new PauseScreen(true), mc.options));
            }
            else if (pending.equals("inventory") || pending.equals("tooltip")) {
                command(mc, "gamemode survival @a");
                stage = 0; wait = 20; return;
            }
            else if (pending.startsWith("creative")) {
                command(mc, "gamemode creative @a");
                stage = 0; wait = 20; return;
            }
            else if (pending.equals("trading")) {
                var menu = new net.minecraft.world.inventory.MerchantMenu(0, mc.player.getInventory());
                var offers = new net.minecraft.world.item.trading.MerchantOffers();
                for (int n = 0; n < 10; n++) offers.add(new net.minecraft.world.item.trading.MerchantOffer(
                    new net.minecraft.world.item.trading.ItemCost(Items.EMERALD, n + 1), new ItemStack(Items.BREAD, n + 1), 12, 2, .05f));
                menu.setOffers(offers);
                mc.gui.setScreen(new MerchantScreen(menu, mc.player.getInventory(), Component.literal("Бібліотекар")));
            }
            else if (pending.equals("horse") || pending.equals("llama")) {
                var horse = pending.equals("llama") ? net.minecraft.world.entity.EntityTypes.LLAMA.create(mc.level, net.minecraft.world.entity.EntitySpawnReason.COMMAND) :
                    net.minecraft.world.entity.EntityTypes.HORSE.create(mc.level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                horse.setPos(mc.player.position());
                horse.setId(999001);
                int columns = pending.equals("llama") ? 5 : 0;
                var menu = new net.minecraft.world.inventory.HorseInventoryMenu(0, mc.player.getInventory(),
                    new net.minecraft.world.SimpleContainer(Math.max(2, columns * 3)), horse, columns);
                mc.gui.setScreen(new HorseInventoryScreen(menu, mc.player.getInventory(), horse, columns));
            }
            else if (pending.equals("nautilus")) {
                var mount = net.minecraft.world.entity.EntityTypes.NAUTILUS.create(mc.level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                mount.setPos(mc.player.position());
                mount.setId(999002);
                var menu = new net.minecraft.world.inventory.NautilusInventoryMenu(0, mc.player.getInventory(), new net.minecraft.world.SimpleContainer(2), mount, 0);
                mc.gui.setScreen(new NautilusInventoryScreen(menu, mc.player.getInventory(), mount, 0));
            }
            else {
                if (pending.equals("double-chest")) {
                    command(mc, "setblock 2 -60 2 minecraft:chest[type=left,facing=north]");
                    command(mc, "setblock 1 -60 2 minecraft:chest[type=right,facing=north]");
                } else if (pending.equals("chest")) {
                    command(mc, "setblock 1 -60 2 minecraft:air");
                    command(mc, "setblock 2 -60 2 minecraft:chest");
                } else command(mc, "setblock 3 -60 2 minecraft:" + BLOCKS.get(pending));
                stage = 0;
                wait = 20;
                return;
            }
            stage = 1;
            wait = 30;
        } catch (Exception e) {
            write("failed.txt", e.toString());
            throw new IllegalStateException(e);
        }
    }
}
