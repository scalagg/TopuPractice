package gg.tropic.practice.menu.pipeline

import gg.scala.lemon.util.QuickAccess.username
import gg.tropic.practice.duel.DuelRequestUtilities
import gg.tropic.practice.games.DuelRequest
import gg.tropic.practice.kit.Kit
import gg.tropic.practice.kit.group.KitGroup
import gg.tropic.practice.kit.group.KitGroupService
import gg.tropic.practice.map.Map
import gg.tropic.practice.menu.template.TemplateKitMenu
import gg.tropic.practice.menu.template.TemplateMapMenu
import gg.tropic.practice.queue.QueueService
import net.evilblock.cubed.menu.Button
import net.evilblock.cubed.menu.Menu
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.ItemBuilder
import net.evilblock.cubed.util.bukkit.Tasks
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import java.util.UUID

/**
 * @author GrowlyX
 * @since 10/21/2023
 */
object DuelRequestPipeline
{
    private fun stage2ASendDuelRequestRandomMap(
        player: Player,
        target: UUID,
        kit: Kit
    )
    {
        val request = DuelRequest(
            requester = player.uniqueId,
            requestee = target,
            kitID = kit.id
        )

        player.closeInventory()
        player.sendMessage(
            "${CC.SEC}Sent a duel request to ${CC.GREEN}${target.username()}${CC.SEC} with the kit ${CC.GREEN}${kit.displayName}${CC.SEC} and a random map."
        )

        stage4PublishDuelRequest(request)
    }

    private fun stage3SendDuelRequest(
        player: Player,
        target: UUID,
        kit: Kit,
        map: Map
    )
    {
        val request = DuelRequest(
            requester = player.uniqueId,
            requestee = target,
            kitID = kit.id,
            mapID = map.name
        )

        player.closeInventory()
        player.sendMessage(
            "${CC.SEC}Sent a duel request to ${CC.GREEN}${target.username()}${CC.SEC} with the kit ${CC.GREEN}${kit.displayName}${CC.SEC} and map ${CC.GREEN}${
                map.displayName
            }${CC.SEC}."
        )

        stage4PublishDuelRequest(request)
    }

    private fun stage4PublishDuelRequest(request: DuelRequest)
    {
        QueueService.createMessage(
            "request-duel",
            "request" to request
        ).publish(
            channel = "practice:queue"
        )
    }

    private fun stage2BSendDuelRequestCustomMap(
        target: UUID,
        kit: Kit,
        previous: Menu,
        // weird initialization issues from parent/super class requires us to pass this through
        kitGroups: Set<String> = KitGroupService.groupsOf(kit)
            .map(KitGroup::id)
            .toSet()
    ) = object : TemplateMapMenu()
    {
        override fun filterDisplayOfMap(map: Map) = map.associatedKitGroups
            .intersect(kitGroups)
            .isNotEmpty()

        override fun itemTitleFor(player: Player, map: Map) = "${CC.B_GREEN}${map.displayName}"
        override fun itemDescriptionOf(player: Player, map: Map) = listOf(
            "",
            "${CC.GREEN}Click to select!"
        )

        override fun itemClicked(player: Player, map: Map, type: ClickType)
        {
            Button.playNeutral(player)
            stage3SendDuelRequest(player, target, kit, map)
        }

        override fun getGlobalButtons(player: Player) = mutableMapOf(
            4 to ItemBuilder
                .of(Material.NETHER_STAR)
                .name("${CC.B_AQUA}Random Map")
                .addToLore(
                    "",
                    "${CC.AQUA}Click to select!"
                )
                .toButton { _, _ ->
                    Button.playNeutral(player)
                    stage2ASendDuelRequestRandomMap(player, target, kit)
                }
        )

        override fun getPrePaginatedTitle(player: Player) = "Select a map..."

        override fun onClose(player: Player, manualClose: Boolean)
        {
            if (manualClose)
            {
                Tasks.sync { previous.openMenu(player) }
            }
        }
    }

    fun build(target: UUID) = object : TemplateKitMenu()
    {
        private var kitSelectionLock = false
        override fun filterDisplayOfKit(player: Player, kit: Kit) = true

        override fun itemTitleFor(player: Player, kit: Kit) = "${CC.B_GREEN}${kit.displayName}"
        override fun itemDescriptionOf(player: Player, kit: Kit) = listOf(
            "",
            "${CC.GREEN}Click to select!"
        )

        override fun itemClicked(player: Player, kit: Kit, type: ClickType)
        {
            if (kitSelectionLock)
            {
                return
            }

            kitSelectionLock = true

            DuelRequestUtilities
                .duelRequestExists(player.uniqueId, target, kit)
                .thenAccept {
                    if (it)
                    {
                        kitSelectionLock = false
                        player.sendMessage(
                            "${CC.RED}You already have an outgoing duel request to ${target.username()} with kit ${kit.displayName}!"
                        )
                        return@thenAccept
                    }

                    if (player.hasPermission("practice.duel.select-custom-map"))
                    {
                        val stage2B = stage2BSendDuelRequestCustomMap(target, kit, this)

                        if (!stage2B.ensureMapsAvailable())
                        {
                            kitSelectionLock = false

                            Button.playFail(player)
                            player.sendMessage("${CC.RED}There are no maps associated with the kit ${CC.YELLOW}${kit.displayName}${CC.RED}!")
                            return@thenAccept
                        }

                        kitSelectionLock = false

                        Button.playNeutral(player)
                        stage2B.openMenu(player)
                        return@thenAccept
                    }

                    kitSelectionLock = false

                    Button.playNeutral(player)
                    stage2ASendDuelRequestRandomMap(player, target, kit)
                }
                .exceptionally {
                    kitSelectionLock = false

                    it.printStackTrace()
                    return@exceptionally null
                }
        }

        override fun getPrePaginatedTitle(player: Player) = "Select a kit..."
    }
}