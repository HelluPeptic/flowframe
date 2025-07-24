package com.flowframe.mixin;

import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mixin(com.berksire.furniture.item.TrashBagItem.class)
public class MixinTrashBagItem {
    private static final Set<String> BLACKLIST = Set.of(
        "minecraft:air",
        "minecraft:bedrock",
        "minecraft:barrier",
        "minecraft:command_block",
        "minecraft:command_block_minecart",
        "minecraft:structure_block",
        "minecraft:structure_void",
        "minecraft:repeating_command_block",
        "minecraft:chain_command_block",
        "minecraft:light_block",
        "minecraft:light",
        "majruszsaccessories:booster_overlay_single",
        "majruszsaccessories:booster_overlay_double",
        "majruszsaccessories:booster_overlay_tripple",
        "lootr:lootr_barrel",
        "lootr:lootr_chest",
        "lootr:inventory",
        "lootr:traped_chest",
        "lootr:shulker",
        "dungeonnowloading:chaos_spawner_barrier_center",
        "dungeonnowloading:chaos_spawner_barrier_edge",
        "dungeonnowloading:chaos_spawner_barrier_vertex",
        "dungeonnowloading:chaos_spawner_broken_diamond_edge",
        "dungeonnowloading:chaos_spawner_broken_diamond_vertex",
        "dungeonnowloading:chaos_spawner_broken_edge",
        "dungeonnowloading:chaos_spawner_diamond_edge",
        "dungeonnowloading:chaos_spawner_diamond_vertex",
        "dungeonnowloading:chaos_spawner_edge",
        "lilis_lucky_lures:floating_debris",
        "lilis_lucky_lures:floating_books",
        "lilis_lucky_lures:river_fish_pool",
        "lilis_lucky_lures:ocean_fish_pool",
        "wildernature:uncommon_contract",
        "wildernature:leveling_contract",
        "wildernature:rare_contract",
        "aquamirae:frozen_chest",
        "supplementaries:lumisene_bottle",
        "supplementaries:lumisene_bucket",
        "biomesoplenty:blood",
        "biomesoplenty:liquid_null",
        "biomesoplenty:liquid_null_bucket",
        "minecraft:end_portal_frame",
        "minecraft:end_gateway",
        "minecraft:end_portal",
        "minecraft:nether_portal"
    );

    @Redirect(
        method = "use",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/stream/Stream;toList()Ljava/util/List;"
        )
    )
    private List<Item> filterBlacklistedItems(java.util.stream.Stream<Item> stream) {
        return stream
            .filter(item -> {
                    Identifier id = Registries.ITEM.getId(item);
                return id != null && !BLACKLIST.contains(id.toString());
            })
            .collect(Collectors.toList());
    }
}
