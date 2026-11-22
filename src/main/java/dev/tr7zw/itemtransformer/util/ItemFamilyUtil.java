package dev.tr7zw.itemtransformer.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;

public final class ItemFamilyUtil {

    private static final List<List<String>> FAMILIES = buildFamilies();

    private ItemFamilyUtil() {
    }

    public static boolean canTransformTo(Item from, Item to) {
        if (from == null || to == null || from == Items.AIR || to == Items.AIR) {
            return false;
        }
        if (from == to) {
            return true;
        }
        Set<Item> family = LazyHolder.FAMILY.get(from);
        return family != null && family.contains(to);
    }

    public static Set<Item> getFamily(Item item) {
        if (item == null || item == Items.AIR) {
            return null;
        }
        return LazyHolder.FAMILY.get(item);
    }

    /** slab=1, block=2, door=4 */
    public static int itemCost(Item item) {
        if (item == null) return 2;
        String name = simpleName(item);
        if (name.endsWith("_slab")) return 1;
        if (name.endsWith("_door")) return 4;
        return 2;
    }

    public static boolean canConvert(ItemStack input, Item to) {
        if (!canTransformTo(input.getItem(), to)) return false;
        int fromCost = itemCost(input.getItem());
        int toCost = itemCost(to);
        return (input.getCount() * fromCost) / toCost >= 1;
    }

    public static int conversionCount(ItemStack input, Item to) {
        int fromCost = itemCost(input.getItem());
        int toCost = itemCost(to);
        int toCount = (input.getCount() * fromCost) / toCost;
        if (toCount < 1) toCount = 1;
        return toCount;
    }

    private static String simpleName(Item item) {
        String name = BuiltInRegistries.ITEM.getKey(item).toString().toLowerCase();
        int idx = name.lastIndexOf(':');
        if (idx >= 0) name = name.substring(idx + 1);
        return name;
    }

    private static List<List<String>> buildFamilies() {
        List<List<String>> families = new ArrayList<>();

        // Wood: plank family and log family are separate.
        String[] woods = {
                "oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
                "mangrove", "cherry", "bamboo", "crimson", "warped",
                "poplar", "pale_oak"
        };
        for (String wood : woods) {
            List<String> plankFamily = new ArrayList<>(List.of(
                    wood + "_planks", wood + "_slab", wood + "_stairs", wood + "_shelf",
                    wood + "_trapdoor", wood + "_door", wood + "_fence_gate", wood + "_fence",
                    wood + "_sign", wood + "_hanging_sign", wood + "_button", wood + "_pressure_plate",
                    wood + "_wall"));
            if ("bamboo".equals(wood)) {
                plankFamily.add("bamboo_mosaic");
                plankFamily.add("bamboo_mosaic_slab");
                plankFamily.add("bamboo_mosaic_stairs");
            }
            families.add(plankFamily);
            families.add(List.of(
                    wood + "_log", wood + "_wood",
                    "stripped_" + wood + "_log", "stripped_" + wood + "_wood"));
        }



        // Copper: grouped by oxidation state; waxed is a separate family from unwaxed.
        String[] copperPrefixes = {"", "exposed_", "weathered_", "oxidized_"};
        for (String p : copperPrefixes) {
            String base = p.isEmpty() ? "copper_block" : p + "copper";
            List<String> shapes = List.of(
                    base,
                    p + "chiseled_copper",
                    p + "cut_copper",
                    p + "cut_copper_slab",
                    p + "cut_copper_stairs",
                    p + "copper_grate");
            families.add(new ArrayList<>(shapes));
            List<String> waxed = new ArrayList<>();
            for (String n : shapes) {
                waxed.add("waxed_" + n);
            }
            families.add(waxed);
        }

        // Stone: raw stone family + smooth/polished stone family (separate).
        families.add(List.of("stone", "stone_slab", "stone_stairs", "stone_wall", "stone_pressure_plate", "stone_button"));
        families.add(List.of(
                "smooth_stone", "polished_stone",
                "smooth_stone_slab", "smooth_stone_stairs", "smooth_stone_wall",
                "polished_stone_slab", "polished_stone_stairs", "polished_stone_wall"));
        families.add(List.of(
                "stone_bricks", "chiseled_stone_bricks", "mottled_stone_bricks",
                "stone_brick_stairs", "stone_brick_slab", "stone_brick_wall"));
        // Mossy stone bricks (separate family).
        addStoneFamily(families, "mossy_stone_bricks");

        addStoneFamily(families, "tuff", "polished_tuff", "chiseled_tuff", "tuff_bricks", "chiseled_tuff_bricks");
        addStoneFamily(families, "granite", "polished_granite");
        addStoneFamily(families, "andesite", "polished_andesite");
        addStoneFamily(families, "diorite", "polished_diorite");
        // Deepslate (raw "deepslate" excluded).
        addStoneFamily(families, "cobbled_deepslate", "cobbled_deepslate_tiles",
                "deepslate_tiles", "polished_deepslate_tiles",
                "polished_deepslate", "deepslate_bricks", "chiseled_deepslate", "chiseled_deepslate_bricks");
        // Basalt (smooth_basalt excluded).
        addStoneFamily(families, "basalt", "polished_basalt");
        addStoneFamily(families, "sandstone", "chiseled_sandstone", "cut_sandstone");

        addStoneFamily(families, "smooth_sandstone");

        addStoneFamily(families, "red_sandstone", "chiseled_red_sandstone", "cut_red_sandstone");

        addStoneFamily(families, "smooth_red_sandstone");
        addStoneFamily(families, "end_stone", "end_stone_bricks", "chiseled_end_stone_bricks");

        addStoneFamily(families, "mud");

        addStoneFamily(families, "packed_mud", "reinforced_deepslate", "mud_bricks", "chiseled_mud_bricks");
        // Blackstone (gilded and cracked forms excluded).
        addStoneFamily(families, "blackstone", "polished_blackstone",
                "polished_blackstone_bricks", "chiseled_polished_blackstone", "polished_blackstone_tiles");
        // Cobblestone (separate from mossy).
        addStoneFamily(families, "cobblestone", "cut_cobblestone");
        addStoneFamily(families, "mossy_cobblestone", "cut_mossy_cobblestone");

        families.add(List.of("bricks", "brick_slab", "brick_stairs", "brick_wall"));
        addQuartzFamily(families);

        families.add(List.of("purpur_block", "purpur_pillar", "purpur_slab", "purpur_stairs"));
        // Nether bricks (plain and red are separate).
        addStoneFamily(families, "nether_bricks", "chiseled_nether_bricks");
        addStoneFamily(families, "red_nether_bricks");
        // Prismarine (three grades, separate).
        addStoneFamily(families, "prismarine", "chiseled_prismarine");
        addStoneFamily(families, "prismarine_bricks");
        addStoneFamily(families, "dark_prismarine");
        addStoneFamily(families, "sulfur", "polished_sulfur", "chiseled_sulfur", "sulfur_bricks");
        addStoneFamily(families, "cinnabar", "polished_cinnabar", "chiseled_cinnabar", "cinnabar_bricks");
        addStoneFamily(families, "resin_block", "resin_bricks", "chiseled_resin_bricks");

        // Concrete (each color separate; powder excluded).
        String[] colors = {
                "white", "orange", "magenta", "light_blue", "yellow", "lime",
                "pink", "gray", "light_gray", "red", "purple", "blue", "brown",
                "green", "black"
        };
        for (String color : colors) {
            families.add(List.of(color + "_concrete", color + "_concrete_slab", color + "_concrete_stairs"));
        }

        return families;
    }

    private static void addStoneFamily(List<List<String>> out, String... bases) {
        List<String> family = new ArrayList<>();
        for (String base : bases) {
            family.add(base);
            // Strip trailing 's' for plural bases.
            String shape;
            if (base.endsWith("_bricks") || base.endsWith("_tiles")) {
                shape = base.substring(0, base.length() - 1);
            } else {
                shape = base;
            }
            family.add(shape + "_slab");
            family.add(shape + "_stairs");
            family.add(shape + "_wall");
            family.add(shape + "_pressure_plate");
            family.add(shape + "_fence");
            family.add(shape + "_fence_gate");
            family.add(shape + "_trapdoor");
            family.add(shape + "_door");
            family.add(shape + "_sign");
            family.add(shape + "_hanging_sign");
            family.add(shape + "_button");
        }
        out.add(family);
    }

    /** Quartz has inconsistent naming, so enumerate explicitly. */
    private static void addQuartzFamily(List<List<String>> out) {
        out.add(List.of(
                "quartz_block", "quartz_slab", "quartz_stairs",
                "chiseled_quartz_block", "chiseled_quartz_slab", "chiseled_quartz_stairs",
                "quartz_pillar", "quartz_bricks"));
        out.add(List.of("smooth_quartz", "smooth_quartz_slab", "smooth_quartz_stairs"));
    }

    private static final class LazyHolder {
        static final Map<Item, Set<Item>> FAMILY;

        static {
            Map<String, Item> byName = new HashMap<>();

            for (var entry : BuiltInRegistries.ITEM.entrySet()) {
                Item item = entry.getValue();
                if (item == null || item == Items.AIR) {
                    continue;
                }
                String name = item.toString().toLowerCase();
                int idx = name.lastIndexOf(':');
                if (idx >= 0) {
                    name = name.substring(idx + 1);
                }
                byName.put(name, item);
            }

            Map<Item, Set<Item>> family = new HashMap<>();
            for (List<String> fam : FAMILIES) {
                Set<Item> members = new LinkedHashSet<>();
                for (String n : fam) {
                    Item it = byName.get(n);
                    if (it != null) {
                        members.add(it);
                    }
                }
                if (members.isEmpty()) {
                    continue;
                }
                for (Item member : members) {
                    family.put(member, members);
                }
            }
            FAMILY = family;
        }

        private LazyHolder() {
        }
    }
}
