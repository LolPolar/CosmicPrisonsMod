package me.landon.papercompanion.api;

import java.util.UUID;

public record CompanionPlayerSession(
        UUID playerId,
        String playerName,
        int protocolVersion,
        String clientModVersion,
        int clientCapabilitiesBitset) {}
