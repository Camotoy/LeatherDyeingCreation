// This file is licensed under the MIT License

/*
MobEquipmentPacket(runtimeEntityId=1, item=ItemData(id=301, damage=0, count=1, tag={
  "customColor": -8337633i
 */


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nukkitx.nbt.NBTOutputStream;
import com.nukkitx.nbt.NbtMap;
import com.nukkitx.nbt.NbtMapBuilder;
import com.nukkitx.nbt.NbtUtils;
import com.nukkitx.protocol.bedrock.data.inventory.*;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Value;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class LeatherDyingCreation {

    public static final ObjectMapper JSON_MAPPER = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private static final DefaultPrettyPrinter PRETTY_PRINTER = new DefaultPrettyPrinter();

    public static void main(String[] args) {
        List<CraftingDataEntry> entries = new ArrayList<>();
        int[] armors = new int[] {298, 299, 300, 301};
        int dye = 351;
        Int2IntMap dyeColorToLeatherColor = new Int2IntArrayMap();
        // The keys are from https://minecraft.gamepedia.com/Java_Edition_data_value/Pre-flattening#Dyes
        // And the values are from dyeing a cauldron in Bedrock
        dyeColorToLeatherColor.put(1, -5231066);
        dyeColorToLeatherColor.put(2, -10585066);
        dyeColorToLeatherColor.put(5, -7785800);
        dyeColorToLeatherColor.put(6, -15295332);
        dyeColorToLeatherColor.put(7, -6447721);
        dyeColorToLeatherColor.put(8, -12103854);
        dyeColorToLeatherColor.put(9, -816214);
        dyeColorToLeatherColor.put(10, -8337633);
        dyeColorToLeatherColor.put(11, -75715);
        dyeColorToLeatherColor.put(12, -12930086);
        dyeColorToLeatherColor.put(13, -7785800);
        dyeColorToLeatherColor.put(14, -425955);
        dyeColorToLeatherColor.put(16, -14869215); // Black dye
        dyeColorToLeatherColor.put(17, -8170446); // Brown dye
        dyeColorToLeatherColor.put(18, -12827478); // Blue dye
        dyeColorToLeatherColor.put(19, -986896); // White dye
        for (int armor : armors) {
            for (int i = 0; i <= 19; i++) {
                if (i == 0) continue; // Ink sac
                if (i == 3) continue; // Cocoa beans
                if (i == 4) continue; // Lapis
                if (i == 15) continue; // Bone Meal
                String id = "leather_" + armor + "_dye_" + i;
                NbtMapBuilder nbt = NbtMap.builder();
                nbt.putInt("customColor", dyeColorToLeatherColor.get(i));
                CraftingData craftingData = CraftingData.fromShapeless(id, new ItemData[] {
                        ItemData.of(armor, (short) 0, 1),
                        ItemData.of(dye, (short) i, 1)
                }, new ItemData[] {ItemData.of(armor, (short) 0, 1, nbt.build())}, UUID.randomUUID(), "crafting_table", 50);

                CraftingDataEntry entry = new CraftingDataEntry();

                CraftingDataType type = craftingData.getType();
                entry.type = type.ordinal();

                if (type != CraftingDataType.MULTI) {
                    entry.block = craftingData.getCraftingTag();
                } else {
                    entry.uuid = craftingData.getUuid();
                }

                if (type == CraftingDataType.SHAPED || type == CraftingDataType.SHAPELESS || type == CraftingDataType.SHAPELESS_CHEMISTRY || type == CraftingDataType.SHULKER_BOX || type == CraftingDataType.SHAPED_CHEMISTRY) {
                    entry.id = craftingData.getRecipeId();
                    entry.priority = craftingData.getPriority();
                    entry.output = writeItemArray(craftingData.getOutputs(), true);
                }
                if (type == CraftingDataType.SHAPELESS || type == CraftingDataType.SHAPELESS_CHEMISTRY || type == CraftingDataType.SHULKER_BOX) {
                    entry.input = writeItemArray(craftingData.getInputs(), false);
                }

                if (type == CraftingDataType.FURNACE || type == CraftingDataType.FURNACE_DATA) {
                    Integer damage = craftingData.getInputDamage();
                    if (damage == 0x7fff) damage = -1;
                    if (damage == 0) damage = null;
                    entry.input = new Item(craftingData.getInputId(), damage, null, null);
                    entry.output = itemFromNetwork(craftingData.getOutputs()[0], true);
                }
                entries.add(entry);
            }
        }

        Path baseDir =  Paths.get(".").toAbsolutePath();
        try (OutputStream outputStream = Files.newOutputStream(baseDir.resolve("recipes.json"), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
            JSON_MAPPER.writer(PRETTY_PRINTER).writeValue(outputStream, entries);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Item[] writeItemArray(ItemData[] inputs, boolean output) {
        List<Item> outputs = new ArrayList<>();
        for (ItemData input : inputs) {
            Item item = itemFromNetwork(input, output);
            if (item != Item.EMPTY) {
                outputs.add(item);
            }
        }
        return outputs.toArray(new Item[0]);
    }

    private static String nbtToBase64(NbtMap tag) {
        if (tag != null) {
            ByteArrayOutputStream tagStream = new ByteArrayOutputStream();
            try (NBTOutputStream writer = NbtUtils.createWriterLE(tagStream)) {
                writer.writeTag(tag);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return Base64.getEncoder().encodeToString(tagStream.toByteArray());
        } else {
            return null;
        }
    }

    private static Item itemFromNetwork(ItemData data, boolean output) {
        int id = data.getId();
        Integer damage = (int) data.getDamage();
        Integer count = data.getCount();
        String tag = nbtToBase64(data.getTag());

        if (id == 0) {
            return Item.EMPTY;
        }
        if (damage == 0 || (damage == -1 && output)) damage = null;
        if (count == 1) count = null;

        return new Item(id, damage, count, tag);
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private static class CraftingDataEntry {
        private String id;
        private int type;
        private Object input;
        private Object output;
        private String[] shape;
        private String block;
        private UUID uuid;
        private Integer priority;
    }

    @Value
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private static class Item {
        public static final Item EMPTY = new Item(0, null, null, null);

        int id;
        Integer damage;
        Integer count;
        String nbt_b64;
    }

    @Value
    private static class Recipes {
        int version;
        List<CraftingDataEntry> recipes;
        List<PotionMixData> potionMixes;
        List<ContainerMixData> containerMixes;
    }
}
