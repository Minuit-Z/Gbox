package com.guet.flexbox.build

import com.facebook.litho.*
import com.facebook.yoga.YogaEdge
import com.facebook.yoga.YogaPositionType
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

internal object FrameFactory : WidgetFactory<Row.Builder>(), ThreadFactory {

    private val count = AtomicInteger()

    private val measureThreadPool = ThreadPoolExecutor(
            0,
            Int.MAX_VALUE,
            30L,
            TimeUnit.SECONDS,
            SynchronousQueue<Runnable>(),
            this
    )

    override fun newThread(r: Runnable): Thread {
        return Thread(r, "Frame:Measure_${count.getAndIncrement()}")
    }

    override fun onCreateWidget(
            c: ComponentContext,
            dataBinding: DataContext,
            attrs: Map<String, String>?,
            visibility: Int
    ): Row.Builder {
        return Row.create(c)
    }

    override fun onInstallChildren(
            owner: Row.Builder,
            c: ComponentContext,
            dataBinding: DataContext,
            attrs: Map<String, String>?,
            children: List<Component>?,
            visibility: Int) {
        if (children.isNullOrEmpty()) {
            return
        }
        var width = if (attrs != null) {
            dataBinding.tryGetValue(attrs["borderWidth"], Int.MIN_VALUE)
        } else {
            Int.MIN_VALUE
        }
        var height = if (attrs != null) {
            dataBinding.tryGetValue(attrs["height"], Int.MIN_VALUE)
        } else {
            Int.MIN_VALUE
        }
        val wrappers = children.map {
            Row.create(c)
                    .positionType(YogaPositionType.ABSOLUTE)
                    .positionPx(YogaEdge.LEFT, 0)
                    .positionPx(YogaEdge.TOP, 0)
                    .child(it)
                    .build()
        }
        wrappers.forEach {
            owner.child(it)
        }
        if (width > 0 && height > 0) {
            return
        } else {
            width = width.toPx()
            height = height.toPx()
            val widthSpec = if (width > 0) {
                SizeSpec.makeSizeSpec(width, SizeSpec.AT_MOST)
            } else {
                SizeSpec.makeSizeSpec(0, SizeSpec.UNSPECIFIED)
            }
            val heightSpec = if (height > 0) {
                SizeSpec.makeSizeSpec(height, SizeSpec.AT_MOST)
            } else {
                SizeSpec.makeSizeSpec(0, SizeSpec.UNSPECIFIED)
            }
            //concurrent measure
            var maxWidth = 0
            var maxHeight = 0
            var futures: List<Future<Size>>? = null
            if (children.size > 1) {
                futures = wrappers.subList(1, children.size).map {
                    measureThreadPool.submit<Size> {
                        val s = Size()
                        it.measure(
                                c,
                                widthSpec,
                                heightSpec,
                                s
                        )
                        s
                    }
                }
            }
            val size = Size()
            val first = wrappers.first()
            first.measure(
                    c,
                    widthSpec,
                    heightSpec,
                    size
            )
            maxWidth = max(maxWidth, size.width)
            maxHeight = max(maxHeight, size.height)
            owner.child(first)
            futures?.map {
                it.get()
            }?.forEach {
                maxWidth = max(maxWidth, it.width)
                maxHeight = max(maxHeight, it.height)
            }
            if (width <= 0) {
                owner.widthPx(maxWidth)
            }
            if (height <= 0) {
                owner.heightPx(maxHeight)
            }
        }
    }
}