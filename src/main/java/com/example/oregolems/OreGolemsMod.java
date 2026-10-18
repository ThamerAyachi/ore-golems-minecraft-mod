package com.example.oregolems;

import com.example.oregolems.block.RedstoneGolemSignalBlock;
import com.example.oregolems.client.OreGolemModel;
import com.example.oregolems.client.OreGolemRenderer;
import com.example.oregolems.entity.AbstractOreGolemEntity;
import com.example.oregolems.entity.variant.CoalGolemEntity;
import com.example.oregolems.entity.variant.DiamondGolemEntity;
import com.example.oregolems.entity.variant.EmeraldGolemEntity;
import com.example.oregolems.entity.variant.GoldGolemEntity;
import com.example.oregolems.entity.variant.LapisGolemEntity;
import com.example.oregolems.entity.variant.MiniIronGolemEntity;
import com.example.oregolems.entity.variant.NetheriteGolemEntity;
import com.example.oregolems.entity.variant.RedstoneGolemEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

/**
 * Main Mod Entry Point for Ore Golems Expansion (oregolems).
 */
@Mod(OreGolemsMod.MODID)
public final class OreGolemsMod {
    public static final String MODID = "oregolems";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // Invisible Redstone Signal Block for the Redstone Golem
    public static final RegistryObject<Block> REDSTONE_GOLEM_SIGNAL = BLOCKS.register("redstone_golem_signal",
            () -> new RedstoneGolemSignalBlock(BlockBehaviour.Properties.of()
                    .setId(BLOCKS.key("redstone_golem_signal"))
                    .replaceable()
                    .noCollision()
                    .noLootTable()
                    .air())
    );

    // ==================== ENTITY TYPES ====================

    public static final RegistryObject<EntityType<CoalGolemEntity>> COAL_GOLEM =
            ENTITY_TYPES.register("coal_golem", () -> EntityType.Builder
                    .of(CoalGolemEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.95F)
                    .clientTrackingRange(8)
                    .build(ENTITY_TYPES.key("coal_golem")));

    public static final RegistryObject<EntityType<MiniIronGolemEntity>> MINI_IRON_GOLEM =
            ENTITY_TYPES.register("mini_iron_golem", () -> EntityType.Builder
                    .of(MiniIronGolemEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.95F)
                    .clientTrackingRange(8)
                    .build(ENTITY_TYPES.key("mini_iron_golem")));

    public static final RegistryObject<EntityType<GoldGolemEntity>> GOLD_GOLEM =
            ENTITY_TYPES.register("gold_golem", () -> EntityType.Builder
                    .of(GoldGolemEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.95F)
                    .clientTrackingRange(8)
                    .build(ENTITY_TYPES.key("gold_golem")));

    public static final RegistryObject<EntityType<EmeraldGolemEntity>> EMERALD_GOLEM =
            ENTITY_TYPES.register("emerald_golem", () -> EntityType.Builder
                    .of(EmeraldGolemEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.95F)
                    .clientTrackingRange(8)
                    .build(ENTITY_TYPES.key("emerald_golem")));

    public static final RegistryObject<EntityType<RedstoneGolemEntity>> REDSTONE_GOLEM =
            ENTITY_TYPES.register("redstone_golem", () -> EntityType.Builder
                    .of(RedstoneGolemEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.95F)
                    .clientTrackingRange(8)
                    .build(ENTITY_TYPES.key("redstone_golem")));

    public static final RegistryObject<EntityType<LapisGolemEntity>> LAPIS_GOLEM =
            ENTITY_TYPES.register("lapis_golem", () -> EntityType.Builder
                    .of(LapisGolemEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.95F)
                    .clientTrackingRange(8)
                    .build(ENTITY_TYPES.key("lapis_golem")));

    public static final RegistryObject<EntityType<DiamondGolemEntity>> DIAMOND_GOLEM =
            ENTITY_TYPES.register("diamond_golem", () -> EntityType.Builder
                    .of(DiamondGolemEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.95F)
                    .clientTrackingRange(8)
                    .build(ENTITY_TYPES.key("diamond_golem")));

    public static final RegistryObject<EntityType<NetheriteGolemEntity>> NETHERITE_GOLEM =
            ENTITY_TYPES.register("netherite_golem", () -> EntityType.Builder
                    .of(NetheriteGolemEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.95F)
                    .fireImmune()
                    .clientTrackingRange(8)
                    .build(ENTITY_TYPES.key("netherite_golem")));

    // ==================== SPAWN EGG ITEMS ====================

    public static final RegistryObject<Item> COAL_GOLEM_SPAWN_EGG = ITEMS.register("coal_golem_spawn_egg",
            () -> new SpawnEggItem(new Item.Properties()
                    .setId(ITEMS.key("coal_golem_spawn_egg"))
                    .spawnEgg(COAL_GOLEM.get())));

    public static final RegistryObject<Item> MINI_IRON_GOLEM_SPAWN_EGG = ITEMS.register("mini_iron_golem_spawn_egg",
            () -> new SpawnEggItem(new Item.Properties()
                    .setId(ITEMS.key("mini_iron_golem_spawn_egg"))
                    .spawnEgg(MINI_IRON_GOLEM.get())));

    public static final RegistryObject<Item> GOLD_GOLEM_SPAWN_EGG = ITEMS.register("gold_golem_spawn_egg",
            () -> new SpawnEggItem(new Item.Properties()
                    .setId(ITEMS.key("gold_golem_spawn_egg"))
                    .spawnEgg(GOLD_GOLEM.get())));

    public static final RegistryObject<Item> EMERALD_GOLEM_SPAWN_EGG = ITEMS.register("emerald_golem_spawn_egg",
            () -> new SpawnEggItem(new Item.Properties()
                    .setId(ITEMS.key("emerald_golem_spawn_egg"))
                    .spawnEgg(EMERALD_GOLEM.get())));

    public static final RegistryObject<Item> REDSTONE_GOLEM_SPAWN_EGG = ITEMS.register("redstone_golem_spawn_egg",
            () -> new SpawnEggItem(new Item.Properties()
                    .setId(ITEMS.key("redstone_golem_spawn_egg"))
                    .spawnEgg(REDSTONE_GOLEM.get())));

    public static final RegistryObject<Item> LAPIS_GOLEM_SPAWN_EGG = ITEMS.register("lapis_golem_spawn_egg",
            () -> new SpawnEggItem(new Item.Properties()
                    .setId(ITEMS.key("lapis_golem_spawn_egg"))
                    .spawnEgg(LAPIS_GOLEM.get())));

    public static final RegistryObject<Item> DIAMOND_GOLEM_SPAWN_EGG = ITEMS.register("diamond_golem_spawn_egg",
            () -> new SpawnEggItem(new Item.Properties()
                    .setId(ITEMS.key("diamond_golem_spawn_egg"))
                    .spawnEgg(DIAMOND_GOLEM.get())));

    public static final RegistryObject<Item> NETHERITE_GOLEM_SPAWN_EGG = ITEMS.register("netherite_golem_spawn_egg",
            () -> new SpawnEggItem(new Item.Properties()
                    .setId(ITEMS.key("netherite_golem_spawn_egg"))
                    .fireResistant()
                    .spawnEgg(NETHERITE_GOLEM.get())));

    // ==================== CREATIVE MODE TAB ====================

    public static final net.minecraft.resources.ResourceKey<CreativeModeTab> SPAWN_EGGS_TAB_KEY =
            net.minecraft.resources.ResourceKey.create(Registries.CREATIVE_MODE_TAB, net.minecraft.resources.Identifier.withDefaultNamespace("spawn_eggs"));

    public static final RegistryObject<CreativeModeTab> ORE_GOLEMS_TAB = CREATIVE_MODE_TABS.register("ore_golems_tab",
            () -> CreativeModeTab.builder()
                    .withTabsBefore(SPAWN_EGGS_TAB_KEY)
                    .title(Component.translatable("itemGroup.oregolems"))
                    .icon(() -> DIAMOND_GOLEM_SPAWN_EGG.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(COAL_GOLEM_SPAWN_EGG.get());
                        output.accept(MINI_IRON_GOLEM_SPAWN_EGG.get());
                        output.accept(GOLD_GOLEM_SPAWN_EGG.get());
                        output.accept(EMERALD_GOLEM_SPAWN_EGG.get());
                        output.accept(REDSTONE_GOLEM_SPAWN_EGG.get());
                        output.accept(LAPIS_GOLEM_SPAWN_EGG.get());
                        output.accept(DIAMOND_GOLEM_SPAWN_EGG.get());
                        output.accept(NETHERITE_GOLEM_SPAWN_EGG.get());
                    })
                    .build());

    public OreGolemsMod(FMLJavaModLoadingContext context) {
        var modBusGroup = context.getModBusGroup();

        BLOCKS.register(modBusGroup);
        ENTITY_TYPES.register(modBusGroup);
        ITEMS.register(modBusGroup);
        CREATIVE_MODE_TABS.register(modBusGroup);

        EntityAttributeCreationEvent.BUS.addListener(OreGolemsMod::onEntityAttributeCreation);
        BuildCreativeModeTabContentsEvent.BUS.addListener(OreGolemsMod::addCreativeTabItems);
        BlockEvent.EntityPlaceEvent.BUS.addListener(OreGolemsMod::onBlockPlaced);

        LOGGER.info("Ore Golems Expansion initialized!");
    }

    private static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(COAL_GOLEM.get(), CoalGolemEntity.createAttributes().build());
        event.put(MINI_IRON_GOLEM.get(), MiniIronGolemEntity.createAttributes().build());
        event.put(GOLD_GOLEM.get(), GoldGolemEntity.createAttributes().build());
        event.put(EMERALD_GOLEM.get(), EmeraldGolemEntity.createAttributes().build());
        event.put(REDSTONE_GOLEM.get(), RedstoneGolemEntity.createAttributes().build());
        event.put(LAPIS_GOLEM.get(), LapisGolemEntity.createAttributes().build());
        event.put(DIAMOND_GOLEM.get(), DiamondGolemEntity.createAttributes().build());
        event.put(NETHERITE_GOLEM.get(), NetheriteGolemEntity.createAttributes().build());
    }

    private static void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(SPAWN_EGGS_TAB_KEY)) {
            event.accept(COAL_GOLEM_SPAWN_EGG);
            event.accept(MINI_IRON_GOLEM_SPAWN_EGG);
            event.accept(GOLD_GOLEM_SPAWN_EGG);
            event.accept(EMERALD_GOLEM_SPAWN_EGG);
            event.accept(REDSTONE_GOLEM_SPAWN_EGG);
            event.accept(LAPIS_GOLEM_SPAWN_EGG);
            event.accept(DIAMOND_GOLEM_SPAWN_EGG);
            event.accept(NETHERITE_GOLEM_SPAWN_EGG);
        }
    }

    /**
     * Detects when a Carved Pumpkin (or Jack o'Lantern) is placed on top of an Ore Block
     * to summon the matching miniature Ore Golem in the world.
     */
    private static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockState placedState = event.getPlacedBlock();
        if (!placedState.is(Blocks.CARVED_PUMPKIN) && !placedState.is(Blocks.JACK_O_LANTERN)) {
            return;
        }

        BlockPos pumpkinPos = event.getPos();
        BlockPos basePos = pumpkinPos.below();
        BlockState baseState = serverLevel.getBlockState(basePos);

        // Verify the pumpkin wasn't already consumed by a vanilla Snow/Iron golem pattern
        if (serverLevel.getBlockState(pumpkinPos).isAir() || baseState.isAir()) {
            return;
        }

        EntityType<? extends AbstractOreGolemEntity> golemType = resolveGolemTypeForBaseBlock(baseState);
        if (golemType == null) {
            return;
        }

        AbstractOreGolemEntity golem = golemType.create(serverLevel, EntitySpawnReason.MOB_SUMMONED);
        if (golem == null) {
            return;
        }

        // Clear both blocks and play destroy particles, then replace the base Ore Block with a Copper Chest
        net.minecraft.core.Direction facing = placedState.hasProperty(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)
                ? placedState.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)
                : net.minecraft.core.Direction.NORTH;

        serverLevel.setBlock(pumpkinPos, Blocks.AIR.defaultBlockState(), 2);
        serverLevel.setBlock(basePos, Blocks.AIR.defaultBlockState(), 2);
        serverLevel.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pumpkinPos, Block.getId(placedState));
        serverLevel.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, basePos, Block.getId(baseState));

        BlockState chestState = net.minecraft.world.level.block.CopperChestBlock.getFromCopperBlock(
                Blocks.COPPER_BLOCK, facing, serverLevel, basePos
        );
        serverLevel.setBlock(basePos, chestState, 2);

        BlockPos spawnBlockPos = AbstractOreGolemEntity.findAdjacentWalkablePos(serverLevel, basePos, facing);
        double spawnY = spawnBlockPos.equals(pumpkinPos) ? pumpkinPos.getY() : spawnBlockPos.getY();

        float yaw = facing.toYRot();
        golem.snapTo(spawnBlockPos.getX() + 0.5D, spawnY, spawnBlockPos.getZ() + 0.5D, yaw, 0.0F);
        golem.setHomeChestPos(basePos);
        golem.onSummonedWithDirection(facing);
        serverLevel.addFreshEntity(golem);

        serverLevel.updateNeighborsAt(pumpkinPos, Blocks.AIR);
        serverLevel.updateNeighborsAt(basePos, chestState.getBlock());
        serverLevel.playSound(null, basePos, SoundEvents.COPPER_GOLEM_SPAWN, SoundSource.BLOCKS, 1.0F, 1.0F);

        for (ServerPlayer player : serverLevel.getEntitiesOfClass(ServerPlayer.class, golem.getBoundingBox().inflate(5.0D))) {
            CriteriaTriggers.SUMMONED_ENTITY.trigger(player, golem);
        }
    }

    private static EntityType<? extends AbstractOreGolemEntity> resolveGolemTypeForBaseBlock(BlockState state) {
        if (state.is(Blocks.COAL_BLOCK) || state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE)) {
            return COAL_GOLEM.get();
        }
        if (state.is(Blocks.IRON_BLOCK) || state.is(Blocks.IRON_ORE) || state.is(Blocks.DEEPSLATE_IRON_ORE) || state.is(Blocks.RAW_IRON_BLOCK)) {
            return MINI_IRON_GOLEM.get();
        }
        if (state.is(Blocks.GOLD_BLOCK) || state.is(Blocks.GOLD_ORE) || state.is(Blocks.DEEPSLATE_GOLD_ORE) || state.is(Blocks.RAW_GOLD_BLOCK)) {
            return GOLD_GOLEM.get();
        }
        if (state.is(Blocks.EMERALD_BLOCK) || state.is(Blocks.EMERALD_ORE) || state.is(Blocks.DEEPSLATE_EMERALD_ORE)) {
            return EMERALD_GOLEM.get();
        }
        if (state.is(Blocks.REDSTONE_BLOCK) || state.is(Blocks.REDSTONE_ORE) || state.is(Blocks.DEEPSLATE_REDSTONE_ORE)) {
            return REDSTONE_GOLEM.get();
        }
        if (state.is(Blocks.LAPIS_BLOCK) || state.is(Blocks.LAPIS_ORE) || state.is(Blocks.DEEPSLATE_LAPIS_ORE)) {
            return LAPIS_GOLEM.get();
        }
        if (state.is(Blocks.DIAMOND_BLOCK) || state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)) {
            return DIAMOND_GOLEM.get();
        }
        if (state.is(Blocks.NETHERITE_BLOCK) || state.is(Blocks.ANCIENT_DEBRIS)) {
            return NETHERITE_GOLEM.get();
        }
        return null;
    }

    @Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
    public static final class ClientModEvents {
        @SubscribeEvent
        public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(OreGolemModel.LAYER_LOCATION, OreGolemModel::createBodyLayer);
        }

        @SubscribeEvent
        public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(COAL_GOLEM.get(), OreGolemRenderer::new);
            event.registerEntityRenderer(MINI_IRON_GOLEM.get(), OreGolemRenderer::new);
            event.registerEntityRenderer(GOLD_GOLEM.get(), OreGolemRenderer::new);
            event.registerEntityRenderer(EMERALD_GOLEM.get(), OreGolemRenderer::new);
            event.registerEntityRenderer(REDSTONE_GOLEM.get(), OreGolemRenderer::new);
            event.registerEntityRenderer(LAPIS_GOLEM.get(), OreGolemRenderer::new);
            event.registerEntityRenderer(DIAMOND_GOLEM.get(), OreGolemRenderer::new);
            event.registerEntityRenderer(NETHERITE_GOLEM.get(), OreGolemRenderer::new);
        }
    }
}
