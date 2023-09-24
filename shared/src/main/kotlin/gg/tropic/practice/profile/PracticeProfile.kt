package gg.tropic.practice.profile

import gg.scala.store.controller.DataStoreObjectControllerCache
import gg.scala.store.storage.storable.IDataStoreObject
import gg.tropic.practice.kit.Kit
import gg.tropic.practice.profile.loadout.Loadout
import gg.tropic.practice.statistics.GlobalStatistics
import gg.tropic.practice.statistics.KitStatistics
import gg.tropic.practice.statistics.ranked.RankedKitStatistics
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * @author GrowlyX
 * @since 9/17/2023
 */
data class PracticeProfile(
    override val identifier: UUID
) : IDataStoreObject
{
    val globalStatistics = GlobalStatistics()
    val casualStatistics = ConcurrentHashMap<
        String,
        KitStatistics
    >()

    val rankedStatistics = ConcurrentHashMap<
        String,
        RankedKitStatistics
    >()

    val customLoadouts = mutableMapOf<
        String,
        ConcurrentHashMap<
            String, // "Default #1"
            Loadout
        >
    >()

    fun getLoadoutsFromKit(kit: Kit) = customLoadouts[kit.id] ?: ConcurrentHashMap()

    fun save() = DataStoreObjectControllerCache
        .findNotNull<PracticeProfile>()
        .save(this)
}
