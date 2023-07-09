package com.lagradost.quicknovel.ui

import android.text.Spannable
import android.text.SpannableString
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.text.getSpans
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.lagradost.quicknovel.ChapterStartSpanned
import com.lagradost.quicknovel.FailedSpanned
import com.lagradost.quicknovel.LoadingSpanned
import com.lagradost.quicknovel.ReadActivityViewModel
import com.lagradost.quicknovel.SpanDisplay
import com.lagradost.quicknovel.TTSHelper
import com.lagradost.quicknovel.TextSpan
import com.lagradost.quicknovel.databinding.SingleFailedBinding
import com.lagradost.quicknovel.databinding.SingleFinishedChapterBinding
import com.lagradost.quicknovel.databinding.SingleImageBinding
import com.lagradost.quicknovel.databinding.SingleLoadingBinding
import com.lagradost.quicknovel.databinding.SingleTextBinding
import com.lagradost.quicknovel.util.UIHelper.setImage
import io.noties.markwon.image.AsyncDrawableSpan

const val DRAW_DRAWABLE = 1
const val DRAW_TEXT = 0
const val DRAW_LOADING = 2
const val DRAW_FAILED = 3
const val DRAW_CHAPTER = 4


data class ScrollVisibilityItem(
    val adapterPosition: Int,
    val viewHolder: RecyclerView.ViewHolder?,
)

/*
data class ScrollVisibility(
    val firstVisible: ScrollVisibilityItem,
    val firstFullyVisible: ScrollVisibilityItem,
    val lastVisible: ScrollVisibilityItem,
    val lastFullyVisible: ScrollVisibilityItem,

    val screenTop: Int,
    val screenBottom: Int,
    val screenTopBar: Int,
)*/

/** a scroll index specifies exactly where you are inside a book SHOULD NOT BE SAVED
 * as ScrollIndex innerIndex should be derived from char
 *
 * index = chapter index
 * innerIndex = what text block, innerIndex *can* be derived from char
 * char = what character in index, not local
 */
data class ScrollIndex(
    val index: Int,
    val innerIndex: Int,
    val char: Int,
)

data class ScrollVisibilityIndex(
    // first in the recyclerview
    val firstInMemory: TextVisualLine,
    // last in the recyclerview
    val lastInMemory: TextVisualLine,

    // first line you can clearly see
    val firstFullyVisible: TextVisualLine?,
    // first line you can't clearly see
    val lastHalfVisible: TextVisualLine?,

    // first line after the bottom bar you can see clearly
    val firstFullyVisibleUnderLine: TextVisualLine?
)

/** this represents a single text line split by the layout, NOT newlines,
 * SHOULD not be stored in any way as layout change or scroll with invalidate the values */
data class TextVisualLine(
    // chars are in relation to index, not span
    val startChar: Int,
    val endChar: Int,

    val index: Int,
    val innerIndex: Int,

    val top: Int,
    val bottom: Int,
)

fun TextVisualLine.toScroll() : ScrollIndex {
    return ScrollIndex(index = this.index, innerIndex = this.innerIndex, char = this.startChar)
}

fun removeHighLightedText(tv: TextView) {
    val wordToSpan: Spannable = SpannableString(tv.text)

    val spans = wordToSpan.getSpans<android.text.Annotation>(0, tv.text.length)
    for (s in spans) {
        if (s.value == "rounded")
            wordToSpan.removeSpan(s)
    }
    tv.setText(wordToSpan, TextView.BufferType.SPANNABLE)
}

fun setHighLightedText(tv: TextView, start: Int, end: Int) {
    try {
        val wordToSpan: Spannable = SpannableString(tv.text)
        val length = tv.text.length
        val spans = wordToSpan.getSpans<android.text.Annotation>(0, length)
        for (s in spans) {
            if (s.value == "rounded")
                wordToSpan.removeSpan(s)
        }

        wordToSpan.setSpan(
            android.text.Annotation("", "rounded"),
            minOf(maxOf(start, 0), length),
            minOf(maxOf(end, 0), length),
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        tv.setText(wordToSpan, TextView.BufferType.SPANNABLE)
        return
    } catch (t: Throwable) {
        return
    }
}

class TextAdapter(private val viewModel: ReadActivityViewModel) :
    ListAdapter<SpanDisplay, TextAdapter.TextAdapterHolder>(DiffCallback()) {
    var currentTTSLine: TTSHelper.TTSLine? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TextAdapterHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding: ViewBinding = when (viewType) {
            DRAW_TEXT -> SingleTextBinding.inflate(inflater, parent, false)
            DRAW_DRAWABLE -> SingleImageBinding.inflate(inflater, parent, false)
            DRAW_LOADING -> SingleLoadingBinding.inflate(inflater, parent, false)
            DRAW_FAILED -> SingleFailedBinding.inflate(inflater, parent, false)
            DRAW_CHAPTER -> SingleFinishedChapterBinding.inflate(inflater, parent, false)
            else -> throw NotImplementedError()
        }

        return TextAdapterHolder(binding, viewModel)
    }

    /** updates new onbind calls, but not current */
    fun updateTTSLine(line: TTSHelper.TTSLine?) {
        currentTTSLine = line
    }

    /*fun getViewOffset(scrollVisibility: ScrollVisibilityItem, char: Int): Int? {
        try {
            if (scrollVisibility.adapterPosition < 0 || scrollVisibility.adapterPosition >= itemCount) return null
            //val item = getItem(scrollVisibility.index)
            val viewHolder = scrollVisibility.viewHolder
            if (viewHolder !is TextAdapterHolder) return null
            val binding = viewHolder.binding
            if (binding !is SingleTextBinding) return null
            val outLocation = IntArray(2)
            binding.root.getLocationInWindow(outLocation)
            binding.root.layout.apply {
                for (i in 0 until lineCount) {
                    if (getLineEnd(i) >= char) {
                        //binding.root.getLocationInWindow(outLocation)
                        //val (_, y) = outLocation

                        return getLineTop(i)
                    }
                }
            }
        } catch (_ : Throwable) { }
        return null
    }*/

    /* private fun transformIndexToScrollIndex(
         scrollVisibility: ScrollVisibilityItem,
         screenTop: Int,
         screenBottom: Int
     ): ScrollIndex? {
         try {
             if (scrollVisibility.adapterPosition < 0 || scrollVisibility.adapterPosition >= itemCount) return null
             val item = getItem(scrollVisibility.adapterPosition)
             val viewHolder = scrollVisibility.viewHolder

             var firstVisibleChar: Int? = null
             var firstInvisibleChar: Int? = null
             if (viewHolder is TextAdapterHolder) {
                 val binding = viewHolder.binding
                 if (binding is SingleTextBinding) {
                     val outLocation = IntArray(2)
                     binding.root.getLocationInWindow(outLocation)
                     val (_, y) = outLocation
                     binding.root.layout.apply {
                         for (i in 0 until lineCount) {
                             val top = y + getLineTop(i)
                             val bottom = y + getLineBottom(i)
                             if (top < screenBottom && top > screenTop && bottom < screenBottom && bottom > screenTop) {
                                 if (firstVisibleChar == null) firstVisibleChar = getLineStart(i)
                                 if (firstInvisibleChar != null) break
                             } else {
                                 if (firstInvisibleChar == null) firstInvisibleChar = getLineStart(i)
                                 if (firstVisibleChar != null) break
                             }
                         }
                     }
                 }
             }

             return ScrollIndex(
                 index = item.index,
                 innerIndex = item.innerIndex,
                 firstVisibleChar = firstVisibleChar,
                 firstInvisibleChar = firstInvisibleChar
             )
         } catch (_ : Throwable) {
             return null
         }
     }*/


    fun getLines(scrollVisibility: ScrollVisibilityItem): List<TextVisualLine> {
        try {
            if (scrollVisibility.adapterPosition < 0 || scrollVisibility.adapterPosition >= itemCount) return emptyList()
            val viewHolder = scrollVisibility.viewHolder
            if (viewHolder !is TextAdapterHolder) return emptyList()
            val binding = viewHolder.binding
            if (binding !is SingleTextBinding) return emptyList()
            val span = viewHolder.span
            if (span !is TextSpan) return emptyList()

            val outLocation = IntArray(2)
            binding.root.getLocationInWindow(outLocation)
            val y = outLocation[1]+binding.root.paddingTop

            //val paddingTop =
            //val paddingBottom = binding.root.paddingBottom


            val list = arrayListOf<TextVisualLine>()
            binding.root.layout.apply {
                for (i in 0 until lineCount) {
                    list.add(
                        TextVisualLine(
                            startChar = span.start + getLineStart(i),
                            endChar = span.start + getLineEnd(i),
                            innerIndex = span.innerIndex,
                            index = span.index,
                            top = getLineTop(i) + y,//+paddingTop,
                            bottom = getLineBottom(i) + y//+paddingBottom
                        )
                    )
                }
            }
            return list
        } catch (t: Throwable) {
            return emptyList()
        }
    }

    /*fun getIndex(data: ScrollVisibility): ScrollVisibilityIndex? {
        return ScrollVisibilityIndex(
            firstVisible = transformIndexToScrollIndex(
                data.firstVisible,
                data.screenTop,
                data.screenBottom
            ) ?: return null,
            firstFullyVisible = transformIndexToScrollIndex(
                data.firstFullyVisible,
                data.screenTop,
                data.screenBottom
            ) ?: return null,
            lastFullyVisible = transformIndexToScrollIndex(
                data.lastFullyVisible,
                data.screenTop,
                data.screenBottom
            ) ?: return null,
            lastVisible = transformIndexToScrollIndex(
                data.lastVisible,
                data.screenTop,
                data.screenBottom
            ) ?: return null,
        )
    }*/

    override fun onBindViewHolder(holder: TextAdapterHolder, position: Int) {
        val currentItem = getItem(position)
        holder.bind(currentItem, currentTTSLine)
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is TextSpan -> {
                if (item.text.getSpans<AsyncDrawableSpan>(0, item.text.length).isNotEmpty()) {
                    DRAW_DRAWABLE
                } else {
                    DRAW_TEXT
                }
            }

            is LoadingSpanned -> {
                DRAW_LOADING
            }

            is FailedSpanned -> {
                DRAW_FAILED
            }

            is ChapterStartSpanned -> {
                DRAW_CHAPTER
            }

            else -> throw NotImplementedError()
        }
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).id
    }

    class TextAdapterHolder(
        val binding: ViewBinding,
        private val viewModel: ReadActivityViewModel
    ) :
        RecyclerView.ViewHolder(binding.root) {

        var span: SpanDisplay? = null

        // returns the range of the highlight in UI, (start to end) to (top to bottom)
        // : Pair<Int, Int>?


        fun updateTTSLine(line: TTSHelper.TTSLine?) {
            if (binding !is SingleTextBinding) return
            val span = span
            if (span !is TextSpan) return
            if (line == null || ((line.startChar < span.start && line.endChar < span.start)
                        || (line.startChar > span.end && line.endChar > span.end) || line.index != span.index)
            ) {
                removeHighLightedText(binding.root)
                return
            }

            val length = binding.root.length()
            val start = minOf(maxOf(line.startChar - span.start, 0), length)
            val end = minOf(maxOf(line.endChar - span.start, 0), length)

            setHighLightedText(
                binding.root,
                start,
                end
            )
            /*try {
                var startTextTop: Int? = null
                var startTextBottom: Int? = null
                if(binding.root.layout == null) return null
                binding.root.layout.apply {
                    if (this == null || lineCount == 0) return null

                    for (i in 0 until lineCount) {
                        if (startTextTop == null && getLineEnd(i) >= start) {
                            startTextTop = getLineTop(i)
                        }

                        if (startTextBottom == null && getLineEnd(i) >= end) {
                            startTextBottom = getLineBottom(i)
                        }
                    }
                    val outLocation = IntArray(2)
                    binding.root.getLocationInWindow(outLocation)
                    val y = outLocation[1]

                    return ((startTextTop ?: return null) + y) to ((startTextBottom
                        ?: getLineBottom(lineCount - 1)) + y)
                }
            } catch (_ : Throwable) {
                return null
            }*/
        }

        private fun bindText(obj: TextSpan) {
            when (binding) {
                is SingleImageBinding -> {
                    val img = obj.text.getSpans<AsyncDrawableSpan>(0, obj.text.length)[0]
                    val url = img.drawable.destination
                    if (binding.root.url == url) return
                    binding.root.url = url // don't reload if already set
                    img.drawable.result?.let { drawable ->
                        binding.root.setImageDrawable(drawable)
                    } ?: kotlin.run {
                        binding.root.setImage(url)
                    }
                }

                is SingleTextBinding -> {
                    binding.root.setOnClickListener {
                        viewModel.switchVisibility()
                    }
                    binding.root.text = obj.text
                }

                else -> throw NotImplementedError()
            }
        }

        private fun bindLoading(obj: LoadingSpanned) {
            if (binding !is SingleLoadingBinding) throw NotImplementedError()
            binding.root.text = obj.url?.let { "Loading $it" } ?: "Loading"
            binding.root.setOnClickListener {
                viewModel.switchVisibility()
            }
        }

        private fun bindFailed(obj: FailedSpanned) {
            if (binding !is SingleFailedBinding) throw NotImplementedError()
            binding.root.text = obj.reason
            binding.root.setOnClickListener {
                viewModel.switchVisibility()
            }
        }

        private fun bindChapter(obj: ChapterStartSpanned) {
            if (binding !is SingleFinishedChapterBinding) throw NotImplementedError()
            binding.root.text = obj.name
            binding.root.setOnClickListener {
                viewModel.switchVisibility()
            }
        }

        fun bind(obj: SpanDisplay, ttsLine: TTSHelper.TTSLine?) {
            span = obj
            when (obj) {
                is TextSpan -> {
                    this.bindText(obj)
                    // because we bind text here we know that it will be cleared and thus
                    // we do not have to update it with null
                    if (ttsLine != null)
                        this.updateTTSLine(ttsLine)
                }

                is LoadingSpanned -> {
                    this.bindLoading(obj)
                }

                is FailedSpanned -> {
                    this.bindFailed(obj)
                }

                is ChapterStartSpanned -> {
                    this.bindChapter(obj)
                }

                else -> throw NotImplementedError()
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<SpanDisplay>() {
        override fun areItemsTheSame(oldItem: SpanDisplay, newItem: SpanDisplay): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: SpanDisplay, newItem: SpanDisplay): Boolean {
            return when (oldItem) {
                is TextSpan -> {
                    if (newItem !is TextSpan) return false
                    // don't check the span content as that does not change
                    return newItem.end == oldItem.end && newItem.start == oldItem.start && newItem.index != oldItem.index
                }

                is LoadingSpanned -> {
                    if (newItem !is LoadingSpanned) return false

                    newItem.id == oldItem.id && newItem.url == oldItem.url
                }

                is FailedSpanned -> {
                    if (newItem !is FailedSpanned) return false

                    newItem.id == oldItem.id && newItem.reason == oldItem.reason
                }

                is ChapterStartSpanned -> {
                    if (newItem !is ChapterStartSpanned) return false

                    newItem.id == oldItem.id && oldItem.name == newItem.name
                }

                else -> throw NotImplementedError()
            }
        }
    }
}