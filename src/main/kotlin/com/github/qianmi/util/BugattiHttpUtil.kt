package com.github.qianmi.util

import cn.hutool.core.collection.CollectionUtil
import cn.hutool.core.date.DateField
import cn.hutool.core.date.DateUtil
import cn.hutool.core.io.IORuntimeException
import cn.hutool.http.ContentType
import cn.hutool.http.HttpResponse
import cn.hutool.http.HttpUtil
import cn.hutool.http.Method
import com.github.qianmi.domain.enums.EnvEnum
import com.github.qianmi.domain.project.AllProject
import com.github.qianmi.domain.project.link.Bugatti
import com.github.qianmi.domain.project.tools.Shell
import com.github.qianmi.services.request.CiBuildReleaseRequest
import com.github.qianmi.services.request.CiBuildRequest
import com.github.qianmi.services.vo.*
import com.github.qianmi.storage.BugattiCookie
import com.github.qianmi.storage.DomainConfig
import com.github.qianmi.util.JsonUtil.toBean
import com.github.qianmi.util.JsonUtil.toJsonString
import com.github.qianmi.util.JsonUtil.toList
import com.google.gson.JsonObject
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import java.util.*

object BugattiHttpUtil {

    private const val bugattiUrl = Bugatti.domainUrl

    @JvmStatic
    @Nullable
    fun login(project: Project?): HttpResponse? {
        try {
            val httpResponse = login(
                DomainConfig.getInstance().userName,
                DomainConfig.getInstance().passwd
            )
            if (httpResponse.status == 406) {
                NotifyUtil.notifyError(project, "登录Bugatti失败！账号或密码错误~")
            } else {
                BugattiCookie.getInstance().cookie = httpResponse.getCookie("SESSION").toString()
            }
            return httpResponse
        } catch (e: IORuntimeException) {
            NotifyUtil.notifyError(project, "登录Bugatti失败！请检查网络(VPN)是否通畅~")
        }
        return null
    }

    @JvmStatic
    fun isLoginSuccess(httpResponse: HttpResponse?): Boolean {
        return httpResponse != null
                && httpResponse.isOk
    }

    @JvmStatic
    fun login(userName: String, password: String): HttpResponse {
        val body: MutableMap<String, Any> = HashMap(8)
        body["userName"] = userName
        body["password"] = password
        return HttpUtil
            .createPost("$bugattiUrl/login")
            .body(body.toJsonString(), ContentType.JSON.value)
            .timeout(3000)
            .execute()
    }

    @NotNull
    @JvmStatic
    fun getProjectInfo(myProject: AllProject.MyProject): BugattiProjectInfoResult {
        val result: String = HttpUtil
            .createGet("$bugattiUrl/project/${myProject.bugatti.projectCode}/${myProject.bugatti.envCode}")
            .cookie(getCookie())
            .execute()
            .body()
        return result.toBean()!!
    }

    @NotNull
    @JvmStatic
    fun getBranchList(myProject: AllProject.MyProject): List<BugattiProjectBranchResult> {
        val result: String = HttpUtil
            .createGet("$bugattiUrl/ci/branchs?gitUrl=${myProject.gitlab.url}")
            .cookie(getCookie())
            .execute()
            .body()
        return result.toList()
    }

    /**
     * Returns 分支名称，版本信息
     */
    @NotNull
    @JvmStatic
    fun mapLastBetaVersion(myProject: AllProject.MyProject): Map<String, BugattiLastVersionResult> {
        val result: String = HttpUtil
            .createGet("$bugattiUrl/project/${myProject.bugatti.projectCode}/versoin/lastbetaversion")
            .cookie(getCookie())
            .execute()
            .body()
        return result.toList<BugattiLastVersionResult>().associateBy { it.branch }
    }

    @NotNull
    @JvmStatic
    fun getLastReleaseVersion(myProject: AllProject.MyProject): BugattiLastVersionResult? {
        val result: String = HttpUtil
            .createGet("$bugattiUrl/project/${myProject.bugatti.projectCode}/versoin/lastrelaseversion")
            .cookie(getCookie())
            .execute()
            .body()
        val resultList = result.toList<BugattiLastVersionResult>()
        if (CollectionUtil.isNotEmpty(resultList)) {
            return resultList[0]
        }
        return null
    }

    @JvmStatic
    fun getShellElementList(bugattiProjectCode: String, env: EnvEnum): List<Shell.Element> {
        //屏蔽生产环境
        if (EnvEnum.PROD == env) {
            return Collections.emptyList()
        }
        val result = HttpUtil
            .createGet("$bugattiUrl/task/clusters?envId=${env.bugatti.envCode}&projectId=$bugattiProjectCode")
            .cookie(getCookie())
            .execute()
            .body()

        val shellEleList = ArrayList<Shell.Element>()


        for (ele in JsonUtil.parse(result).asJsonObject.getAsJsonArray("host").toList()) {
            val eleObj = ele as JsonObject

            if (Optional.ofNullable(eleObj.get("show")).map { it.asBoolean }.orElse(false)) {
                shellEleList.add(
                    Shell.Element.instanceOf(BugattiShellInfoResult(
                        Optional.ofNullable(eleObj.get("group")).map { it.asString }.orElse(""),
                        Optional.ofNullable(eleObj.get("ip")).map { it.asString }.orElse(""),
                        Optional.ofNullable(eleObj.get("version")).map { it.asString }.orElse(""),
                    )))
            }
        }
        return shellEleList
    }


    @NotNull
    @JvmStatic
    fun jenkinsCIBuild(myProject: AllProject.MyProject, branchName: String): BugattiResult {

        val result: String = HttpUtil
            .createRequest(Method.PUT, "$bugattiUrl/ci/build")
            .body(CiBuildRequest.instanceOf(myProject, branchName).toJsonString())
            .cookie(getCookie())
            .execute()
            .body()
        return if (BugattiResult.SUCCESS == result) {
            BugattiResult.success()
        } else {
            BugattiResult.fail(result)
        }
    }

    @NotNull
    @JvmStatic
    fun jenkinsCIRelease(
        myProject: AllProject.MyProject,
        branchName: String,
        version: String,
        snapshotVersion: String,
    ): BugattiResult {
        val request = CiBuildReleaseRequest.instanceOf(myProject, branchName, version, snapshotVersion)

        val result: String = HttpUtil
            .createRequest(Method.PUT, "$bugattiUrl/ci/release?force=true")
            .body(request.toJsonString())
            .cookie(getCookie())
            .execute()
            .body()
        val releaseResult = result.toBean<BugattiCIReleaseResult>()!!

        return if (BugattiResult.SUCCESS == releaseResult.data) {
            BugattiResult.success()
        } else {
            BugattiResult.fail(releaseResult.data)
        }
    }

    @NotNull
    @JvmStatic
    fun ciBuildResult(myProject: AllProject.MyProject): BugattiCIBuildResult {
        val result: String = HttpUtil
            .createGet("$bugattiUrl/ci/builds?projectId=${myProject.bugatti.projectCode}&page=0&pageSize=1")
            .cookie(getCookie())
            .execute()
            .body()
        val resultList = result.toList<BugattiCIBuildResult>()
        if (CollectionUtil.isNotEmpty(resultList)) {
            return resultList[0]
        }
        return BugattiCIBuildResult()
    }

    @NotNull
    @JvmStatic
    fun ciReleaseResult(myProject: AllProject.MyProject, version: String, branchName: String): BugattiCIBuildResult {
        val result: String = HttpUtil
            .createGet("$bugattiUrl/ci/releases?projectId=${myProject.bugatti.projectCode}&page=0&pageSize=1&version=${version}&user=&tag=${branchName}")
            .cookie(getCookie())
            .execute()
            .body()
        val resultList = result.toList<BugattiCIBuildResult>()
        if (CollectionUtil.isNotEmpty(resultList)) {
            return resultList[0]
        }
        return BugattiCIBuildResult()
    }

    @JvmStatic
    fun clearCookie() {
        BugattiCookie.getInstance().cookie = ""
    }

    private fun getCookie(): String {
        val storage = BugattiCookie.getInstance()
        if (StringUtil.isNotBlank(storage.cookie)) {
            return storage.cookie
        }
        val login = login(null)
        val loginSuccess = isLoginSuccess(login)
        if (loginSuccess) {
            return storage.cookie
        }
        return ""
    }

    fun refreshCookie(project: Project?) {
        login(project)
    }

    init {
        //2小时更新一次 布加迪cookie
        val time = 7200000L
        Timer().schedule(object : TimerTask() {
            override fun run() {
                refreshCookie(null)
            }
        }, DateUtil.offset(Date(), DateField.SECOND, time.toInt()), time)
    }
}