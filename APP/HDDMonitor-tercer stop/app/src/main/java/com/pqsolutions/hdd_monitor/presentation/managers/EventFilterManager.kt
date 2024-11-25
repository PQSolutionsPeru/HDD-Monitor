package com.pqsolutions.hdd_monitor.presentation.managers

import android.util.Log
import com.pqsolutions.hdd_monitor.data.Event
import com.pqsolutions.hdd_monitor.presentation.state.EventFilter
import com.pqsolutions.hdd_monitor.presentation.state.EventSortOption
import java.time.LocalDateTime

class EventFilterManager {
    companion object {
        private const val TAG = "EventFilterManager"
    }

    fun applyFilterAndSort(
        events: List<Event>,
        currentFilter: EventFilter,
        currentSort: EventSortOption
    ): List<Event> {
        return events
            .filter { event ->
                when (currentFilter) {
                    is EventFilter.All -> true
                    is EventFilter.Programmed -> event.isProgramado
                    is EventFilter.Accepted -> event.isAceptado
                    is EventFilter.ByClient -> event.clientDocName == currentFilter.clientDocName
                    is EventFilter.ByType -> event.type == currentFilter.eventType
                }
            }
            .sortedWith { a, b ->
                when (currentSort.field) {
                    EventSortOption.SortField.DATE -> {
                        val dateComparison = (a.dateTime ?: LocalDateTime.MIN)
                            .compareTo(b.dateTime ?: LocalDateTime.MIN)
                        if (currentSort.direction == EventSortOption.SortDirection.DESC)
                            dateComparison * -1 else dateComparison
                    }
                    EventSortOption.SortField.STATUS -> {
                        val comparison = a.status.compareTo(b.status)
                        if (currentSort.direction == EventSortOption.SortDirection.DESC)
                            comparison * -1 else comparison
                    }
                    EventSortOption.SortField.TITLE -> {
                        val comparison = a.title.compareTo(b.title)
                        if (currentSort.direction == EventSortOption.SortDirection.DESC)
                            comparison * -1 else comparison
                    }
                    EventSortOption.SortField.TYPE -> {
                        val comparison = (a.type ?: "").compareTo(b.type ?: "")
                        if (currentSort.direction == EventSortOption.SortDirection.DESC)
                            comparison * -1 else comparison
                    }
                }
            }.also {
                Log.d(TAG, "Applied filter: $currentFilter and sort: $currentSort, resulting in ${it.size} events")
            }
    }

    fun getFilterDescription(filter: EventFilter): String {
        return when (filter) {
            is EventFilter.All -> "Todos los eventos"
            is EventFilter.Programmed -> "Eventos programados"
            is EventFilter.Accepted -> "Eventos aceptados"
            is EventFilter.ByClient -> "Eventos del cliente: ${filter.clientDocName}"
            is EventFilter.ByType -> "Eventos de tipo: ${filter.eventType}"
        }
    }

    fun getSortDescription(sort: EventSortOption): String {
        val fieldDesc = when (sort.field) {
            EventSortOption.SortField.DATE -> "Fecha"
            EventSortOption.SortField.STATUS -> "Estado"
            EventSortOption.SortField.TITLE -> "Título"
            EventSortOption.SortField.TYPE -> "Tipo"
        }
        val directionDesc = when (sort.direction) {
            EventSortOption.SortDirection.ASC -> "ascendente"
            EventSortOption.SortDirection.DESC -> "descendente"
        }
        return "Ordenado por $fieldDesc en orden $directionDesc"
    }
}