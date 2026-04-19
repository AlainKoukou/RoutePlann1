package com.univ.routeplanner.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.univ.routeplanner.R
import com.univ.routeplanner.data.db.RouteEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RouteHistoryAdapter(
    private var items: List<RouteEntity> = emptyList(),
    private val onItemClick: (RouteEntity) -> Unit
) : RecyclerView.Adapter<RouteHistoryAdapter.RouteViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())

    class RouteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvRouteTitle: TextView = view.findViewById(R.id.tvRouteTitle)
        val tvRouteMetrics: TextView = view.findViewById(R.id.tvRouteMetrics)
        val tvRouteTimestamp: TextView = view.findViewById(R.id.tvRouteTimestamp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_route_history, parent, false)
        return RouteViewHolder(view)
    }

    override fun onBindViewHolder(holder: RouteViewHolder, position: Int) {
        val route = items[position]

        // NEW: prefer friendly names, fall back to coords
        val originDisplay = route.originName ?: route.origin
        val destDisplay = route.destinationName ?: route.destination
        holder.tvRouteTitle.text = "$originDisplay  →  $destDisplay"

        val km = route.distanceMeters / 1000.0
        val min = route.durationSeconds / 60.0
        holder.tvRouteMetrics.text = "%.2f km  •  %.1f min".format(km, min)

        holder.tvRouteTimestamp.text = "Saved: ${dateFormat.format(Date(route.fetchedAt))}"

        holder.itemView.setOnClickListener { onItemClick(route) }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<RouteEntity>) {
        items = newItems
        notifyDataSetChanged()
    }
}