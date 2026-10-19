# Усі класи екранів 26.2

Повний перелік наявності, включно з абстрактними основами; не розширює обсяг змін. Віджети/текст/прямокутники з коду не мають окремої великої фонової PNG. У колонці показані прямі посилання; успадковані фони й віджети див. у вказаного базового класу та в AUDIT.md.

| Клас | Базовий клас | Область | Прямі ресурси | Спосіб |
|---|---|---|---|---|
| `com.mojang.realmsclient.RealmsMainScreen` | `net.minecraft.realms.RealmsScreen` | `reference-only` | `textures/gui/realms/no_realms.png` | `direct-texture-UV` |
| `com.mojang.realmsclient.gui.screens.AddRealmPopupScreen` | `net.minecraft.realms.RealmsScreen` | `reference-only` | `widget/cross_button;widget/cross_button_highlighted` | `sprite-stretch` |
| `com.mojang.realmsclient.gui.screens.RealmsBrokenWorldScreen` | `net.minecraft.realms.RealmsScreen` | `reference-only` | `widget/slot_frame` | `sprite-stretch` |
| `com.mojang.realmsclient.gui.screens.RealmsClientOutdatedScreen` | `net.minecraft.realms.RealmsScreen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `com.mojang.realmsclient.gui.screens.RealmsConfirmScreen` | `net.minecraft.realms.RealmsScreen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `com.mojang.realmsclient.gui.screens.RealmsCreateRealmScreen` | `net.minecraft.realms.RealmsScreen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `com.mojang.realmsclient.gui.screens.RealmsDownloadLatestWorldScreen` | `net.minecraft.realms.RealmsScreen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `com.mojang.realmsclient.gui.screens.RealmsGenericErrorScreen` | `net.minecraft.realms.RealmsScreen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `com.mojang.realmsclient.gui.screens.RealmsLongRunningMcoConnectTaskScreen` | `com.mojang.realmsclient.gui.screens.RealmsLongRunningMcoTaskScreen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `com.mojang.realmsclient.gui.screens.RealmsLongRunningMcoTaskScreen` | `net.minecraft.realms.RealmsScreen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `com.mojang.realmsclient.gui.screens.RealmsNotificationsScreen` | `net.minecraft.realms.RealmsScreen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `com.mojang.realmsclient.gui.screens.RealmsParentalConsentScreen` | `net.minecraft.realms.RealmsScreen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `com.mojang.realmsclient.gui.screens.RealmsPendingInvitesScreen` | `net.minecraft.realms.RealmsScreen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `com.mojang.realmsclient.gui.screens.RealmsResetWorldScreen` | `net.minecraft.realms.RealmsScreen` | `reference-only` | `textures/gui/realms/adventure.png;textures/gui/realms/experience.png;textures/gui/realms/inspiration.png;textures/gui/realms/new_world.png;textures/gui/realms/survival_spawn.png;textures/gui/realms/upload.png` | `direct-texture-UV` |
| `com.mojang.realmsclient.gui.screens.RealmsSelectFileToUploadScreen` | `net.minecraft.realms.RealmsScreen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `com.mojang.realmsclient.gui.screens.RealmsSelectWorldTemplateScreen` | `net.minecraft.realms.RealmsScreen` | `reference-only` | `widget/slot_frame` | `sprite-stretch` |
| `com.mojang.realmsclient.gui.screens.RealmsTermsScreen` | `net.minecraft.realms.RealmsScreen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `com.mojang.realmsclient.gui.screens.RealmsUploadScreen` | `net.minecraft.realms.RealmsScreen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `com.mojang.realmsclient.gui.screens.configuration.RealmsBackupInfoScreen` | `net.minecraft.realms.RealmsScreen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `com.mojang.realmsclient.gui.screens.configuration.RealmsBackupScreen` | `net.minecraft.realms.RealmsScreen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `com.mojang.realmsclient.gui.screens.configuration.RealmsConfigureWorldScreen` | `net.minecraft.realms.RealmsScreen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `com.mojang.realmsclient.gui.screens.configuration.RealmsInviteScreen` | `net.minecraft.realms.RealmsScreen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `com.mojang.realmsclient.gui.screens.configuration.RealmsPreferredRegionSelectionScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `com.mojang.realmsclient.gui.screens.configuration.RealmsSlotOptionsScreen` | `net.minecraft.realms.RealmsScreen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `AccessibilityOnboardingScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `AlertScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `BackupConfirmScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `ChatScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `ConfirmLinkScreen` | `ConfirmScreen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `ConfirmScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `ConnectScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `CreateBuffetWorldScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `CreateFlatWorldScreen` | `Screen` | `reference-only` | `container/slot` | `sprite-stretch` |
| `CreditsAndAttributionScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `DatapackLoadFailureScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `DeathScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `DirectJoinServerScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `DisconnectedScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `ErrorScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `FileFixerAbortedScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `GenericMessageScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `GenericWaitingScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `InBedChatScreen` | `ChatScreen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `LevelLoadingScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `ManageServerScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `MultiplayerOptionsScreen` | `Screen` | `reference-only` | `textures/gui/inworld_menu_list_background.png` | `direct-texture-UV` |
| `NoticeWithLinkScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `OutOfMemoryScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `PauseScreen` | `Screen` | `pause` | `pause_menu/bug;pause_menu/player_reporting;pause_menu/social_interactions` | `sprite-stretch` |
| `PresetFlatWorldScreen` | `Screen` | `reference-only` | `container/slot` | `sprite-stretch` |
| `PrivacyConfirmLinkScreen` | `ConfirmLinkScreen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `ProgressScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `RecoverWorldDataScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `Screen` | `events.AbstractContainerEventHandler` | `reference-only` | `textures/gui/footer_separator.png;textures/gui/header_separator.png;textures/gui/inworld_footer_separator.png;textures/gui/inworld_header_separator.png;textures/gui/inworld_menu_background.png;textures/gui/menu_background.png` | `direct-texture-UV` |
| `TitleScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `WinScreen` | `Screen` | `reference-only` | `textures/misc/credits_vignette.png` | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `achievement.StatsScreen` | `Screen` | `reference-only` | `container/slot` | `sprite-stretch` |
| `advancements.AdvancementsScreen` | `Screen` | `reference-only` | `textures/gui/advancements/window.png` | `direct-texture-UV` |
| `debug.DebugOptionsScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `debug.GameModeSwitcherScreen` | `Screen` | `reference-only` | `textures/gui/container/gamemode_switcher.png` | `direct-texture-UV` |
| `dialog.ButtonListDialogScreen` | `net.minecraft.server.dialog.ButtonListDialog>` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `dialog.DialogListDialogScreen` | `dialog.ButtonListDialogScreen<net.minecraft.server.dialog.DialogListDialog>` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `dialog.DialogScreen` | `net.minecraft.server.dialog.Dialog>` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `dialog.MultiButtonDialogScreen` | `dialog.ButtonListDialogScreen<net.minecraft.server.dialog.MultiActionDialog>` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `dialog.ServerLinksDialogScreen` | `dialog.ButtonListDialogScreen<net.minecraft.server.dialog.ServerLinksDialog>` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `dialog.SimpleDialogScreen` | `net.minecraft.server.dialog.SimpleDialog>` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `dialog.WaitingForResponseScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `friends.FriendsListConfirmScreen` | `ConfirmScreen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `friends.FriendsOverlayScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `inventory.AbstractCommandBlockEditScreen` | `Screen` | `inventory` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `inventory.AbstractContainerScreen` | `net.minecraft.world.inventory.AbstractContainerMenu>` | `inventory` | `container/slot_highlight_back;container/slot_highlight_front;textures/gui/container/inventory.png` | `direct-texture-UV;sprite-nine_slice` |
| `inventory.AbstractFurnaceScreen` | `net.minecraft.world.inventory.AbstractFurnaceMenu>` | `inventory` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `inventory.AbstractMountInventoryScreen` | `net.minecraft.world.inventory.AbstractMountInventoryMenu>` | `inventory` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `inventory.AbstractRecipeBookScreen` | `net.minecraft.world.inventory.RecipeBookMenu>` | `inventory` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `inventory.AbstractSignEditScreen` | `Screen` | `inventory` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `inventory.AnvilScreen` | `inventory.ItemCombinerScreen<net.minecraft.world.inventory.AnvilMenu>` | `inventory` | `container/anvil/error;container/anvil/text_field;container/anvil/text_field_disabled;textures/gui/container/anvil.png` | `direct-texture-UV;sprite-stretch` |
| `inventory.BeaconScreen` | `inventory.AbstractContainerScreen<net.minecraft.world.inventory.BeaconMenu>` | `inventory` | `container/beacon/button;container/beacon/button_disabled;container/beacon/button_highlighted;container/beacon/button_selected;container/beacon/cancel;container/beacon/confirm;textures/gui/container/beacon.png` | `direct-texture-UV;sprite-stretch` |
| `inventory.BlastFurnaceScreen` | `inventory.AbstractFurnaceScreen<net.minecraft.world.inventory.BlastFurnaceMenu>` | `inventory` | `container/blast_furnace/burn_progress;container/blast_furnace/lit_progress;textures/gui/container/blast_furnace.png` | `direct-texture-UV;sprite-stretch` |
| `inventory.BookEditScreen` | `Screen` | `inventory` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `inventory.BookSignScreen` | `Screen` | `inventory` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `inventory.BookViewScreen` | `Screen` | `inventory` | `textures/gui/book.png` | `direct-texture-UV` |
| `inventory.BrewingStandScreen` | `inventory.AbstractContainerScreen<net.minecraft.world.inventory.BrewingStandMenu>` | `inventory` | `container/brewing_stand/brew_progress;container/brewing_stand/bubbles;container/brewing_stand/fuel_length;textures/gui/container/brewing_stand.png` | `direct-texture-UV;sprite-stretch` |
| `inventory.CartographyTableScreen` | `inventory.AbstractContainerScreen<net.minecraft.world.inventory.CartographyTableMenu>` | `inventory` | `container/cartography_table/duplicated_map;container/cartography_table/error;container/cartography_table/locked;container/cartography_table/map;container/cartography_table/scaled_map;textures/gui/container/cartography_table.png` | `direct-texture-UV;sprite-stretch` |
| `inventory.CommandBlockEditScreen` | `inventory.AbstractCommandBlockEditScreen` | `inventory` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `inventory.ContainerScreen` | `inventory.AbstractContainerScreen<net.minecraft.world.inventory.ChestMenu>` | `inventory` | `textures/gui/container/generic_54.png` | `direct-texture-UV` |
| `inventory.CrafterScreen` | `inventory.AbstractContainerScreen<net.minecraft.world.inventory.CrafterMenu>` | `inventory` | `container/crafter/disabled_slot;container/crafter/powered_redstone;container/crafter/unpowered_redstone;textures/gui/container/crafter.png` | `direct-texture-UV;sprite-stretch` |
| `inventory.CraftingScreen` | `inventory.AbstractRecipeBookScreen<net.minecraft.world.inventory.CraftingMenu>` | `inventory` | `textures/gui/container/crafting_table.png` | `direct-texture-UV` |
| `inventory.CreativeModeInventoryScreen` | `inventory.AbstractContainerScreen<inventory.CreativeModeInventoryScreen$ItemPickerMenu>` | `inventory` | `container/creative_inventory/scroller;container/creative_inventory/scroller_disabled;container/creative_inventory/tab_bottom_selected_1;container/creative_inventory/tab_bottom_selected_2;container/creative_inventory/tab_bottom_selected_3;container/creative_inventory/tab_bottom_selected_4;container/creative_inventory/tab_bottom_selected_5;container/creative_inventory/tab_bottom_selected_6;container/creative_inventory/tab_bottom_selected_7;container/creative_inventory/tab_bottom_unselected_1;container/creative_inventory/tab_bottom_unselected_2;container/creative_inventory/tab_bottom_unselected_3;container/creative_inventory/tab_bottom_unselected_4;container/creative_inventory/tab_bottom_unselected_5;container/creative_inventory/tab_bottom_unselected_6;container/creative_inventory/tab_bottom_unselected_7;container/creative_inventory/tab_top_selected_1;container/creative_inventory/tab_top_selected_2;container/creative_inventory/tab_top_selected_3;container/creative_inventory/tab_top_selected_4;container/creative_inventory/tab_top_selected_5;container/creative_inventory/tab_top_selected_6;container/creative_inventory/tab_top_selected_7;container/creative_inventory/tab_top_unselected_1;container/creative_inventory/tab_top_unselected_2;container/creative_inventory/tab_top_unselected_3;container/creative_inventory/tab_top_unselected_4;container/creative_inventory/tab_top_unselected_5;container/creative_inventory/tab_top_unselected_6;container/creative_inventory/tab_top_unselected_7` | `sprite-stretch` |
| `inventory.DispenserScreen` | `inventory.AbstractContainerScreen<net.minecraft.world.inventory.DispenserMenu>` | `inventory` | `textures/gui/container/dispenser.png` | `direct-texture-UV` |
| `inventory.EnchantmentScreen` | `inventory.AbstractContainerScreen<net.minecraft.world.inventory.EnchantmentMenu>` | `inventory` | `container/enchanting_table/enchantment_slot;container/enchanting_table/enchantment_slot_disabled;container/enchanting_table/enchantment_slot_highlighted;container/enchanting_table/level_1;container/enchanting_table/level_1_disabled;container/enchanting_table/level_2;container/enchanting_table/level_2_disabled;container/enchanting_table/level_3;container/enchanting_table/level_3_disabled;textures/entity/enchantment/enchanting_table_book.png;textures/gui/container/enchanting_table.png` | `direct-texture-UV;sprite-stretch` |
| `inventory.FurnaceScreen` | `inventory.AbstractFurnaceScreen<net.minecraft.world.inventory.FurnaceMenu>` | `inventory` | `container/furnace/burn_progress;container/furnace/lit_progress;textures/gui/container/furnace.png` | `direct-texture-UV;sprite-stretch` |
| `inventory.GrindstoneScreen` | `inventory.AbstractContainerScreen<net.minecraft.world.inventory.GrindstoneMenu>` | `inventory` | `container/grindstone/error;textures/gui/container/grindstone.png` | `direct-texture-UV;sprite-stretch` |
| `inventory.HangingSignEditScreen` | `inventory.AbstractSignEditScreen` | `inventory` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `inventory.HopperScreen` | `inventory.AbstractContainerScreen<net.minecraft.world.inventory.HopperMenu>` | `inventory` | `textures/gui/container/hopper.png` | `direct-texture-UV` |
| `inventory.HorseInventoryScreen` | `inventory.AbstractMountInventoryScreen<net.minecraft.world.inventory.HorseInventoryMenu>` | `inventory` | `container/horse/chest_slots;container/slot;textures/gui/container/horse.png` | `direct-texture-UV;sprite-stretch` |
| `inventory.InventoryScreen` | `inventory.AbstractRecipeBookScreen<net.minecraft.world.inventory.InventoryMenu>` | `inventory` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `inventory.ItemCombinerScreen` | `net.minecraft.world.inventory.ItemCombinerMenu>` | `inventory` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `inventory.JigsawBlockEditScreen` | `Screen` | `inventory` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `inventory.LecternScreen` | `inventory.BookViewScreen` | `inventory` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `inventory.LoomScreen` | `inventory.AbstractContainerScreen<net.minecraft.world.inventory.LoomMenu>` | `inventory` | `container/loom/error;container/loom/pattern;container/loom/pattern_highlighted;container/loom/pattern_selected;container/loom/scroller;container/loom/scroller_disabled;container/slot/banner;container/slot/banner_pattern;container/slot/dye;textures/gui/container/loom.png` | `direct-texture-UV;sprite-stretch` |
| `inventory.MerchantScreen` | `inventory.AbstractContainerScreen<net.minecraft.world.inventory.MerchantMenu>` | `inventory` | `container/villager/discount_strikethrough;container/villager/experience_bar_background;container/villager/experience_bar_current;container/villager/experience_bar_result;container/villager/out_of_stock;container/villager/scroller;container/villager/scroller_disabled;container/villager/trade_arrow;container/villager/trade_arrow_out_of_stock;textures/gui/container/villager.png` | `direct-texture-UV;sprite-stretch` |
| `inventory.MinecartCommandBlockEditScreen` | `inventory.AbstractCommandBlockEditScreen` | `inventory` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `inventory.NautilusInventoryScreen` | `inventory.AbstractMountInventoryScreen<net.minecraft.world.inventory.NautilusInventoryMenu>` | `inventory` | `container/slot;textures/gui/container/nautilus.png` | `direct-texture-UV;sprite-stretch` |
| `inventory.ShulkerBoxScreen` | `inventory.AbstractContainerScreen<net.minecraft.world.inventory.ShulkerBoxMenu>` | `inventory` | `textures/gui/container/shulker_box.png` | `direct-texture-UV` |
| `inventory.SignEditScreen` | `inventory.AbstractSignEditScreen` | `inventory` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `inventory.SmithingScreen` | `inventory.ItemCombinerScreen<net.minecraft.world.inventory.SmithingMenu>` | `inventory` | `container/slot/smithing_template_armor_trim;container/slot/smithing_template_netherite_upgrade;container/smithing/error;textures/gui/container/smithing.png` | `direct-texture-UV;sprite-stretch` |
| `inventory.SmokerScreen` | `inventory.AbstractFurnaceScreen<net.minecraft.world.inventory.SmokerMenu>` | `inventory` | `container/smoker/burn_progress;container/smoker/lit_progress;textures/gui/container/smoker.png` | `direct-texture-UV;sprite-stretch` |
| `inventory.StonecutterScreen` | `inventory.AbstractContainerScreen<net.minecraft.world.inventory.StonecutterMenu>` | `inventory` | `container/stonecutter/recipe;container/stonecutter/recipe_highlighted;container/stonecutter/recipe_selected;container/stonecutter/scroller;container/stonecutter/scroller_disabled;textures/gui/container/stonecutter.png` | `direct-texture-UV;sprite-stretch` |
| `inventory.StructureBlockEditScreen` | `Screen` | `inventory` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `inventory.TestBlockEditScreen` | `Screen` | `inventory` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `inventory.TestInstanceBlockEditScreen` | `Screen` | `inventory` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `multiplayer.CodeOfConductScreen` | `multiplayer.WarningScreen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `multiplayer.JoinMultiplayerScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `multiplayer.RestrictionsScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `multiplayer.SafetyScreen` | `multiplayer.WarningScreen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `multiplayer.ServerReconfigScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `multiplayer.WarningScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `options.AccessibilityOptionsScreen` | `options.OptionsSubScreen` | `options` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `options.ChatOptionsScreen` | `options.OptionsSubScreen` | `options` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `options.FontOptionsScreen` | `options.OptionsSubScreen` | `options` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `options.InWorldGameRulesScreen` | `worldselection.AbstractGameRulesScreen` | `options` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `options.LanguageSelectScreen` | `options.OptionsSubScreen` | `options` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `options.MouseSettingsScreen` | `options.OptionsSubScreen` | `options` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `options.OnlineOptionsScreen` | `options.OptionsSubScreen` | `options` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `options.OptionsScreen` | `Screen` | `options` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `options.OptionsSubScreen` | `Screen` | `options` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `options.SkinCustomizationScreen` | `options.OptionsSubScreen` | `options` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `options.SoundOptionsScreen` | `options.OptionsSubScreen` | `options` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `options.UnsupportedGraphicsWarningScreen` | `Screen` | `options` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `options.VideoSettingsScreen` | `options.OptionsSubScreen` | `options` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `options.WorldOptionsScreen` | `Screen` | `options` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `options.controls.ControlsScreen` | `options.OptionsSubScreen` | `options` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `options.controls.KeyBindsScreen` | `options.OptionsSubScreen` | `options` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `packs.PackSelectionScreen` | `Screen` | `reference-only` | `pack.png;textures/misc/unknown_pack.png` | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `reporting.AbstractReportScreen` | `net.minecraft.client.multiplayer.chat.report.Report$Builder<` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `reporting.ChatReportScreen` | `reporting.AbstractReportScreen<net.minecraft.client.multiplayer.chat.report.ChatReport$Builder>` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `reporting.ChatSelectionScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `reporting.NameReportScreen` | `reporting.AbstractReportScreen<net.minecraft.client.multiplayer.chat.report.NameReport$Builder>` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `reporting.ReportPlayerScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `reporting.ReportReasonSelectionScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `reporting.SkinReportScreen` | `reporting.AbstractReportScreen<net.minecraft.client.multiplayer.chat.report.SkinReport$Builder>` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `social.SocialInteractionsScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `telemetry.TelemetryInfoScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `worldselection.AbstractGameRulesScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `worldselection.ConfirmExperimentalFeaturesScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `worldselection.CreateWorldScreen` | `Screen` | `reference-only` | `textures/gui/tab_header_background.png` | `direct-texture-UV` |
| `worldselection.EditWorldScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `worldselection.ExperimentsScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `worldselection.FileFixerProgressScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `worldselection.OptimizeWorldScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `worldselection.SelectWorldScreen` | `Screen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `worldselection.WorldCreationGameRulesScreen` | `worldselection.AbstractGameRulesScreen` | `reference-only` | — | `code/inherited/shared-widgets; see bytecode and AUDIT.md` |
| `net.minecraft.realms.RealmsScreen` | `Screen` | `reference-only` | `textures/gui/title/realms.png` | `direct-texture-UV` |
