import gg.scala.basics.plugin.profile.BasicsProfileService
import gg.scala.basics.plugin.settings.defaults.values.StateSettingValue
import gg.scala.commons.acf.ConditionFailedException
import gg.scala.commons.acf.annotation.CommandAlias
import gg.scala.commons.annotations.commands.AutoRegister
import gg.scala.commons.command.ScalaCommand
import gg.scala.commons.issuer.ScalaPlayer
import net.evilblock.cubed.util.CC

/**
 * @author Elb1to
 * @since 10/19/2023
 */
@AutoRegister
object ToggleSpectatorsCommand : ScalaCommand()
{
    @CommandAlias(
        "togglespecs|togglespectators"
    )
    fun onSpectationToggle(player: ScalaPlayer)
    {
        val profile = BasicsProfileService.find(player.bukkit())
            ?: throw ConditionFailedException(
                "Sorry, your profile did not load properly."
            )

        val allowSpectators = profile.settings["duels:allow-spectators"]!!
        val mapped = allowSpectators.map<StateSettingValue>()

        if (mapped == StateSettingValue.ENABLED)
        {
            allowSpectators.value = "DISABLED"
            player.sendMessage(
                "${CC.RED}Players are no longer able to spectate your matches."
            )
        } else
        {
            allowSpectators.value = "ENABLED"
            player.sendMessage(
                "${CC.GREEN}Players are now able to spectate your matches."
            )
        }

        profile.save()
    }
}