package com.yuchen.ailedger.data

import android.content.Context
import com.yuchen.ailedger.model.LearnedVisualSkill
import java.io.File

/** 保存云端生成的 Skill 语义，不保存固定点击路线、节点选择器或坐标脚本。 */
class OperationSkillArtifactStore(context: Context) {
    private val directory = File(context.applicationContext.noBackupFilesDir, DIRECTORY).apply { mkdirs() }

    fun save(skill: LearnedVisualSkill) {
        val target = fileFor(skill.workflowId)
        val temporary = File(directory, "${target.name}.tmp")
        temporary.writeText(OperationSkillJsonCodec.encode(skill), Charsets.UTF_8)
        if (!temporary.renameTo(target)) {
            target.writeText(temporary.readText(Charsets.UTF_8), Charsets.UTF_8)
            temporary.delete()
        }
    }

    fun load(workflowId: String): LearnedVisualSkill? {
        val file = fileFor(workflowId)
        if (!file.isFile) return null
        return runCatching { OperationSkillJsonCodec.decode(file.readText(Charsets.UTF_8)) }.getOrNull()
    }

    fun delete(workflowId: String) {
        runCatching { fileFor(workflowId).delete() }
    }

    private fun fileFor(workflowId: String): File {
        val safeId = workflowId.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)
        require(safeId.isNotBlank()) { "invalid workflow id" }
        return File(directory, "$safeId.json")
    }

    companion object {
        private const val DIRECTORY = "operation-skills"
    }
}
