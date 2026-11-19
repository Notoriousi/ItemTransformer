# ItemTransformer

An addon for [ItemSwapper](https://github.com/tr7zw/ItemSwapper) that enables value-conserving block-family transformations without requiring a crafting table.

## How it works

While holding a block, open ItemSwapper's palette and click a related sibling to convert it instantly. Planks become stairs, slabs merge back into full blocks, and brick variants swap on the fly. 

Upgrading materials (like slabs to planks) automatically pools resources from your inventory and shulker boxes, while server-side validation strictly enforces value-conserving ratios to prevent duplication exploits.

## Features

* **Family Swapping:** Convert items into matching sibling variants (planks ⇄ stairs, glass ⇄ panes, quartz shapes, etc.).
* **Smart Aggregation:** Pools items from your hotbar, inventory, and shulker boxes to craft up to a full stack at once.
* **Server-Authoritative:** Fully validated on the server side to ensure safety and prevent dupes, with instant client-side prediction.

## Conversion Rates

* **Slab:** `0.5` blocks
* **Full Block, Stairs, Trapdoor, Fence, Fence Gate, Sign, Hanging Sign, Button, Pressure Plate:** `1.0` block
* **Door:** `2.0` blocks

## Requirements

* Minecraft 26.3
* Fabric Loader
* Java 25+
* Fabric API
* [ItemSwapper](https://github.com/tr7zw/ItemSwapper)

## Credits

* **[tr7zw](https://github.com/tr7zw)** for creating [ItemSwapper](https://github.com/tr7zw/ItemSwapper).
