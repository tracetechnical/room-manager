package uk.co.tracetechnicalservices.roommanager.repositories

import org.springframework.stereotype.Component
import uk.co.tracetechnicalservices.roommanager.models.DimmerGroup
import java.util.*

@Component
class DimmerGroupRepository {
    val dimmerGroups: MutableMap<String, DimmerGroup> = emptyMap<String, DimmerGroup>().toMutableMap()

    fun getAll(): Map<String, DimmerGroup> {
        return dimmerGroups
    }

    fun getByName(name: String): Optional<DimmerGroup> {
        if (dimmerGroups[name] == null)
            return Optional.empty()
        else
            return Optional.of(dimmerGroups[name]!!)
    }

    fun getById(id: Int): Optional<DimmerGroup> {
        return dimmerGroups.values.stream().filter { a -> a.groupIdx == id }.findFirst()
    }

    fun put(name: String, dimmerGroup: DimmerGroup) {
        dimmerGroups[name] = dimmerGroup
    }
}