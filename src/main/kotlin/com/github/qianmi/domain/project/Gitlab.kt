package com.github.qianmi.domain.project

private const val formatUrl = "{domain}/{group}/{projectName}.git"

class Gitlab(
    /**
     * 是否支持
     */
    var isSupport: Boolean,
    /**
     * gitLab 地址
     */
    var url: String,
) {

    companion object {
        fun defaultGitlab(): Gitlab {
            return Gitlab(false, "")
        }
    }
}