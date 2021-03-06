package knf.kuma.record

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import knf.kuma.R
import knf.kuma.animeinfo.ActivityAnimeMaterial
import knf.kuma.commons.*
import knf.kuma.database.CacheDB
import knf.kuma.pojos.RecordObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import xdroid.toaster.Toaster
import java.util.*

class RecordsAdapterMaterial(private val activity: AppCompatActivity) : RecyclerView.Adapter<RecordsAdapterMaterial.RecordItem>() {
    private var items: MutableList<RecordObject> = ArrayList()

    private val dao = CacheDB.INSTANCE.recordsDAO()

    private val layout: Int
        @LayoutRes
        get() = if (PrefsUtil.layType == "0") {
            R.layout.item_record_material
        } else {
            R.layout.item_record_grid_material
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordItem {
        return RecordItem(LayoutInflater.from(parent.context).inflate(layout, parent, false))
    }

    override fun onBindViewHolder(holder: RecordItem, position: Int) {
        val item = items[position]
        val animeObject = item.animeObject
        animeObject?.let { holder.imageView.load(PatternUtil.getCover(animeObject.aid)) }
        holder.title.text = item.name
        holder.chapter.text = getCardText(item)
        holder.cardView.setOnClickListener {
            if (item.animeObject != null)
                ActivityAnimeMaterial.open(activity, item, holder.imageView)
            else
                Toaster.toast("Error al abrir")
        }
    }

    private fun getCardText(recordObject: RecordObject): String {
        return if (!recordObject.chapter.startsWith("Episodio "))
            "Episodio ${recordObject.chapter}"
        else
            recordObject.chapter
    }

    override fun getItemCount(): Int {
        return items.size
    }

    fun remove(position: Int) {
        activity.lifecycleScope.launch(Dispatchers.IO){
            dao.delete(items[position])
            items.removeAt(position)
            launch(Dispatchers.Main) {
                notifyItemRemoved(position)
            }
        }
    }

    fun update(items: MutableList<RecordObject>) {
        if (this.items notSameContent items) {
            this.items = items
            notifyDataSetChanged()
        }
    }

    inner class RecordItem(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: View by itemView.bind(R.id.card)
        val imageView: ImageView by itemView.bind(R.id.img)
        val title: TextView by itemView.bind(R.id.title)
        val chapter: TextView by itemView.bind(R.id.chapter)
    }
}
