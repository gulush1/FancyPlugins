package de.oliver.fancynpcs.integrations.resourcepack.network;

import de.oliver.fancynpcs.integrations.resourcepack.state.PackSessionRegistry;
import de.oliver.fancynpcs.integrations.resourcepack.state.PackStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;
import java.util.UUID;

/**
 * Recognises Minecraft packet objects structurally (package + simple-name marker + field types) so the
 * same code works on Spigot-mapped 1.20–1.20.4 and Mojang-mapped 1.20.5–26.x servers, where class and
 * field names differ. Results are cached per class with {@link ClassValue}. Everything here is
 * reflection; any failure degrades to {@link Kind#NONE} / {@code null}, never throws.
 */
final class PacketShapes {

    private static final String PROTOCOL_PACKAGE = "net.minecraft.network.protocol.";
    private static final String LOGIN_PACKAGE_MARKER = ".protocol.login.";
    private static final String RESOURCE_PACK_MARKER = "ResourcePack";
    private static final String GAME_PROFILE_CLASS = "com.mojang.authlib.GameProfile";

    private static final ClassValue<Shape> SHAPES = new ClassValue<>() {
        @Override
        protected Shape computeValue(Class<?> type) {
            return Shape.of(type);
        }
    };

    private static final ClassValue<Optional<Method>> PROFILE_ID_ACCESSORS = new ClassValue<>() {
        @Override
        protected Optional<Method> computeValue(Class<?> type) {
            return resolveProfileIdAccessor(type);
        }
    };

    private PacketShapes() {
    }

    enum Kind {
        NONE, LOGIN_SUCCESS, PACK_PUSH, PACK_POP, PACK_STATUS
    }

    static @NotNull Kind classify(@Nullable Object packet) {
        if (packet == null) {
            return Kind.NONE;
        }
        return SHAPES.get(packet.getClass()).kind();
    }

    static @Nullable UUID profileId(@NotNull Object packet) {
        Object profile = read(SHAPES.get(packet.getClass()).profileField(), packet);
        if (profile == null) {
            return null;
        }
        Optional<Method> accessor = PROFILE_ID_ACCESSORS.get(profile.getClass());
        if (accessor.isEmpty()) {
            return null;
        }
        try {
            Object id = accessor.get().invoke(profile);
            return id instanceof UUID uuid ? uuid : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    static @NotNull UUID packId(@NotNull Object packet) {
        Object value = read(SHAPES.get(packet.getClass()).uuidField(), packet);
        return value instanceof UUID uuid ? uuid : PackSessionRegistry.LEGACY_PACK_ID;
    }

    static @Nullable UUID poppedPackId(@NotNull Object packet) {
        Object optional = read(SHAPES.get(packet.getClass()).optionalField(), packet);
        if (!(optional instanceof Optional<?> value) || value.isEmpty()) {
            return null;
        }
        Object content = value.get();
        return content instanceof UUID uuid ? uuid : null;
    }

    static @Nullable PackStatus status(@NotNull Object packet) {
        Object value = read(SHAPES.get(packet.getClass()).enumField(), packet);
        if (!(value instanceof Enum<?> enumValue)) {
            return null;
        }
        return PackStatus.fromClientOrdinal(enumValue.ordinal());
    }

    static @Nullable String packHash(@NotNull Object pushPacket) {
        Object value = read(SHAPES.get(pushPacket.getClass()).hashField(), pushPacket);
        return value instanceof String hash && !hash.isBlank() ? hash : null;
    }

    private static @Nullable Object read(@Nullable Field field, @NotNull Object packet) {
        if (field == null) {
            return null;
        }
        try {
            return field.get(packet);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private static @NotNull Optional<Method> resolveProfileIdAccessor(@NotNull Class<?> type) {
        try {
            Optional<Method> accessor = findNoArgUuidMethod(type, "id");
            return accessor.isPresent() ? accessor : findNoArgUuidMethod(type, "getId");
        } catch (RuntimeException | LinkageError e) {
            return Optional.empty();
        }
    }

    private static @NotNull Optional<Method> findNoArgUuidMethod(@NotNull Class<?> type, @NotNull String name) {
        try {
            Method method = type.getMethod(name);
            if (method.getReturnType() == UUID.class) {
                return Optional.of(method);
            }
            return Optional.empty();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private record Shape(Kind kind, @Nullable Field uuidField, @Nullable Field optionalField,
                          @Nullable Field enumField, @Nullable Field profileField, @Nullable Field hashField) {

        private static final Shape NONE = new Shape(Kind.NONE, null, null, null, null, null);

        private static @NotNull Shape of(@NotNull Class<?> type) {
            if (!type.getName().startsWith(PROTOCOL_PACKAGE)) {
                return NONE;
            }
            try {
                return fromFields(type);
            } catch (RuntimeException | LinkageError e) {
                return NONE;
            }
        }

        private static @NotNull Shape fromFields(@NotNull Class<?> type) {
            FieldScan scan = scanFields(type);
            setAccessible(scan.uuidField, scan.optionalField, scan.enumField, scan.profileField, scan.secondStringField);
            Kind kind = classify(type, scan.hasStringField, scan.optionalField, scan.enumField, scan.profileField);
            return new Shape(kind, scan.uuidField, scan.optionalField, scan.enumField, scan.profileField, scan.secondStringField);
        }

        private static @NotNull FieldScan scanFields(@NotNull Class<?> type) {
            FieldScan scan = new FieldScan();
            for (Field field : type.getDeclaredFields()) {
                if (!field.isSynthetic() && !Modifier.isStatic(field.getModifiers())) {
                    scan.accept(field);
                }
            }
            return scan;
        }

        private static @NotNull Kind classify(@NotNull Class<?> type, boolean hasStringField,
                                                @Nullable Field optionalField, @Nullable Field enumField,
                                                @Nullable Field profileField) {
            if (profileField != null && type.getName().contains(LOGIN_PACKAGE_MARKER)) {
                return Kind.LOGIN_SUCCESS;
            }
            if (!type.getSimpleName().contains(RESOURCE_PACK_MARKER)) {
                return Kind.NONE;
            }
            if (hasStringField) {
                return Kind.PACK_PUSH;
            }
            if (optionalField != null) {
                return Kind.PACK_POP;
            }
            if (enumField != null) {
                return Kind.PACK_STATUS;
            }
            return Kind.NONE;
        }

        private static void setAccessible(@Nullable Field... fields) {
            for (Field field : fields) {
                if (field != null) {
                    field.setAccessible(true);
                }
            }
        }
    }

    private static final class FieldScan {

        private Field uuidField;
        private Field optionalField;
        private Field enumField;
        private Field profileField;
        private Field firstStringField;
        private Field secondStringField;
        private boolean hasStringField;

        private void accept(@NotNull Field field) {
            if (uuidField == null && field.getType() == UUID.class) {
                uuidField = field;
            } else if (optionalField == null && field.getType() == Optional.class) {
                optionalField = field;
            } else if (enumField == null && field.getType().isEnum()) {
                enumField = field;
            } else if (profileField == null && field.getType().getName().equals(GAME_PROFILE_CLASS)) {
                profileField = field;
            } else if (field.getType() == String.class) {
                acceptString(field);
            }
        }

        private void acceptString(@NotNull Field field) {
            hasStringField = true;
            if (firstStringField == null) {
                firstStringField = field;
            } else if (secondStringField == null) {
                secondStringField = field;
            }
        }
    }
}
