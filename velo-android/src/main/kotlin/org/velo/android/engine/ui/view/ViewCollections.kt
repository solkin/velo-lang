package org.velo.android.engine.ui.view

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.tabs.TabLayout
import core.VeloFunction

/**
 * Collection-widget operations for a [ViewState]: the recycling text list and the tab row,
 * plus the [VeloListAdapter] backing the list.
 */

/** Replace the rows of a list with these text items. */
internal fun ViewState.items(rows: List<String>) {
    ui { listAdapter()?.submit(rows) }
}

/** Fire [cb] with the row index when a list item is tapped. */
internal fun ViewState.onItemClick(cb: VeloFunction) {
    retain(cb)
    ui { listAdapter()?.onClick = { index -> cb.post(index) } }
}

/** Append a tab with the given [label]. */
internal fun ViewState.tab(label: String) {
    ui { (av as? TabLayout)?.let { it.addTab(it.newTab().setText(label)) } }
}

/** Fire [cb] with the selected index whenever a tab row's or radio group's selection changes. */
internal fun ViewState.onSelect(cb: VeloFunction) {
    retain(cb)
    ui {
        when (val v = av) {
            is TabLayout -> v.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) { cb.post(tab.position) }
                override fun onTabUnselected(tab: TabLayout.Tab) {}
                override fun onTabReselected(tab: TabLayout.Tab) {}
            })
            is RadioGroup -> v.setOnCheckedChangeListener { group, checkedId ->
                cb.post(group.indexOfChild(group.findViewById(checkedId)))
            }
            is NavigationBarView -> v.setOnItemSelectedListener { mi -> cb.post(mi.itemId); true }
            else -> {}
        }
    }
}

/** Select the tab or navigation destination at [index] programmatically. */
internal fun ViewState.select(index: Int) {
    ui {
        when (val v = av) {
            is TabLayout -> v.getTabAt(index)?.select()
            is NavigationBarView -> v.selectedItemId = index
            else -> {}
        }
    }
}

/**
 * A minimal text-row [RecyclerView] adapter for the Velo `list` widget: each row is a
 * Material list-item text view, tapping it fires [onClick] with the row index. Real
 * recycling, so large lists stay cheap — richer per-row content is out of scope for v1.
 */
internal class VeloListAdapter : RecyclerView.Adapter<VeloListAdapter.Row>() {

    private val items = ArrayList<String>()
    var onClick: ((Int) -> Unit)? = null

    @SuppressLint("NotifyDataSetChanged")
    fun submit(rows: List<String>) {
        items.clear()
        items.addAll(rows)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Row {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return Row(view as TextView)
    }

    override fun onBindViewHolder(holder: Row, position: Int) {
        holder.label.text = items[position]
        holder.label.setOnClickListener { onClick?.invoke(holder.bindingAdapterPosition) }
    }

    class Row(val label: TextView) : RecyclerView.ViewHolder(label)
}
