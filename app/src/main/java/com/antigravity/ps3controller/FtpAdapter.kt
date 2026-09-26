package com.antigravity.ps3controller

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class FtpAdapter(
    private val onItemClick: (Ps3Item) -> Unit,
    private val onDownloadClick: (Ps3Item) -> Unit,
    private val onRenameClick: (Ps3Item) -> Unit,
    private val onDeleteClick: (Ps3Item) -> Unit
) : RecyclerView.Adapter<FtpAdapter.FtpViewHolder>() {

    private val items = mutableListOf<Ps3Item>()

    fun submitList(newItems: List<Ps3Item>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FtpViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ftp_file, parent, false)
        return FtpViewHolder(view)
    }

    override fun onBindViewHolder(holder: FtpViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class FtpViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvIcon: TextView = itemView.findViewById(R.id.tvItemIcon)
        private val tvName: TextView = itemView.findViewById(R.id.tvItemName)
        private val tvSubtitle: TextView = itemView.findViewById(R.id.tvItemSubtitle)
        private val btnOptions: TextView = itemView.findViewById(R.id.btnItemOptions)

        fun bind(item: Ps3Item) {
            tvName.text = item.name

            if (item.isDirectory) {
                tvIcon.text = if (item.name == "..") "⬆️" else "📁"
                tvSubtitle.text = "Carpeta de archivos"
            } else {
                tvIcon.text = getFileIcon(item.name)
                tvSubtitle.text = formatFileSize(item.size)
            }

            itemView.setOnClickListener { onItemClick(item) }

            btnOptions.setOnClickListener { view ->
                showPopupMenu(view, item)
            }
        }

        private fun showPopupMenu(view: View, item: Ps3Item) {
            val popup = PopupMenu(view.context, view)
            if (!item.isDirectory) {
                popup.menu.add(0, 1, 0, "⬇️ Descargar a Teléfono")
            }
            if (item.name != "..") {
                popup.menu.add(0, 2, 1, "✏️ Renombrar")
                popup.menu.add(0, 3, 2, "🗑️ Eliminar")
            }
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    1 -> onDownloadClick(item)
                    2 -> onRenameClick(item)
                    3 -> onDeleteClick(item)
                }
                true
            }
            popup.show()
        }

        private fun getFileIcon(fileName: String): String {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "pkg" -> "📦"
                "iso" -> "💿"
                "p3t" -> "🎨"
                "png", "jpg", "jpeg", "bmp" -> "🖼️"
                "mp3", "wav", "aac" -> "🎵"
                "mp4", "mkv", "avi" -> "🎬"
                "bin", "self", "edat", "sprx" -> "⚙️"
                "txt", "ini", "cfg", "log" -> "📄"
                else -> "📄"
            }
        }

        private fun formatFileSize(size: Long): String {
            if (size <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
            return String.format(Locale.US, "%.2f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
        }
    }
}
