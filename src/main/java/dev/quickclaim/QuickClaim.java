package dev.quickclaim;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.*;

@Mod(QuickClaim.ID)
public final class QuickClaim {

    public static final String ID = "quickclaim";

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ID);

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ID);

    public static final DeferredRegister<BlockEntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ID);

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, ID);

    public static final RegistryObject<Item> WAND =
            ITEMS.register("claim_wand", () -> new ClaimWand(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Block> CLAIM_BLOCK =
            BLOCKS.register(
                    "claim_block",
                    () ->
                            new ClaimBlock(
                                    BlockBehaviour.Properties.copy(Blocks.LECTERN)
                                            .strength(4, 1200)
                                            .noOcclusion()));

    public static final RegistryObject<Item> CLAIM_BLOCK_ITEM =
            ITEMS.register(
                    "claim_block", () -> new BlockItem(CLAIM_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<BlockEntityType<ClaimBlock.Entity>> CLAIM_BLOCK_ENTITY =
            ENTITIES.register(
                    "claim_block",
                    () ->
                            BlockEntityType.Builder.of(ClaimBlock.Entity::new, CLAIM_BLOCK.get())
                                    .build(null));

    public static final RegistryObject<MenuType<PaymentMenu>> PAYMENT_MENU =
            MENUS.register(
                    "payment_settings",
                    () ->
                            IForgeMenuType.create(
                                    (id, inv, buf) -> new PaymentMenu(id, inv, buf.readNbt())));

    public QuickClaim(FMLJavaModLoadingContext context) {
        IEventBus bus = context.getModEventBus();
        ITEMS.register(bus);
        BLOCKS.register(bus);
        ENTITIES.register(bus);
        MENUS.register(bus);
        bus.addListener(this::creative);
        bus.addListener(
                (net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) ->
                        event.enqueueWork(CarryCompat::register));
        MinecraftForge.EVENT_BUS.register(new ProtectionEvents());
        MinecraftForge.EVENT_BUS.addListener(ClaimCommands::register);
        Network.init();
    }

    private void creative(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            e.accept(WAND);
            e.accept(CLAIM_BLOCK_ITEM);
        }
    }
}
