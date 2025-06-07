package de.erdbeerbaerlp.dcintegration.architectury.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dcshadow.net.kyori.adventure.text.NBTComponent;
import de.erdbeerbaerlp.dcintegration.architectury.util.accessors.ShowInTooltipAccessor;
import de.erdbeerbaerlp.dcintegration.common.DiscordIntegration;
import de.erdbeerbaerlp.dcintegration.common.storage.Configuration;
import de.erdbeerbaerlp.dcintegration.common.util.MessageUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.NbtTagArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

import java.util.Arrays;

public class ArchitecturyMessageUtils extends MessageUtils {
    public static String formatPlayerName(ServerPlayer player) {
        if (player.getTabListDisplayName() != null)
            return ChatFormatting.stripFormatting(player.getTabListDisplayName().getString());
        else
            return ChatFormatting.stripFormatting(player.getName().getString());
    }

    public static MessageEmbed genItemStackEmbedIfAvailable(final Component component) {
        if (!Configuration.instance().forgeSpecific.sendItemInfo) return null;
        JsonObject json;
        try {
            final JsonElement jsonElement = JsonParser.parseString(Component.Serializer.toJson(component));
            if (jsonElement.isJsonObject())
                json = jsonElement.getAsJsonObject();
            else return null;
        } catch (final IllegalStateException ex) {
            ex.printStackTrace();
            return null;
        }
        if (json.has("with")) {
            final JsonArray args = json.getAsJsonArray("with");
            for (JsonElement el : args) {
                if (el instanceof JsonObject arg1) {
                    if (arg1.has("hoverEvent")) {
                        final JsonObject hoverEvent = arg1.getAsJsonObject("hoverEvent");
                        if (hoverEvent.has("action") && hoverEvent.get("action").getAsString().equals("show_item") && hoverEvent.has("contents")) {
                            if (hoverEvent.getAsJsonObject("contents").has("tag")) {
                                final JsonObject item = hoverEvent.getAsJsonObject("contents").getAsJsonObject();
                                try {
                                    final ItemStack is = new ItemStack(BuiltInRegistries.ITEM.get((new ResourceLocation(item.get("id").getAsString()))));
                                    if (item.has("tag")) {
                                        final CompoundTag tag = (CompoundTag) NbtTagArgument.nbtTag().parse(new StringReader(item.get("tag").getAsString()));
                                        is.setTag(tag);
                                    }
                                    final CompoundTag itemTag = is.getOrCreateTag();
                                    final EmbedBuilder b = new EmbedBuilder();
                                    String title = is.hasCustomHoverName() ? is.getHoverName().getString() : Component.translatable(is.getItem().getDescriptionId(), is.getItem().getName(is).getString(), null).toString();
                                    if (title.isEmpty())
                                        title = Component.translatable(is.getItem().getDescriptionId()).getString();
                                    else
                                        b.setFooter(is.getItemHolder().unwrap().left().get().location().toString());
                                    b.setTitle(title);
                                    final StringBuilder tooltip = new StringBuilder();
                                    boolean[] flags = new boolean[6]; // Enchantments, Modifiers, Unbreakable, CanDestroy, CanPlace, Other
                                    Arrays.fill(flags, false); // Set everything visible

                                    if (itemTag.contains("HideFlags")) {
                                        final int input = (itemTag.getInt("HideFlags"));
                                        for (int i = 0; i < flags.length; i++) {
                                            flags[i] = (input & (1 << i)) != 0;
                                        }
                                    }
                                    //Add Enchantments
                                    if (!flags[0]) {
                                        EnchantmentHelper.deserializeEnchantments(is.getEnchantmentTags()).forEach((ench, level) -> {
                                            tooltip.append(ChatFormatting.stripFormatting(ench.getFullname(level).getString())).append("\n");
                                        });
                                    }
                                    //Add Lores
                                    final ListTag list = itemTag.getCompound("display").getList("Lore", 8);
                                    list.forEach((nbt) -> {
                                        try {
                                            if (nbt instanceof StringTag) {
                                                final Component comp = ComponentArgument.textComponent().parse(new StringReader(nbt.getAsString()));
                                                tooltip.append("_").append(comp.getString()).append("_\n");
                                            }
                                        } catch (CommandSyntaxException e) {
                                            e.printStackTrace();
                                        }
                                    });
                                    //Add 'Unbreakable' Tag
                                    if (!flags[2] && itemTag.contains("Unbreakable") && itemTag.getBoolean("Unbreakable"))
                                        tooltip.append("Unbreakable\n");
                                    b.setDescription(tooltip.toString());
                                    return b.build();
                                } catch (CommandSyntaxException ignored) {
                                    //Just go on and ignore it
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }
}
