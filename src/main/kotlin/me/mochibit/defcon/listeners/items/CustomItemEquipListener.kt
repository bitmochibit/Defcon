package me.mochibit.defcon.listeners.items

import me.mochibit.defcon.Defcon.Logger.warn
import me.mochibit.defcon.enums.ItemBehaviour
import me.mochibit.defcon.events.equip.CustomItemEquipEvent
import me.mochibit.defcon.extensions.equipSlotName
import me.mochibit.defcon.extensions.getBehaviour
import me.mochibit.defcon.extensions.getItemID
import me.mochibit.defcon.registers.listener.VersionIndicator
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.*
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack

/**
 * Handles custom item equipping logic across different inventory interactions
 */
@VersionIndicator("1.20", "1.21.3")
class CustomItemEquipListener : Listener {

    /**
     * Enum representing armor slot types with mapping to inventory positions
     */
    enum class ArmorSlot(val rawSlot: Int, val inventorySlot: Int, val equipSlotName: String) {
        HELMET(5, 39, "HEAD"),
        CHESTPLATE(6, 38, "CHEST"),
        LEGGINGS(7, 37, "LEGS"),
        BOOTS(8, 36, "FEET");

        companion object {
            fun fromRawSlot(rawSlot: Int): ArmorSlot? =
                entries.firstOrNull { it.rawSlot == rawSlot }

            fun fromInventorySlot(inventorySlot: Int): ArmorSlot? =
                entries.firstOrNull { it.inventorySlot == inventorySlot }

            fun fromEquipSlotName(equipSlotName: String): ArmorSlot? =
                entries.firstOrNull { it.equipSlotName.equals(equipSlotName, ignoreCase = true) }

            fun fromVanillaArmorType(material: Material): ArmorSlot? = when {
                material.name.endsWith("_HELMET") || material.name.endsWith("_HEAD") ||
                material.name.contains("SKULL") || material == Material.CARVED_PUMPKIN -> HELMET

                material.name.endsWith("_CHESTPLATE") || material.name.endsWith("_TUNIC") ||
                material == Material.ELYTRA -> CHESTPLATE

                material.name.endsWith("_LEGGINGS") || material.name.endsWith("_PANTS") -> LEGGINGS

                material.name.endsWith("_BOOTS") -> BOOTS

                else -> null
            }
        }
    }

    /**
     * Constants for inventory slots
     */
    companion object {
        const val OFFHAND_INVENTORY_SLOT = 40
        const val OFFHAND_RAW_SLOT = 45
    }

    /**
     * Checks if an ItemStack is a custom equippable item
     */
    private fun ItemStack.isCustomEquippable(): Boolean {
        return this.getItemID() != null && this.equipSlotName().isNotEmpty()
    }

    /**
     * Direct armor slot click handler
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onDirectArmorSlotClick(event: InventoryClickEvent) {
        // Only process armor slot clicks
        if (event.slotType != InventoryType.SlotType.ARMOR) return

        val cursor = event.cursor ?: return
        if (cursor.type == Material.AIR) return

        val player = event.whoClicked as? Player ?: return

        // Skip vanilla items
        if (!cursor.isCustomEquippable()) return

        // Get slot information
        val targetSlot = ArmorSlot.fromRawSlot(event.rawSlot) ?: return
        val itemSlot = ArmorSlot.fromEquipSlotName(cursor.equipSlotName()) ?: return

        // Verify item can go in this slot
        if (targetSlot != itemSlot) {
            event.isCancelled = true
            return
        }

        // Handle equipment process
        val oldItem = event.currentItem ?: ItemStack(Material.AIR)
        if (processEquipmentChange(cursor, targetSlot, event.rawSlot, player, event)) {
            // Complete the equipment change
            player.inventory.setItem(event.slot, cursor)
            player.setItemOnCursor(oldItem)
            player.updateInventory()
        }
    }

    /**
     * Shift-click to equip handler with improved vanilla-like behavior
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onShiftClickEquip(event: InventoryClickEvent) {
        // Verify this is a shift-click
        if (event.click != ClickType.SHIFT_LEFT && event.click != ClickType.SHIFT_RIGHT) return
        if (event.action != InventoryAction.MOVE_TO_OTHER_INVENTORY) return

        val currentItem = event.currentItem ?: return
        if (currentItem.type == Material.AIR) return

        // Skip vanilla items
        if (!currentItem.isCustomEquippable()) return

        // Ignore clicks in crafting or already in armor slots
        if (event.slotType == InventoryType.SlotType.CRAFTING ||
            event.slotType == InventoryType.SlotType.ARMOR) return

        val player = event.whoClicked as? Player ?: return

        // Get slot information
        val itemSlot = ArmorSlot.fromEquipSlotName(currentItem.equipSlotName()) ?: return
        val equipmentSlot = itemSlot.inventorySlot

        // Check if target slot is occupied
        val oldItem = player.inventory.getItem(equipmentSlot)

        // If slot is occupied, mimic vanilla behavior by finding alternative placement
        if (oldItem != null && oldItem.type != Material.AIR) {
            // Handle vanilla-like slot finding
            moveToAlternativeInventorySection(currentItem, player, event)
            return
        }

        // Process the equipment change
        if (processEquipmentChange(currentItem, itemSlot, itemSlot.rawSlot, player, event)) {
            // Complete the equipment change
            player.inventory.setItem(event.slot, null)
            player.inventory.setItem(equipmentSlot, currentItem)
            player.updateInventory()
        }
    }

    /**
     * Move item to an alternative inventory section when target armor slot is occupied
     */
    private fun moveToAlternativeInventorySection(
        item: ItemStack,
        player: Player,
        event: InventoryClickEvent
    ) {
        // Determine which section to move to based on source section
        val (startSlot, endSlot) = if (event.rawSlot >= 36 && event.rawSlot <= 44) {
            // If from hotbar, try main inventory
            Pair(9, 35)
        } else {
            // If from main inventory, try hotbar
            Pair(0, 8)
        }

        // Find first empty slot
        for (i in startSlot..endSlot) {
            val slotItem = player.inventory.getItem(i)
            if (slotItem == null || slotItem.type == Material.AIR) {
                // Cancel default behavior and handle manually
                event.isCancelled = true

                // Move item to empty slot
                val itemCopy = item.clone()
                player.inventory.setItem(event.slot, null)
                player.inventory.setItem(i, itemCopy)
                player.updateInventory()
                return
            }
        }
        // If no empty slot, let vanilla handle it
    }

    /**
     * Drag-and-drop to equip custom items handler
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onDragEquip(event: InventoryDragEvent) {
        // Check if any dragged slots are armor slots
        val armorSlot = event.rawSlots.firstOrNull { isArmorSlot(it) } ?: return

        val cursor = event.oldCursor
        if (cursor.type == Material.AIR) return

        val player = event.whoClicked as? Player ?: return

        // Skip vanilla items
        if (!cursor.isCustomEquippable()) return

        // Get slot information
        val targetSlot = ArmorSlot.fromRawSlot(armorSlot) ?: run {
            event.isCancelled = true
            return
        }

        val itemSlot = ArmorSlot.fromEquipSlotName(cursor.equipSlotName()) ?: return

        // Verify item can go in this slot
        if (targetSlot != itemSlot) {
            event.isCancelled = true
            return
        }

        // Get current item in slot
        val inventorySlot = targetSlot.inventorySlot
        val oldItem = player.inventory.getItem(inventorySlot) ?: ItemStack(Material.AIR)

        // Process the equipment change
        if (processEquipmentChange(cursor, targetSlot, armorSlot, player, event)) {
            player.inventory.setItem(inventorySlot, cursor)
            player.setItemOnCursor(oldItem)
            player.updateInventory()
        }
    }

    /**
     * Number key hotbar press to equip item handler
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onHotbarKeyEquip(event: InventoryClickEvent) {
        // Verify this is a hotbar key press
        if (event.click != ClickType.NUMBER_KEY) return

        // Check if target is an armor slot
        if (event.slotType != InventoryType.SlotType.ARMOR) return

        val player = event.whoClicked as? Player ?: return

        // Get item from hotbar
        val hotbarItem = player.inventory.getItem(event.hotbarButton) ?: return
        if (hotbarItem.type == Material.AIR) return

        // Skip vanilla items
        if (!hotbarItem.isCustomEquippable()) return

        // Get slot information
        val targetSlot = ArmorSlot.fromRawSlot(event.rawSlot) ?: return
        val itemSlot = ArmorSlot.fromEquipSlotName(hotbarItem.equipSlotName()) ?: return

        // Verify item can go in this slot
        if (targetSlot != itemSlot) {
            event.isCancelled = true
            return
        }

        val oldItem = event.currentItem ?: ItemStack(Material.AIR)

        // Process the equipment change
        if (processEquipmentChange(hotbarItem, targetSlot, event.rawSlot, player, event)) {
            // Swap the items
            player.inventory.setItem(event.slot, hotbarItem)
            player.inventory.setItem(event.hotbarButton, oldItem)
            player.updateInventory()
        }
    }

    /**
     * Prevents double-click to collect custom equippable items from armor slots
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onDoubleClickCollection(event: InventoryClickEvent) {
        if (event.click != ClickType.DOUBLE_CLICK) return

        val cursor = event.cursor ?: return
        if (cursor.type == Material.AIR) return

        // Prevent collecting custom equipable items with double-click
        if (cursor.isCustomEquippable()) {
            event.isCancelled = true
        }
    }

    /**
     * Right-click to equip items outside inventory handler
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onRightClickEquip(event: PlayerInteractEvent) {
        // Only handle right clicks
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return

        val player = event.player
        val item = event.item ?: return
        val hand = event.hand ?: return

        // Skip vanilla items
        if (!item.isCustomEquippable()) return

        // Must cancel early to prevent vanilla behavior
        event.isCancelled = true

        // Get slot information
        val equipSlotName = item.equipSlotName()
        val targetSlot = ArmorSlot.fromEquipSlotName(equipSlotName) ?: run {
            warn("Could not map equipment slot name $equipSlotName to any armor slot")
            event.isCancelled = false
            return
        }

        val equipmentSlot = targetSlot.inventorySlot
        val currentItem = player.inventory.getItem(equipmentSlot)

        // Verify and trigger equip event
        if (checkItemSlotCompatibility(item, targetSlot) &&
            triggerEquipEvent(item, targetSlot.rawSlot, player)) {

            // Create copy with amount 1 for equipping
            val itemToEquip = item.clone().apply { amount = 1 }

            // Handle item removal based on which hand was used
            when (hand) {
                EquipmentSlot.HAND -> {
                    handleMainHandItemRemoval(player, currentItem)
                }
                EquipmentSlot.OFF_HAND -> {
                    handleOffHandItemRemoval(player, currentItem)
                }
                else -> return
            }

            // Equip the item in the correct slot
            player.inventory.setItem(equipmentSlot, itemToEquip)
            player.updateInventory()
        }
    }

    /**
     * Handle item removal from main hand when equipping
     */
    private fun handleMainHandItemRemoval(player: Player, currentItem: ItemStack?) {
        val handSlot = player.inventory.heldItemSlot
        val mainHandItem = player.inventory.getItem(handSlot)

        if (mainHandItem != null) {
            if (mainHandItem.amount > 1) {
                mainHandItem.amount--
                player.inventory.setItem(handSlot, mainHandItem)
            } else {
                // If there's an item in the target slot, swap it to hand
                player.inventory.setItem(handSlot, currentItem ?: null)
            }
        }
    }

    /**
     * Handle item removal from off hand when equipping
     */
    private fun handleOffHandItemRemoval(player: Player, currentItem: ItemStack?) {
        val offhandItem = player.inventory.itemInOffHand

        if (offhandItem.type != Material.AIR) {
            if (offhandItem.amount > 1) {
                offhandItem.amount--
                player.inventory.setItemInOffHand(offhandItem)
            } else {
                // If there's an item in the target slot, swap it to offhand
                player.inventory.setItemInOffHand(currentItem ?: null)
            }
        }
    }

    /**
     * Offhand swap (F key press) handler
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onOffhandSwapEquip(event: InventoryClickEvent) {
        // Verify this is an offhand swap
        if (event.click != ClickType.SWAP_OFFHAND) return

        val player = event.whoClicked as? Player ?: return

        val currentItem = event.currentItem ?: return
        if (currentItem.type == Material.AIR) return

        // Skip vanilla items
        if (!currentItem.isCustomEquippable()) return

        // Don't allow swapping custom armor with offhand from armor slots
        if (event.slotType == InventoryType.SlotType.ARMOR) {
            event.isCancelled = true
            return
        }

        // Get slot information
        val itemSlot = ArmorSlot.fromEquipSlotName(currentItem.equipSlotName()) ?: return
        val equipmentSlot = itemSlot.inventorySlot

        // Check if target slot is occupied
        val armorItem = player.inventory.getItem(equipmentSlot)
        if (armorItem != null && armorItem.type != Material.AIR) {
            // Let vanilla handle it if slot is occupied
            return
        }

        val offhandItem = player.inventory.itemInOffHand

        // Process the equipment change
        if (processEquipmentChange(currentItem, itemSlot, itemSlot.rawSlot, player, event)) {
            // Cancel default behavior and handle manually
            event.isCancelled = true

            // Move items
            player.inventory.setItem(event.slot, offhandItem)
            player.inventory.setItemInOffHand(null)
            player.inventory.setItem(equipmentSlot, currentItem)
            player.updateInventory()
        }
    }

    /**
     * Direct offhand slot interaction handler
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onDirectOffhandInteraction(event: InventoryClickEvent) {
        // Check specifically for offhand slot clicks
        if (event.slot != OFFHAND_INVENTORY_SLOT && event.rawSlot != OFFHAND_RAW_SLOT) return

        val currentItem = event.currentItem ?: return
        if (currentItem.type == Material.AIR) return

        // Only interested in custom equipable items
        if (!currentItem.isCustomEquippable()) return

        val player = event.whoClicked as? Player ?: return

        // Allow simple pickup actions
        if (event.action == InventoryAction.PICKUP_ALL ||
            event.action == InventoryAction.PICKUP_HALF ||
            event.action == InventoryAction.PICKUP_SOME ||
            event.action == InventoryAction.PICKUP_ONE) {
            return
        }

        // Get slot information
        val itemSlot = ArmorSlot.fromEquipSlotName(currentItem.equipSlotName()) ?: return
        val equipmentSlot = itemSlot.inventorySlot

        // Prevent vanilla items from being dragged onto custom items in offhand
        val cursor = event.cursor
        if (cursor.type != Material.AIR && !cursor.isCustomEquippable()) {
            event.isCancelled = true
            return
        }

        // Handle move to other inventory and hotbar swap
        if (event.action == InventoryAction.MOVE_TO_OTHER_INVENTORY ||
            event.action == InventoryAction.HOTBAR_SWAP) {

            event.isCancelled = true

            // Check if target armor slot is available
            val armorItem = player.inventory.getItem(equipmentSlot)
            if (armorItem == null || armorItem.type == Material.AIR) {
                // Process equipment change to armor slot
                if (processEquipmentChange(currentItem, itemSlot, itemSlot.rawSlot, player, event)) {
                    player.inventory.setItem(equipmentSlot, currentItem)
                    player.inventory.setItemInOffHand(null)
                    player.updateInventory()
                }
            } else {
                // Target slot occupied, try to move elsewhere
                if (event.action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                    // Try hotbar first, then main inventory if needed
                    if (!moveItemToSection(currentItem, player, 0, 8, event) &&
                        player.inventory.itemInOffHand == currentItem) {
                        moveItemToSection(currentItem, player, 9, 35, event)
                    }
                }
            }
        }
    }

    /**
     * Move item to first available slot in specified range
     * Returns true if successful, false otherwise
     */
    private fun moveItemToSection(
        item: ItemStack,
        player: Player,
        startSlot: Int,
        endSlot: Int,
        event: InventoryClickEvent
    ): Boolean {
        // Find first empty slot
        for (i in startSlot..endSlot) {
            val slotItem = player.inventory.getItem(i)
            if (slotItem == null || slotItem.type == Material.AIR) {
                // Cancel default behavior
                event.isCancelled = true

                // Move item
                val itemCopy = item.clone()

                // Remove from source location
                if (event.rawSlot == OFFHAND_RAW_SLOT || event.slot == OFFHAND_INVENTORY_SLOT) {
                    player.inventory.setItemInOffHand(null)
                } else {
                    player.inventory.setItem(event.slot, null)
                }

                // Place in empty slot
                player.inventory.setItem(i, itemCopy)
                player.updateInventory()

                return true
            }
        }
        return false
    }

    /**
     * Creative mode inventory interactions handler
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onCreativeModeEquip(event: InventoryCreativeEvent) {
        val player = event.whoClicked as? Player ?: return

        // Handle armor slot interactions in creative mode
        if (event.slotType == InventoryType.SlotType.ARMOR) {
            val item = event.cursor ?: return
            if (item.type == Material.AIR) return

            // Skip vanilla items
            if (!item.isCustomEquippable()) return

            // Get slot information
            val targetSlot = ArmorSlot.fromRawSlot(event.rawSlot) ?: return
            val itemSlot = ArmorSlot.fromEquipSlotName(item.equipSlotName()) ?: return

            // Verify correct slot
            if (targetSlot != itemSlot) {
                event.isCancelled = true
                event.result = Event.Result.DENY
                player.sendMessage("§cThis item can only be equipped in the ${itemSlot.name.lowercase()} slot.")
                return
            }

            // Verify and trigger equip event
            if (!checkItemSlotCompatibility(item, targetSlot)) {
                event.isCancelled = true
                event.result = Event.Result.DENY
                warn("Prevented equipping item ${item.type} in wrong slot in creative mode")
                return
            }

            if (!triggerEquipEvent(item, event.rawSlot, player)) {
                event.isCancelled = true
                event.result = Event.Result.DENY
                return
            }

            // Allow creative mode equipping to proceed
        }
    }

    /**
     * Check if a raw slot is an armor slot
     */
    private fun isArmorSlot(rawSlot: Int): Boolean = ArmorSlot.fromRawSlot(rawSlot) != null

    /**
     * Centralized equipment processing logic
     * Returns true if equipment should proceed, false if cancelled
     */
    private fun processEquipmentChange(
        item: ItemStack,
        targetSlot: ArmorSlot,
        rawSlot: Int,
        player: Player,
        event: InventoryEvent
    ): Boolean {
        // Check if item can be equipped in this slot
        if (!checkItemSlotCompatibility(item, targetSlot)) {
            setCancelled(event)
            return false
        }

        // Trigger custom equip event
        if (!triggerEquipEvent(item, rawSlot, player)) {
            setCancelled(event)
            return false
        }

        // Cancel vanilla behavior
        setCancelled(event)
        return true
    }

    /**
     * Set cancelled state for different inventory event types
     */
    private fun setCancelled(event: InventoryEvent) {
        when (event) {
            is InventoryClickEvent -> {
                event.isCancelled = true
                event.result = Event.Result.DENY
            }
            is InventoryDragEvent -> {
                event.isCancelled = true
                event.result = Event.Result.DENY
            }
            is InventoryCreativeEvent -> {
                event.isCancelled = true
                event.result = Event.Result.DENY
            }
        }
    }

    /**
     * Check if an item is compatible with the target slot
     */
    private fun checkItemSlotCompatibility(item: ItemStack, targetSlot: ArmorSlot): Boolean {
        // Skip if not a custom equippable item
        if (!item.isCustomEquippable()) return false

        // Get the custom equipment slot for this item
        val equipSlotName = item.equipSlotName()

        // Map to appropriate armor slot
        val itemSlot = ArmorSlot.fromEquipSlotName(equipSlotName) ?: return false

        // Check if slot types match
        return itemSlot == targetSlot
    }

    /**
     * Trigger the CustomItemEquipEvent
     * Returns true if event was not cancelled
     */
    private fun triggerEquipEvent(
        item: ItemStack,
        rawSlot: Int,
        player: Player
    ): Boolean {
        // Get behavior for this item
        val itemBehaviour = item.getBehaviour() ?: ItemBehaviour.GENERIC

        // Create and call event
        val customItemEquipEvent = CustomItemEquipEvent(item, itemBehaviour, rawSlot, player)
        Bukkit.getServer().pluginManager.callEvent(customItemEquipEvent)

        return !customItemEquipEvent.isCancelled
    }
}