package gg.tropic.practice.services

import com.google.gson.reflect.TypeToken
import com.mojang.authlib.GameProfile
import gg.scala.basics.plugin.tablist.TabListService
import gg.scala.commons.agnostic.sync.server.ServerContainer
import gg.scala.commons.agnostic.sync.server.impl.GameServer
import gg.scala.commons.annotations.commands.customizer.CommandManagerCustomizer
import gg.scala.commons.command.ScalaCommandManager
import gg.scala.commons.tablist.TablistPopulator
import gg.scala.flavor.service.Configure
import gg.scala.flavor.service.Service
import gg.scala.lemon.Lemon
import gg.scala.lemon.command.ListCommand
import gg.scala.lemon.handler.PlayerHandler
import gg.scala.lemon.util.QuickAccess
import gg.tropic.practice.configuration.PracticeConfigurationService
import io.github.nosequel.tab.shared.entry.TabElement
import io.github.nosequel.tab.shared.entry.TabEntry
import io.github.nosequel.tab.shared.skin.SkinType
import me.lucko.helper.Schedulers
import net.evilblock.cubed.ScalaCommonsSpigot
import net.evilblock.cubed.serializers.Serializers
import net.evilblock.cubed.util.nms.MinecraftReflection
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture

/**
 * @author GrowlyX
 * @since 12/17/2023
 */
@Service
object MIPPlayerCache : TablistPopulator
{
    private var playerIDs = setOf<String>()

    data class OnlinePracticePlayer(
        val skinValue: String,
        val skinSignature: String,
        val ping: Int,
        val rankWeight: Int,
        val username: String,
        val displayName: String
    )

    private var localModelCache = listOf<OnlinePracticePlayer>()

    @Configure
    fun configure()
    {
        val cache = ScalaCommonsSpigot.instance.kvConnection

        Schedulers
            .async()
            .runRepeating({ _ ->
                val localModels = mutableListOf<OnlinePracticePlayer>()

                Bukkit.getOnlinePlayers()
                    .sortedByDescending {
                        QuickAccess.realRank(it).weight
                    }
                    .forEach {
                        val gameProfile = MinecraftReflection
                            .getGameProfile(it) as GameProfile

                        val lemonPlayer = PlayerHandler
                            .find(it.uniqueId)
                            ?: return@forEach

                        if (it.hasMetadata("vanished"))
                        {
                            return@forEach
                        }

                        val rank = QuickAccess.realRank(it)

                        /*val strippedPrefix = ChatColor
                            .stripColor(rank.prefix)

                        val strippedSuffix = ChatColor
                            .stripColor(rank.suffix)

                        val prefix =
                            (if (strippedPrefix.isNotEmpty())
                                "${rank.prefix} " else "")

                        val suffix = if (strippedSuffix.isNotEmpty())
                            " ${rank.suffix}" else ""

                        val composed = "$prefix${rank.color}${
                            lemonPlayer.getColoredName()
                        }$suffix"*/

                        val textures = gameProfile.properties
                            .get("textures").firstOrNull()

                        val data = if (textures == null)
                            SkinType.LIGHT_GRAY.skinData else arrayOf(
                            textures.value, textures.signature
                        )

                        localModels += OnlinePracticePlayer(
                            skinValue = data.first(),
                            skinSignature = data[1],
                            ping = MinecraftReflection.getPing(it),
                            rankWeight = QuickAccess.realRank(it).weight,
                            username = it.name,
                            displayName = lemonPlayer.getColoredName()
                        )
                    }

                cache.sync().hset(
                    "tropicpractice:player-sync",
                    Lemon.instance.settings.id,
                    Serializers.gson.toJson(localModels)
                )
            }, 0L, 3L)

        val typeToken = object : TypeToken<List<OnlinePracticePlayer>>()
        {}.type

        var playerList = ListCommand.PlayerList(0, listOf())

        Schedulers
            .async()
            .runRepeating({ _ ->
                val mappings = cache.sync()
                    .hgetall("tropicpractice:player-sync")
                    .let {
                        it
                            .filterKeys { k -> ServerContainer.getServer(k) != null }
                            .mapValues { v ->
                                Serializers.gson
                                    .fromJson<List<OnlinePracticePlayer>>(v.value, typeToken)
                            }
                    }

                localModelCache = mappings.values.flatten().toList()
                playerIDs = localModelCache.map { it.username }.toSet()

                val maxPlayerCount = ServerContainer
                    .getServersInGroupCasted<GameServer>("mip")
                    .sumOf { it.getMaxPlayers()!! }

                playerList = ListCommand.PlayerList(
                    maxCount = maxPlayerCount,
                    sortedPlayerEntries = localModelCache
                        .sortedByDescending(OnlinePracticePlayer::rankWeight)
                        .map(OnlinePracticePlayer::displayName)
                )

            }, 0L, 5)

        ListCommand.supplyCustomPlayerList { playerList }
    }

    override fun displayPreProcessor(player: Player) =
        CompletableFuture.completedFuture(
            PracticeConfigurationService.cached().enableMIPTabHandler()
        )!!

    override fun populate(player: Player, element: TabElement)
    {
        element.header = LegacyComponentSerializer
            .legacySection()
            .serialize(TabListService.cached().header)
        element.footer = LegacyComponentSerializer
            .legacySection()
            .serialize(TabListService.cached().footer)

        localModelCache
            .sortedByDescending(OnlinePracticePlayer::rankWeight)
            .take(80)
            .forEachIndexed { index, other ->
                val entry = TabEntry(
                    index / 20, index % 20,
                    other.displayName,
                    other.ping, arrayOf(
                        other.skinValue,
                        other.skinSignature
                    )
                )

                element.add(entry)
            }
    }

    @CommandManagerCustomizer
    fun customize(manager: ScalaCommandManager)
    {
        manager.commandCompletions.registerCompletion("mip-players") { playerIDs }
    }
}
