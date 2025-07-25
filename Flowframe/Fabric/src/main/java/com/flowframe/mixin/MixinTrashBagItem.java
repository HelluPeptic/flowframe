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
        "minecraft:dragon_egg",
        "minecraft:end_portal_frame",
        "minecraft:end_gateway",
        "minecraft:end_portal",
        "minecraft:nether_portal",
        "minecraft:elytra",
        "minecraft:warden_spawn_egg",
        "minecraft:debug_stick",
        "minecraft:debug_stick",
        "majruszsaccessories:booster_overlay_single",
        "majruszsaccessories:booster_overlay_double",
        "majruszsaccessories:booster_overlay_tripple",
        "majruszsaccessories:adventurer_kit",
        "majruszsaccessories:adventurer_rune",
        "majruszsaccessories:ancient_scarab",
        "majruszsaccessories:angler_rune",
        "majruszsaccessories:angler_trophy",
        "majruszsaccessories:certificate_of_taming",
        "majruszsaccessories:dice",
        "majruszsaccessories:discount_voucher",
        "majruszsaccessories:dream_catcher",
        "majruszsaccessories:gambling_card",
        "majruszsaccessories:golden_dice",
        "majruszsaccessories:golden_horseshoe",
        "majruszsaccessories:horseshoe",
        "majruszsaccessories:household_rune",
        "majruszsaccessories:idol_of_fertility",
        "majruszsaccessories:lucky_rock",
        "majruszsaccessories:metal_lure",
        "majruszsaccessories:miner_guide",
        "majruszsaccessories:miner_rune",
        "majruszsaccessories:nature_rune",
        "majruszsaccessories:onyx",
        "majruszsaccessories:owl_feather",
        "majruszsaccessories:removal_card",
        "majruszsaccessories:reverse_card",
        "majruszsaccessories:secret_ingredient",
        "majruszsaccessories:soul_of_minecraft",
        "majruszsaccessories:swimmer_guide",
        "majruszsaccessories:tamed_potato_beetle",
        "majruszsaccessories:tool_scraps",
        "majruszsaccessories:unbreakable_fishing_line",
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
        "simplyskills:gracious_manuscript",
        "betterend:elytra_crystalite",
        "betterend:elytra_armored",
        "storagedrawers:creative_storage_upgrade",
        "storagedrawers:creative_vending_upgrade",
        "simplyswords:runefused_gem",
        "simplyswords:netherfused_gem",
        "simplyswords:empowered_remnant",
        "simplyswords:the_watcher",
        "simplyswords:storms_edge",
        "simplyswords:stormbringer",
        "simplyswords:bramblethorn",
        "simplyswords:watching_warglaive",
        "simplyswords:longsword_of_the_plague",
        "simplyswords:emberblade",
        "simplyswords:hearthflame",
        "simplyswords:soulkeeper",
        "simplyswords:twisted_blade",
        "simplyswords:soulstealer",
        "simplyswords:soulrender",
        "simplyswords:soul_pyre",
        "simplyswords:frostfall",
        "simplyswords:molten_edge",
        "simplyswords:livyatan",
        "simplyswords:icewhisper",
        "simplyswords:arcanethyst",
        "simplyswords:thunderbrand",
        "simplyswords:mjolnir",
        "simplyswords:slumbering_lichblade",
        "simplyswords:waking_lichblade",
        "simplyswords:awaked_lichblade",
        "simplyswords:shadowsting",
        "simplyswords:righteous_relic",
        "simplyswords:tainted_relic",
        "simplyswords:sunfire",
        "simplyswords:harbringer",
        "simplyswords:whisperwind",
        "simplyswords:emberlash",
        "simplyswords:waxweaver",
        "simplyswords:hiveheart",
        "simplyswords:stars_edge",
        "simplyswords:whickpiercer",
        "simplyswords:tempest",
        "simplyswords:flamewind",
        "simplyswords:ribboncleaver",
        "simplyswords:magiscythe",
        "simplyswords:enigma",
        "simplyswords:magispear",
        "simplyswords:magiblade",
        "simplyswords:caelestis"
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
