package me.mrtahadarvish.astral.sim;

import io.papermc.paper.ServerBuildInfo;
import net.kyori.adventure.key.Key;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;

/** Build info of the simulated server (loaded with ServiceLoader). */
public final class SimBuildInfo implements ServerBuildInfo {

    @Override
    public Key brandId() {
        return ServerBuildInfo.BRAND_PAPER_ID;
    }

    @Override
    public boolean isBrandCompatible(Key brandId) {
        return brandId.equals(ServerBuildInfo.BRAND_PAPER_ID);
    }

    @Override
    public String brandName() {
        return "Paper";
    }

    @Override
    public String minecraftVersionId() {
        return "1.21.11";
    }

    @Override
    public String minecraftVersionName() {
        return "1.21.11";
    }

    @Override
    public OptionalInt buildNumber() {
        return OptionalInt.empty();
    }

    @Override
    public Instant buildTime() {
        return Instant.EPOCH;
    }

    @Override
    public Optional<String> gitBranch() {
        return Optional.empty();
    }

    @Override
    public Optional<String> gitCommit() {
        return Optional.empty();
    }

    @Override
    public String asString(StringRepresentation representation) {
        return "Paper 1.21.11 (simulated)";
    }
}
