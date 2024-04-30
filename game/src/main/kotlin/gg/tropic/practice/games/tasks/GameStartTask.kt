package gg.tropic.practice.games.tasks

import dev.iiahmed.disguise.Disguise
import gg.scala.basics.plugin.disguise.DisguiseService
import gg.scala.commons.acf.ConditionFailedException
import gg.scala.lemon.util.QuickAccess.username
import gg.scala.staff.anticheat.AnticheatCheck
import gg.scala.staff.anticheat.AnticheatFeature
import gg.tropic.practice.commands.admin.RankedBanCommand
import gg.tropic.practice.commands.offlineProfile
import gg.tropic.practice.configuration.PracticeConfigurationService
import gg.tropic.practice.games.GameImpl
import gg.tropic.practice.games.GameState
import gg.tropic.practice.queue.QueueType
import gg.tropic.practice.games.event.GameStartEvent
import gg.tropic.practice.games.team.GameTeamSide
import gg.tropic.practice.kit.feature.FeatureFlag
import gg.tropic.practice.profile.PracticeProfileService
import gg.tropic.practice.resetAttributes
import me.lucko.helper.scheduler.Task
import net.evilblock.cubed.nametag.NametagHandler
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.Constants
import net.evilblock.cubed.util.nms.MinecraftReflection
import net.evilblock.cubed.util.time.Duration
import net.evilblock.cubed.util.time.TimeUtil
import net.evilblock.cubed.visibility.VisibilityHandler
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.TitlePart
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.scoreboard.DisplaySlot

/**
 * @author GrowlyX
 * @since 8/5/2022
 */
class GameStartTask(
    private val game: GameImpl
) : Runnable
{
    lateinit var task: Task

    override fun run()
    {
        if (this.game.activeCountdown >= 5)
        {
            this.game.state = GameState.Starting

            this.game.toBukkitPlayers()
                .filterNotNull()
                .forEach { player ->
                    VisibilityHandler.update(player)
//                    NametagHandler.reloadPlayer(player)

                    if (game.flag(FeatureFlag.HeartsBelowNameTag))
                    {
                        val objective = player.scoreboard
                            .getObjective("commonsHealth")
                            ?: player.scoreboard
                                .registerNewObjective(
                                    "commonsHealth", "health"
                                )

                        objective.displaySlot = DisplaySlot.BELOW_NAME
                        objective.displayName = "${CC.D_RED}${Constants.HEART_SYMBOL}"
                    }

                    if (game.flag(FeatureFlag.EntityDisguise))
                    {
                        val type = game
                            .flagMetaData(FeatureFlag.EntityDisguise, "type")
                            ?: "PLAYER"

                        DisguiseService.provider().disguise(
                            player,
                            Disguise
                                .builder()
                                .setEntityType(
                                    EntityType.valueOf(type)
                                )
                                .build()
                        )
                    }

                    player.resetAttributes()
                    game.enterLoadoutSelection(player)
                }

            val teamVersus = this.game.teams.values
                .reversed()
                .map { it.players.size }
                .joinToString("v")

            val gameType = when (true)
            {
                (game.expectationModel.queueType != null) -> game.expectationModel.queueType!!.name
                (game.expectationModel.queueId == "tournament") -> "Tournament"
                (game.expectationModel.queueId == "party") -> "Party"
                else -> "Private"
            }

            val components = mutableListOf(
                "",
                "${CC.PRI}$gameType $teamVersus ${game.kit.displayName}:",
                "${CC.GRAY}${Constants.THIN_VERTICAL_LINE}${CC.WHITE} Players: ${CC.PRI}${
                    this.game.teams[GameTeamSide.A]!!.players
                        .joinToString(", ") {
                            it.username()
                        }
                } and ${
                    this.game.teams[GameTeamSide.B]!!.players
                        .joinToString(", ") {
                            it.username()
                        }
                }"
            )

            if (game.expectationModel.queueType == QueueType.Ranked)
            {
                components += "${CC.GRAY}${Constants.THIN_VERTICAL_LINE}${CC.B_WHITE} ELOs:"

                this.game.teams.values
                    .flatMap { it.toBukkitPlayers() }
                    .filterNotNull()
                    .forEach {
                        val profile = PracticeProfileService.find(it)
                            ?: return@forEach
                        components += "  ${CC.GRAY}${Constants.SMALL_DOT_SYMBOL} ${CC.WHITE}${it.name}: ${CC.GREEN}${
                            profile.getRankedStatsFor(
                                game.kit
                            ).elo
                        }"
                    }
            }/* else
            {
                components += "${CC.GRAY}${Constants.THIN_VERTICAL_LINE}${CC.B_WHITE} Pings:"

                this.game.teams.values
                    .flatMap { it.toBukkitPlayers() }
                    .filterNotNull()
                    .forEach {
                        components += "  ${CC.GRAY}${Constants.SMALL_DOT_SYMBOL} ${CC.WHITE}${it.name}: ${CC.GREEN}${
                            MinecraftReflection.getPing(it)
                        }ms"
                    }
            }*/

            components += listOf(
                "${CC.GRAY}${Constants.THIN_VERTICAL_LINE} ${CC.WHITE}Map: ${CC.PRI}${game.map.displayName}",
                ""
            )

            this.game.sendMessage(
                *components.toTypedArray()
            )
        }

        when (this.game.activeCountdown)
        {
            5, 4, 3, 2, 1 ->
            {
                this.game.audiences {
                    it.sendTitlePart(
                        TitlePart.TITLE,
                        Component
                            .text(this.game.activeCountdown)
                            .decorate(TextDecoration.BOLD)
                            .color(NamedTextColor.GREEN)
                    )

                    it.sendTitlePart(
                        TitlePart.SUBTITLE,
                        Component.text("The game is starting!")
                    )
                }

                this.game.sendMessage(
                    "${CC.SEC}The game starts in ${CC.PRI}${this.game.activeCountdown}${CC.SEC} second${
                        if (this.game.activeCountdown == 1) "" else "s"
                    }..."
                )
                this.game.playSound(Sound.NOTE_STICKS)
            }
        }

        if (this.game.activeCountdown <= 0)
        {
            val event = GameStartEvent(this.game)
            Bukkit.getPluginManager().callEvent(event)

            if (event.isCancelled)
            {
                game.state = GameState.Completed
                game.closeAndCleanup()
                return
            }

            this.game.startTimestamp = System.currentTimeMillis()

            this.game.completeLoadoutSelection()
            this.game.sendMessage("${CC.GREEN}The game has started!")

            if (
                game.expectationModel.queueType == QueueType.Ranked ||
                game.expectationModel.queueId == "tournament"
            )
            {
                this.game.sendMessage(
                    " ",
                    "${CC.BD_RED}WARNING: ${CC.RED}Double Clicking is a punishable offence in all ranked matches. Adjusting your debounce time to 10ms or using a DC-prevention tool is highly recommended if you are unable to avoid double clicking.",
                    " "
                )

                if (Bukkit.getPluginManager().isPluginEnabled("anticheat"))
                {
                    fun Player.runAutoBanFor(reason: String)
                    {
                        val profile = uniqueId.offlineProfile
                        if (profile.hasActiveRankedBan())
                        {
                            return
                        }

                        profile.applyRankedBan(Duration.parse("7d"))
                        profile.deliverRankedBanMessage(player)
                        profile.saveAndPropagate()

                        Bukkit.dispatchCommand(
                            Bukkit.getConsoleSender(),
                            "terminatematch $name Anticheat Ban ($reason)"
                        )
                    }

                    game.toBukkitPlayers()
                        .filterNotNull()
                        .forEach { player ->
                            AnticheatFeature
                                .subscribeToSixtySecondSampleOf(
                                    player = player,
                                    check = AnticheatCheck.DOUBLE_CLICK,
                                    evaluate = { sample ->
                                        val thresholds = PracticeConfigurationService.cached().dataSampleThresholds()

                                        // If the player typically gets 3 or more violations in a 10-second period,
                                        // the player must be banned
                                        if (sample.accumulatedMedianOf() > thresholds.doubleClick)
                                        {
                                            player.runAutoBanFor("SADC")
                                        }
                                    }
                                )
                                .bindWith(game)

                            AnticheatFeature
                                .subscribeToSixtySecondSampleOf(
                                    player = player,
                                    check = AnticheatCheck.AUTO_CLICKER,
                                    evaluate = { sample ->
                                        val thresholds = PracticeConfigurationService.cached().dataSampleThresholds()

                                        // If the player typically gets 5 or more violations in a 10-second period,
                                        // the player must be banned
                                        if (sample.accumulatedMedianOf() > thresholds.autoClick)
                                        {
                                            player.runAutoBanFor("SAAC")
                                        }
                                    }
                                )
                                .bindWith(game)
                        }
                }
            }

            this.game.playSound(Sound.NOTE_PLING, pitch = 1.2f)
            this.game.audiences { it.clearTitle() }

            this.game.state = GameState.Playing
            this.task.closeAndReportException()
            return
        }

        this.game.activeCountdown--
    }
}
