package knf.kuma.random

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import knf.kuma.R
import knf.kuma.animeinfo.ActivityAnimeMaterial
import knf.kuma.commons.*
import java.util.*

internal class RandomAdapterMaterial(private val activity: Activity) : RecyclerView.Adapter<RandomAdapterMaterial.RandomItem>() {
    private var list: List<RandomObject> = ArrayList()

    private val layout: Int
        @LayoutRes
        get() = if (PrefsUtil.layType == "0") {
            R.layout.item_fav_material
        } else {
            R.layout.item_fav_grid_material
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RandomItem {
        return RandomItem(LayoutInflater.from(activity).inflate(layout, parent, false))
    }

    override fun onBindViewHolder(holder: RandomItem, position: Int) {
        val animeObject = list[position]
        holder.imageView.load(PatternUtil.getCover(animeObject.aid))
        holder.title.text = animeObject.name
        holder.type.text = animeObject.type
        holder.cardView.setOnClickListener { ActivityAnimeMaterial.open(activity, animeObject, holder.imageView, false, true) }
    }

    override fun getItemCount(): Int {
        return list.size
    }

    fun update(list: List<RandomObject>) {
        if (this.list notSameContent list) {
            this.list = list
            notifyDataSetChanged()
        }
    }

    internal inner class RandomItem(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: View by itemView.bind(R.id.card)
        val imageView: ImageView by itemView.bind(R.id.img)
        val title: TextView by itemView.bind(R.id.title)
        val type: TextView by itemView.bind(R.id.type)
    }
}
