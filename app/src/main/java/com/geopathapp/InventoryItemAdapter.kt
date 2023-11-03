package com.geopathapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class InventoryItemAdapter(private val itemList : ArrayList<GameItem>) : RecyclerView.Adapter<InventoryItemAdapter.InventoryItemHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InventoryItemHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.inventorylayout, parent, false)
        return InventoryItemHolder(itemView)
    }

    override fun onBindViewHolder(holder: InventoryItemHolder, position: Int) {
        val currentItem = itemList[position]
        val lootActions = LootActions()

        holder.itemName.text = currentItem.gameItemName
        holder.quantity.text = currentItem.gameItemQuant.toString()
        currentItem.gameItemId?.let { lootActions.getImgFromId(it) }?.let {
            holder.itemImg.setImageResource(it)
        }
    }

    override fun getItemCount(): Int {
        return itemList.size
    }

    class InventoryItemHolder(itemView : View) : RecyclerView.ViewHolder(itemView) {
        val itemName : TextView = itemView.findViewById(R.id.itemNamePlaceholder)
        val quantity : TextView = itemView.findViewById(R.id.quantityPlaceholder)
        val itemImg : ImageView = itemView.findViewById(R.id.invImgPlaceholder)
    }

}