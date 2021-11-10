package com.github.qianmi.domain.project.link

import com.github.qianmi.domain.project.MyProject

class Gavin(
    /**
     * 是否支持
     */
    var isSupport: Boolean,

    /**
     * 地址
     */
    var url: String,
) {
    companion object {
        fun defaultGavin(): Gavin {
            return Gavin(true, MyProject.env.gavin.url)
        }
    }
}