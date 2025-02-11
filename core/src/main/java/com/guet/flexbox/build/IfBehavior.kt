package com.guet.flexbox.build

import com.facebook.litho.Component
import com.facebook.litho.ComponentContext
import com.guet.flexbox.NodeInfo

internal object IfBehavior : Behavior() {
    override fun onApply(
            c: ComponentContext,
            dataBinding: DataContext,
            attrs: Map<String, String>,
            children: List<NodeInfo>,
            upperVisibility: Int
    ): List<Component> {
        return if (dataBinding.requestValue("test", attrs)) {
            return children.map {
                c.createFromElement(dataBinding, it, upperVisibility)
            }.flatten()
        } else {
            emptyList()
        }
    }
}