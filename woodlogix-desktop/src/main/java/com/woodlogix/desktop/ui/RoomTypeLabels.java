package com.woodlogix.desktop.ui;

import com.woodlogix.core.enums.RoomType;

import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;

public final class RoomTypeLabels {
    private RoomTypeLabels() {
    }

    public static String localized(String storedType, ResourceBundle messages) {
        return find(storedType, messages)
                .map(type -> messages.getString(messageKey(type)))
                .orElse(storedType);
    }

    public static Optional<RoomType> find(String storedType, ResourceBundle messages) {
        if (storedType == null || storedType.isBlank()) {
            return Optional.empty();
        }
        String normalized = storedType.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        try {
            return Optional.of(RoomType.valueOf(normalized));
        } catch (IllegalArgumentException ignored) {
            return switch (normalized) {
                case "COZINHA", "COCINA" -> Optional.of(RoomType.KITCHEN);
                case "DORMITÓRIO_CASAL", "DORMITORIO_CASAL", "DORMITORIO_DE_MATRIMONIO" ->
                        Optional.of(RoomType.MASTER_BEDROOM);
                case "DORMITÓRIO_SOLTEIRO", "DORMITORIO_SOLTEIRO", "DORMITORIO_INDIVIDUAL" ->
                        Optional.of(RoomType.SINGLE_BEDROOM);
                case "BANHEIRO", "BAÑO", "BANO" -> Optional.of(RoomType.BATHROOM);
                case "CLOSET", "VESTIDOR" -> Optional.of(RoomType.WALK_IN_CLOSET);
                case "LAVANDERIA", "LAVADERO" -> Optional.of(RoomType.LAUNDRY_ROOM);
                case "ESCRITÓRIO", "ESCRITORIO", "DESPACHO" ->
                        Optional.of(RoomType.HOME_OFFICE);
                default -> findLocalized(storedType, messages);
            };
        }
    }

    public static String messageKey(RoomType type) {
        return "room." + type.name().toLowerCase(Locale.ROOT);
    }

    private static Optional<RoomType> findLocalized(
            String storedType, ResourceBundle messages
    ) {
        return java.util.Arrays.stream(RoomType.values())
                .filter(type -> messages.getString(messageKey(type))
                        .equalsIgnoreCase(storedType))
                .findFirst();
    }
}
