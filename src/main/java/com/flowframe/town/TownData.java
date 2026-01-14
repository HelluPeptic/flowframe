package com.flowframe.town;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;

import java.util.UUID;

public class TownData {
    private final String name;
    private final UUID owner;
    private ChatFormatting color;
    private BlockPos coords;
    private final String dimension;
    private int fakeMemberCount = -1; // -1 means use real count

    public TownData(String name, UUID owner, ChatFormatting color, BlockPos coords, String dimension) {
        this.name = name;
        this.owner = owner;
        this.color = color;
        this.coords = coords;
        this.dimension = dimension;
    }

    public String getName() {
        return name;
    }

    public UUID getOwner() {
        return owner;
    }

    public ChatFormatting getColor() {
        return color;
    }

    public void setColor(ChatFormatting color) {
        this.color = color;
    }

    public String getPrefix() {
        return name;
    }

    public String getRank(int memberCount) {
        if (memberCount >= 50) return "Capital";
        if (memberCount >= 40) return "City";
        if (memberCount >= 30) return "Haven";
        if (memberCount >= 20) return "Town";
        if (memberCount >= 15) return "Settlement";
        if (memberCount >= 10) return "Ranch";
        if (memberCount >= 5) return "Village";
        return "Hamlet";
    }

    public int getMembersToNextRank(int memberCount) {
        if (memberCount >= 50) return 0; // Already at max rank
        if (memberCount >= 40) return 50 - memberCount;
        if (memberCount >= 30) return 40 - memberCount;
        if (memberCount >= 20) return 30 - memberCount;
        if (memberCount >= 15) return 20 - memberCount;
        if (memberCount >= 10) return 15 - memberCount;
        if (memberCount >= 5) return 10 - memberCount;
        return 5 - memberCount; // Hamlet to Village
    }

    public BlockPos getCoords() {
        return coords;
    }

    public String getDimension() {
        return dimension;
    }

    public String getFormattedPrefix() {
        // Gray brackets with colored town name
        return "§7[" + color + name + "§7]§r";
    }

    public String getLocationString() {
        return coords.getX() + ", " + coords.getY() + ", " + coords.getZ();
    }

    public int getFakeMemberCount() {
        return fakeMemberCount;
    }

    public void setFakeMemberCount(int fakeMemberCount) {
        this.fakeMemberCount = fakeMemberCount;
    }

    public boolean hasFakeMemberCount() {
        return fakeMemberCount >= 0;
    }

    public void setCoords(BlockPos newCoords) {
        this.coords = newCoords;
    }
}