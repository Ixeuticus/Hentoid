package me.devsaki.hentoid.viewholders

import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterInside
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.signature.ObjectKey
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.IExpandable
import com.mikepenz.fastadapter.IParentItem
import com.mikepenz.fastadapter.ISubItem
import com.mikepenz.fastadapter.items.AbstractItem
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.bundles.ImageItemBundle
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.util.Helper

class ImageFileItemK(private val image: ImageFile, private val showChapter: Boolean) :
    AbstractItem<ImageFileItemK.ViewHolder>(),
    IExpandable<ImageFileItemK.ViewHolder>,
    INestedItem<ImageFileItemK.ViewHolder> {
    private val chapter: Chapter
    private var isCurrent = false
    private val expanded = false

    val glideRequestOptions: RequestOptions

    init {
        val centerInside = CenterInside()
        glideRequestOptions = RequestOptions().optionalTransform(centerInside)

        chapter = image.linkedChapter ?: Chapter(1, "", "") // Default display when nothing is set
        identifier = image.uniqueHash()
    }

    // Return a copy, not the original instance that has to remain in synch with its visual representation
    fun getImage(): ImageFile {
        return image
    }

    fun setCurrent(current: Boolean) {
        isCurrent = current
    }

    fun isFavourite(): Boolean {
        return image.isFavourite
    }

    fun getChapterOrder(): Int {
        return chapter.order
    }

    override val isAutoExpanding: Boolean
        get() = true
    override var isExpanded: Boolean
        get() = expanded
        set(value) {}
    override var parent: IParentItem<*>?
        get() = null
        set(value) {}
    override var subItems: MutableList<ISubItem<*>>
        get() = mutableListOf()
        set(value) { /* Nothing */ }
    override val layoutRes: Int
        get() = R.layout.item_reader_gallery_image

    override fun getLevel(): Int {
        return 1
    }

    override val type: Int
        get() = R.id.gallery_image

    override fun getViewHolder(v: View): ViewHolder {
        return ViewHolder(v)
    }

    class ViewHolder internal constructor(view: View) :
        FastAdapter.ViewHolder<ImageFileItemK>(view) {
        private val pageNumberTxt: TextView
        private val image: ImageView?
        private val checkedIndicator: ImageView
        private val chapterOverlay: TextView

        init {
            pageNumberTxt = ViewCompat.requireViewById(view, R.id.viewer_gallery_pagenumber_text)
            image = ViewCompat.requireViewById(view, R.id.viewer_gallery_image)
            checkedIndicator = ViewCompat.requireViewById(
                view, R.id.checked_indicator
            )
            chapterOverlay = ViewCompat.requireViewById(view, R.id.chapter_overlay)
        }

        override fun bindView(item: ImageFileItemK, payloads: List<Any>) {

            // Payloads are set when the content stays the same but some properties alone change
            if (payloads.isNotEmpty()) {
                val bundle = payloads[0] as Bundle
                val bundleParser = ImageItemBundle(bundle)
                val boolValue = bundleParser.isFavourite
                if (boolValue != null) item.image.isFavourite = boolValue
                val intValue = bundleParser.chapterOrder
                if (intValue != null) item.chapter.order = intValue
            }
            updateText(item)

            // Checkmark
            if (item.isSelected) checkedIndicator.visibility =
                View.VISIBLE else checkedIndicator.visibility =
                View.GONE

            // Chapter overlay
            if (item.showChapter) {
                var chapterText = checkedIndicator.context.resources.getString(
                    R.string.gallery_display_chapter,
                    item.chapter.order
                )
                if (item.chapter.order == Int.MAX_VALUE) chapterText = "" // Don't show temp values
                chapterOverlay.text = chapterText
                chapterOverlay.setBackgroundColor(
                    chapterOverlay.resources.getColor(
                        if (0 == item.chapter.order % 2) R.color.black_opacity_50 else R.color.white_opacity_25
                    )
                )
                chapterOverlay.visibility = View.VISIBLE
            } else chapterOverlay.visibility = View.GONE

            // Image
            Glide.with(image!!)
                .load(Uri.parse(item.image.fileUri))
                .signature(ObjectKey(item.image.uniqueHash()))
                .apply(item.glideRequestOptions)
                .into(image)
        }

        private fun updateText(item: ImageFileItemK) {
            val currentBegin = if (item.isCurrent) ">" else ""
            val currentEnd = if (item.isCurrent) "<" else ""
            val isFavourite = if (item.isFavourite()) HEART_SYMBOL else ""
            pageNumberTxt.text = pageNumberTxt.resources.getString(
                R.string.gallery_display_page,
                currentBegin,
                item.image.order,
                isFavourite,
                currentEnd
            )
            if (item.isCurrent) pageNumberTxt.setTypeface(null, Typeface.BOLD)
        }

        override fun unbindView(item: ImageFileItemK) {
            if (image != null && Helper.isValidContextForGlide(image)) Glide.with(image)
                .clear(image)
        }

        companion object {
            private const val HEART_SYMBOL = "❤"
        }
    }
}