package me.mrtahadarvish.astral.sim;

import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.BlockType;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemType;
import org.bukkit.potion.PotionEffectType;
import org.mockito.MockSettings;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Registry access for the simulation (loaded with ServiceLoader). Every registry entry is a
 * Mockito mock that knows its key; block types know whether they are air / solid / occluding.
 */
public final class SimRegistryAccess implements RegistryAccess {

    private final Map<Object, Registry<?>> registries = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings({"unchecked", "removal"})
    @Deprecated
    public <T extends Keyed> Registry<T> getRegistry(Class<T> type) {
        return (Registry<T>) registries.computeIfAbsent(type, k -> create(new Class<?>[]{type}));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Keyed> Registry<T> getRegistry(RegistryKey<T> key) {
        Registry<?> registry = registries.get(key);
        if (registry == null) {
            registry = create(types(key));
            Registry<?> raced = registries.putIfAbsent(key, registry);
            if (raced != null) {
                registry = raced;
            }
        }
        return (Registry<T>) registry;
    }

    private static Class<?>[] types(RegistryKey<?> key) {
        if (key.equals(RegistryKey.ATTRIBUTE)) {
            return new Class<?>[]{Attribute.class};
        }
        if (key.equals(RegistryKey.MOB_EFFECT)) {
            return new Class<?>[]{PotionEffectType.class};
        }
        if (key.equals(RegistryKey.BLOCK)) {
            return new Class<?>[]{BlockType.Typed.class};
        }
        if (key.equals(RegistryKey.ITEM)) {
            return new Class<?>[]{ItemType.Typed.class};
        }
        if (key.equals(RegistryKey.DATA_COMPONENT_TYPE)) {
            return new Class<?>[]{DataComponentType.class, DataComponentType.Valued.class, DataComponentType.NonValued.class};
        }
        if (key.equals(RegistryKey.MENU)) {
            return new Class<?>[]{org.bukkit.inventory.MenuType.Typed.class, org.bukkit.inventory.MenuType.class};
        }
        if (key.equals(RegistryKey.ENCHANTMENT)) {
            return new Class<?>[]{Enchantment.class};
        }
        if (key.equals(RegistryKey.DAMAGE_TYPE)) {
            return new Class<?>[]{DamageType.class};
        }
        return new Class<?>[]{Keyed.class};
    }

    private static Registry<?> create(Class<?>[] types) {
        Map<String, Object> entries = new ConcurrentHashMap<>();
        return Mockito.mock(Registry.class, Mockito.withSettings().stubOnly().defaultAnswer((Answer<Object>) invocation -> {
            String name = invocation.getMethod().getName();
            Object[] args = invocation.getArguments();
            if ((name.equals("get") || name.equals("getOrThrow")) && args.length == 1 && args[0] != null) {
                String key = keyOf(args[0]);
                Object existing = entries.get(key);
                if (existing != null) {
                    return existing;
                }
                // not computeIfAbsent: creating an entry can initialise a class that reads this registry again
                Object created = entry(types, key);
                Object raced = entries.putIfAbsent(key, created);
                return raced != null ? raced : created;
            }
            if (name.equals("iterator")) {
                return Collections.emptyIterator();
            }
            if (name.equals("stream")) {
                return Stream.empty();
            }
            return Mockito.RETURNS_DEFAULTS.answer(invocation);
        }));
    }

    private static String keyOf(Object key) {
        if (key instanceof NamespacedKey namespacedKey) {
            return namespacedKey.toString();
        }
        if (key instanceof Key adventureKey) {
            return adventureKey.asString();
        }
        return String.valueOf(key);
    }

    private static Object entry(Class<?>[] types, String key) {
        MockSettings settings = Mockito.withSettings().stubOnly().defaultAnswer(new EntryAnswer(key));
        if (types.length > 1) {
            settings = settings.extraInterfaces(Arrays.copyOfRange(types, 1, types.length));
        }
        return Mockito.mock(types[0], settings);
    }

    /** Behaviour of every registry entry. */
    private record EntryAnswer(String key) implements Answer<Object> {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
            String name = invocation.getMethod().getName();
            String path = key.contains(":") ? key.substring(key.indexOf(':') + 1) : key;
            switch (name) {
                case "getKey", "getKeyOrThrow" -> {
                    return NamespacedKey.fromString(key);
                }
                case "key" -> {
                    return Key.key(key);
                }
                case "isAir" -> {
                    return SimBlocks.isAir(path);
                }
                case "isSolid", "hasCollision" -> {
                    return SimBlocks.isSolid(path);
                }
                case "isOccluding" -> {
                    return SimBlocks.isOccluding(path);
                }
                case "toString" -> {
                    return key;
                }
                default -> {
                    return Mockito.RETURNS_DEFAULTS.answer(invocation);
                }
            }
        }
    }
}
