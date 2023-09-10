package me.devsaki.hentoid.viewholders

import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.fastadapter.*
import com.mikepenz.fastadapter.drag.IExtendedDraggable
import com.mikepenz.fastadapter.expandable.items.AbstractExpandableItem
import com.mikepenz.fastadapter.listeners.TouchEventHook
import com.mikepenz.fastadapter.ui.utils.FastAdapterUIUtils
import com.mikepenz.fastadapter.ui.utils.StringHolder
import me.devsaki.hentoid.R

/**
 * Inspired by mikepenz
 */
class SubExpandableItem<T>(
    private val mTouchHelper: ItemTouchHelper,
    val name: String,
    entity: T?
) :
    AbstractExpandableItem<SubExpandableItem.ViewHolder>(),
    IClickable<SubExpandableItem<T>>, ISubItem<SubExpandableItem.ViewHolder>,
    IExtendedDraggable<SubExpandableItem.ViewHolder>,
    INestedItem<SubExpandableItem.ViewHolder> {

    init {
        super.tag = entity
    }

    var header: String? = null
    var description: StringHolder? = null
    private var draggable: Boolean = false

    private var mOnClickListener: ClickListener<SubExpandableItem<T>>? = null

    //we define a clickListener in here so we can directly animate
    /**
     * we overwrite the item specific click listener so we can automatically animate within the item
     *
     * @return
     */
    @Suppress("SetterBackingFieldAssignment")
    override var onItemClickListener: ClickListener<SubExpandableItem<T>>? =
        { v: View?, adapter: IAdapter<SubExpandableItem<T>>, item: SubExpandableItem<T>, position: Int ->
            if (item.subItems.isNotEmpty()) {
                v?.findViewById<View>(R.id.expand_handle)?.let {
                    if (!item.isExpanded) {
                        ViewCompat.animate(it).rotation(180f).start()
                    } else {
                        ViewCompat.animate(it).rotation(0f).start()
                    }
                }
            }

            mOnClickListener?.invoke(v, adapter, item, position) ?: true
        }
        set(onClickListener) {
            this.mOnClickListener = onClickListener // on purpose
        }

    override var onPreItemClickListener: ClickListener<SubExpandableItem<T>>?
        get() = null
        set(_) {}

    //this might not be true for your application
    override var isSelectable: Boolean
        get() = subItems.isEmpty()
        set(value) {
            super.isSelectable = value
        }

    /**
     * defines the type defining this item. must be unique. preferably an id
     *
     * @return the type
     */
    override val type: Int
        get() = R.id.expandable_item

    /**
     * defines the layout which will be used for this item in the list
     *
     * @return the layout for this item
     */
    override val layoutRes: Int
        get() = R.layout.item_expandable

    fun withHeader(header: String): SubExpandableItem<T> {
        this.header = header
        return this
    }

    fun withDescription(description: String): SubExpandableItem<T> {
        this.description = StringHolder(description)
        return this
    }

    fun withDescription(@StringRes descriptionRes: Int): SubExpandableItem<T> {
        this.description = StringHolder(descriptionRes)
        return this
    }

    fun withDraggable(value: Boolean): SubExpandableItem<T> {
        this.draggable = value
        return this
    }

    /**
     * binds the data of this item onto the viewHolder
     *
     * @param holder the viewHolder of this item
     */
    override fun bindView(holder: ViewHolder, payloads: List<Any>) {
        super.bindView(holder, payloads)

        //get the context
        val ctx = holder.itemView.context

        //set the background for the item
        holder.view.clearAnimation()
        ViewCompat.setBackground(
            holder.view,
            FastAdapterUIUtils.getSelectableBackground(ctx, Color.RED, true)
        )
        //set the text for the name
        holder.name.text = name
        //set the text for the description or hide
        StringHolder.applyToOrHide(description, holder.description)

        holder.dragHandle.visibility = if (draggable) View.VISIBLE else View.GONE

        if (subItems.isEmpty()) {
            holder.icon.visibility = View.GONE
        } else {
            holder.icon.visibility = View.VISIBLE
        }

        if (isExpanded) {
            holder.icon.rotation = 0f
        } else {
            holder.icon.rotation = 180f
        }
    }

    override fun unbindView(holder: ViewHolder) {
        super.unbindView(holder)
        holder.name.text = null
        holder.description.text = null
        //make sure all animations are stopped
        holder.icon.clearAnimation()
    }

    override fun getViewHolder(v: View): ViewHolder {
        return ViewHolder(v)
    }

    /**
     * our ViewHolder
     */
    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        var name: TextView = view.findViewById(R.id.material_drawer_name)
        var description: TextView = view.findViewById(R.id.material_drawer_description)
        var icon: ImageView = view.findViewById(R.id.expand_handle)
        var dragHandle: ImageView = view.findViewById(R.id.ivReorder)
    }

    override val isDraggable: Boolean
        get() = draggable
    override val touchHelper: ItemTouchHelper
        get() = mTouchHelper

    override fun getDragView(viewHolder: ViewHolder): View {
        return viewHolder.dragHandle
    }

    class DragHandlerTouchEvent<T>(val action: (position: Int) -> Unit) :
        TouchEventHook<SubExpandableItem<T>>() {
        override fun onBind(viewHolder: RecyclerView.ViewHolder): View? {
            return if (viewHolder is ViewHolder) viewHolder.dragHandle else null
        }

        override fun onTouch(
            v: View,
            event: MotionEvent,
            position: Int,
            fastAdapter: FastAdapter<SubExpandableItem<T>>,
            item: SubExpandableItem<T>
        ): Boolean {
            return if (event.action == MotionEvent.ACTION_DOWN) {
                action(position)
                true
            } else {
                false
            }
        }
    }

    override fun getLevel(): Int {
        return 0
    }
}
