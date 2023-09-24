package gg.tropic.practice.menu.editor

import gg.tropic.practice.kit.Kit
import gg.tropic.practice.menu.template.TemplateKitMenu
import gg.tropic.practice.profile.PracticeProfile
import gg.tropic.practice.profile.loadout.Loadout
import net.evilblock.cubed.util.CC
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType

class EditorKitSelectionMenu(
    private val practiceProfile: PracticeProfile
) : TemplateKitMenu()
{

    override fun filterDisplayOfKit(player: Player, kit: Kit): Boolean = true

    override fun itemDescriptionOf(player: Player, kit: Kit): List<String>
    {
        val loadouts = practiceProfile.getLoadoutsFromKit(kit)

        return listOf(
            " ",
            "${CC.GRAY}Custom Kits: ${loadouts.size}/8",
            " ",
            "${CC.B_RED}Shift-Click to edit",
            "${CC.GREEN}Click to create new"
        )
    }

    override fun itemClicked(player: Player, kit: Kit, type: ClickType)
    {
        val loadouts = practiceProfile.getLoadoutsFromKit(kit)

        if (loadouts.size == 0)
        {
            EditLoadoutContentsMenu(
                kit, Loadout(
                    "Default #1",
                    kit.id,
                    System.currentTimeMillis()
                ), practiceProfile
            )
        } else
        {
            SelectCustomKitMenu(
                practiceProfile,
                loadouts,
                kit
            ).openMenu(player)
        }

    }

    override fun getPrePaginatedTitle(player: Player): String
    {
        return "Select a kit to edit"
    }
}