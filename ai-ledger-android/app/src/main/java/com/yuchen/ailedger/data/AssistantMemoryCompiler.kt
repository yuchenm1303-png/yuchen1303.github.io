package com.yuchen.ailedger.data

import java.time.Instant
import kotlin.math.ln
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

private const val PERSONA_INSTRUCTION_BUDGET = 760
private const val PROFILE_SUMMARY_BUDGET = 580
private const val MEMORY_PREFERENCE_LIMIT = 10
private const val MEMORY_RELEVANT_LIMIT = 12

enum class AssistantMemoryIntent(val id: String, val scope: String) {
    ENGLISH_VOCABULARY("english_vocabulary", "english"),
    ENGLISH_LEARNING("english_learning", "english"),
    ANDROID_DEVELOPMENT("android_development", "android"),
    PROGRAMMING("programming", "coding"),
    MATHEMATICS("mathematics", "math"),
    ACADEMIC_WRITING("academic_writing", "writing"),
    FINANCE("finance", "finance"),
    TRAVEL("travel", "travel"),
    GENERAL("general", "general"),
}

data class AssistantMemorySource(
    val id: String,
    val category: String,
    val scope: String,
    val role: String,
    val score: Int,
    val reason: String,
)

data class AssistantMemoryCompilation(
    val schema: String = "ai_ledger_memory_context_v3",
    val intent: AssistantMemoryIntent,
    val activeScopes: Set<String>,
    val personaInstructions: String?,
    val memorySnapshot: JSONObject?,
    val selectedMemoryIds: List<String>,
    val sources: List<AssistantMemorySource>,
    val suppressedConflictCount: Int,
) {
    val hasAnyContext: Boolean
        get() = !personaInstructions.isNullOrBlank() || memorySnapshot != null

    fun personaConfigJson(): JSONObject? {
        val instructions = personaInstructions?.trim().orEmpty()
        if (instructions.isBlank()) return null
        return JSONObject()
            .put("customInstructions", instructions)
            .put("memoryInstructionSchema", schema)
            .put("memoryIntent", intent.id)
    }

    fun diagnosticsJson(): JSONObject = JSONObject()
        .put("schema", schema)
        .put("intent", intent.id)
        .put("activeScopes", JSONArray(activeScopes.toList()))
        .put("selectedMemoryIds", JSONArray(selectedMemoryIds))
        .put("suppressedConflictCount", suppressedConflictCount)
        .put("sources", JSONArray().apply {
            sources.forEach { source ->
                put(
                    JSONObject()
                        .put("id", source.id)
                        .put("category", source.category)
                        .put("scope", source.scope)
                        .put("role", source.role)
                        .put("score", source.score)
                        .put("reason", source.reason)
                )
            }
        })
}

data class AssistantMemoryRuntimeState(
    val compilation: AssistantMemoryCompilation? = null,
    val updatedAtMillis: Long = 0L,
)

object AssistantMemoryRuntime {
    private val mutableState = MutableStateFlow(AssistantMemoryRuntimeState())
    val state: StateFlow<AssistantMemoryRuntimeState> = mutableState.asStateFlow()

    fun record(compilation: AssistantMemoryCompilation) {
        mutableState.value = AssistantMemoryRuntimeState(
            compilation = compilation,
            updatedAtMillis = System.currentTimeMillis(),
        )
    }
}

object AssistantMemoryCompiler {
    fun compile(
        userText: String,
        customInstructions: String?,
        memoryState: AssistantMemoryState,
        nowMillis: Long = System.currentTimeMillis(),
    ): AssistantMemoryCompilation {
        val prompt = userText.trim()
        val intent = classifyIntent(prompt)
        val activeScopes = linkedSetOf("global", intent.scope, "general")
        val promptTokens = tokenize(prompt)

        val ranked = if (memoryState.memoryEnabled && memoryState.cloudReady) {
            memoryState.memories.asSequence()
                .filter { it.isActiveAt(nowMillis) }
                .map { rank(it, intent, activeScopes, promptTokens, nowMillis) }
                .filter { it.score > Int.MIN_VALUE / 2 }
                .sortedWith(
                    compareByDescending<RankedMemory> { it.score }
                        .thenByDescending { it.item.updatedAt }
                )
                .toList()
        } else {
            emptyList()
        }

        val conflictResolution = suppressConflicts(ranked)
        val selected = conflictResolution.selected
        val customSegments = selectCustomInstructionSegments(customInstructions, intent, promptTokens)
        val hasEnglishPreference = hasEnglishLearningPreference(customInstructions, selected)

        val activeRules = selected
            .filter { rankedItem ->
                isInstructionLike(rankedItem.item) &&
                    instructionScopeMatches(rankedItem.scope, activeScopes)
            }
            .take(6)

        val personaInstructions = buildPersonaInstructions(
            customSegments = customSegments,
            activeRules = activeRules,
            intent = intent,
            hasEnglishLearningPreference = hasEnglishPreference,
        )

        val activeRuleIds = activeRules.mapTo(hashSetOf()) { it.item.id }
        val profiles = selected
            .filter { it.item.category == "profile" && it.item.id !in activeRuleIds }
            .take(6)
        val preferences = selected
            .filter {
                it.item.id !in activeRuleIds &&
                    it.item.category == "preference" &&
                    it.score >= preferenceThreshold(intent)
            }
            .take(MEMORY_PREFERENCE_LIMIT)
        val relevant = selected
            .filter {
                it.item.id !in activeRuleIds &&
                    it.item.category !in setOf("profile", "preference") &&
                    it.score >= relevantThreshold(intent)
            }
            .take(MEMORY_RELEVANT_LIMIT)

        val memorySnapshot = buildMemorySnapshot(intent, activeScopes, profiles, preferences, relevant)
        val sources = buildList {
            activeRules.forEach { add(it.toSource("instruction")) }
            profiles.forEach { add(it.toSource("profile")) }
            preferences.forEach { add(it.toSource("preference")) }
            relevant.forEach { add(it.toSource("memory")) }
        }.distinctBy { it.id }

        return AssistantMemoryCompilation(
            intent = intent,
            activeScopes = activeScopes,
            personaInstructions = personaInstructions,
            memorySnapshot = memorySnapshot,
            selectedMemoryIds = sources.map { it.id }.distinct(),
            sources = sources,
            suppressedConflictCount = conflictResolution.suppressedCount,
        )
    }

    internal fun classifyIntent(text: String): AssistantMemoryIntent {
        val clean = text.trim().lowercase()
        if (clean.isBlank()) return AssistantMemoryIntent.GENERAL

        val hasAndroidSignal = ANDROID_SIGNAL.any(clean::contains)
        val hasProgrammingSignal = PROGRAMMING_SIGNAL.any(clean::contains)
        val hasProgrammingContext = PROGRAMMING_CONTEXT_SIGNAL.any(clean::contains)
        val hasStrongMathSignal = MATH_STRONG_SIGNAL.any(clean::contains) ||
            FORMULA_SIGNAL.containsMatchIn(clean) ||
            (clean.contains("函数") && !hasProgrammingContext)
        val hasLatinWord = LATIN_WORD_REGEX.containsMatchIn(clean)
        val vocabularyQuestion = hasLatinWord &&
            !hasAndroidSignal &&
            !hasProgrammingSignal &&
            !hasStrongMathSignal &&
            (
                ENGLISH_VOCABULARY_SIGNAL.any(clean::contains) ||
                    clean.matches(Regex("^[a-z][a-z' -]{1,50}[？?]?$", RegexOption.IGNORE_CASE))
                )

        return when {
            hasAndroidSignal -> AssistantMemoryIntent.ANDROID_DEVELOPMENT
            hasStrongMathSignal -> AssistantMemoryIntent.MATHEMATICS
            vocabularyQuestion -> AssistantMemoryIntent.ENGLISH_VOCABULARY
            ENGLISH_LEARNING_SIGNAL.any(clean::contains) -> AssistantMemoryIntent.ENGLISH_LEARNING
            hasProgrammingSignal -> AssistantMemoryIntent.PROGRAMMING
            WRITING_SIGNAL.any(clean::contains) -> AssistantMemoryIntent.ACADEMIC_WRITING
            FINANCE_SIGNAL.any(clean::contains) -> AssistantMemoryIntent.FINANCE
            TRAVEL_SIGNAL.any(clean::contains) -> AssistantMemoryIntent.TRAVEL
            else -> AssistantMemoryIntent.GENERAL
        }
    }

    private fun rank(
        item: AssistantMemoryItem,
        intent: AssistantMemoryIntent,
        activeScopes: Set<String>,
        promptTokens: Set<String>,
        nowMillis: Long,
    ): RankedMemory {
        val scope = effectiveScope(item)
        val itemTokens = tokenize(item.content)
        val overlap = promptTokens.intersect(itemTokens).size
        val ruleLike = isInstructionLike(item)
        val explicitScopeMismatch = scope != "auto" && scope != "global" && scope !in activeScopes
        val autoRelevant = scope != "auto" ||
            overlap > 0 ||
            (intent == AssistantMemoryIntent.GENERAL && (item.pinned || item.priority >= 2))

        if (explicitScopeMismatch || !autoRelevant) {
            return RankedMemory(
                item = item,
                scope = scope,
                score = Int.MIN_VALUE,
                reason = if (explicitScopeMismatch) "scope_mismatch" else "auto_without_relevance",
            )
        }

        val exactScopeMatch = scope in activeScopes
        val scopeMatches = scope == "global" || exactScopeMatch
        var score = item.priority * 13
        if (item.pinned) score += 20
        score += (item.confidence.coerceIn(0.0, 1.0) * 5.0).toInt()
        score += minOf(24, overlap * 6)
        score += recencyBonus(item.updatedAt, nowMillis)
        score += minOf(5, ln(item.useCount.toDouble() + 1.0).toInt())

        score += when {
            exactScopeMatch -> 28
            scope == "global" -> 14
            scope == "auto" && overlap > 0 -> 10
            scope == "auto" -> 3
            ruleLike -> -30
            item.category == "project" -> -14
            else -> -5
        }
        score += when (item.category) {
            "rule", "skill" -> if (scopeMatches) 18 else -8
            "profile" -> 8
            "preference" -> 7
            "project" -> if (intent in setOf(
                    AssistantMemoryIntent.ANDROID_DEVELOPMENT,
                    AssistantMemoryIntent.PROGRAMMING,
                )) 10 else 0
            else -> 2
        }
        if (ruleLike && scopeMatches) score += 12
        if (intent == AssistantMemoryIntent.ENGLISH_VOCABULARY && containsEnglishLearningSignal(item.content)) score += 36
        if (intent == AssistantMemoryIntent.ANDROID_DEVELOPMENT && containsAndroidSignal(item.content)) score += 24

        return RankedMemory(
            item = item,
            scope = scope,
            score = score,
            reason = buildString {
                append(
                    when {
                        exactScopeMatch -> "scope_match"
                        scope == "global" -> "global"
                        scope == "auto" -> "auto_relevant"
                        else -> "scope_fallback"
                    }
                )
                if (overlap > 0) append("+token_$overlap")
                if (item.pinned) append("+pinned")
                if (ruleLike) append("+instruction")
            },
        )
    }

    private fun suppressConflicts(ranked: List<RankedMemory>): ConflictResolution {
        val selected = mutableListOf<RankedMemory>()
        val occupiedSlots = hashSetOf<String>()
        val supersededIds = ranked.asSequence()
            .map { it.item.supersedesId.trim() }
            .filter(String::isNotBlank)
            .toHashSet()
        var suppressed = 0

        ranked.forEach { candidate ->
            if (candidate.item.id in supersededIds) {
                suppressed += 1
                return@forEach
            }

            val slots = buildList {
                add("content:${canonicalText(candidate.item.content)}")
                semanticSlot(candidate.item)?.let(::add)
                candidate.item.supersedesId.trim()
                    .takeIf(String::isNotBlank)
                    ?.let { add("supersedes:$it") }
            }
            if (slots.any(occupiedSlots::contains)) {
                suppressed += 1
            } else {
                occupiedSlots += slots
                selected += candidate
            }
        }
        return ConflictResolution(selected, suppressed)
    }

    private fun buildPersonaInstructions(
        customSegments: List<String>,
        activeRules: List<RankedMemory>,
        intent: AssistantMemoryIntent,
        hasEnglishLearningPreference: Boolean,
    ): String? {
        val candidates = mutableListOf<String>()
        if (intent == AssistantMemoryIntent.ENGLISH_VOCABULARY && hasEnglishLearningPreference) {
            candidates += ENGLISH_VOCABULARY_SKILL
        } else if (intent == AssistantMemoryIntent.ENGLISH_LEARNING && hasEnglishLearningPreference) {
            candidates += ENGLISH_LEARNING_SKILL
        }
        candidates += customSegments
        candidates += activeRules.map { normalizeInstructionText(it.item.content) }

        val unique = candidates
            .map { it.trim().trimStart('-', '•', ' ') }
            .filter(String::isNotBlank)
            .distinctBy(::canonicalText)
        if (unique.isEmpty()) return null

        val builder = StringBuilder(
            "当前问题命中了以下用户明确要求。只要不与系统安全、事实准确性或工具协议冲突，就必须执行："
        )
        unique.forEach { instruction ->
            val line = "\n- $instruction"
            if (builder.length + line.length <= PERSONA_INSTRUCTION_BUDGET) builder.append(line)
        }
        return builder.toString().take(PERSONA_INSTRUCTION_BUDGET)
    }

    private fun selectCustomInstructionSegments(
        customInstructions: String?,
        intent: AssistantMemoryIntent,
        promptTokens: Set<String>,
    ): List<String> {
        val source = customInstructions.orEmpty().trim()
        if (source.isBlank()) return emptyList()
        return source.split(Regex("\n+|(?<=[。！？；;])"))
            .map(String::trim)
            .filter(String::isNotBlank)
            .map { segment ->
                val overlap = tokenize(segment).intersect(promptTokens).size
                val scope = inferScope(segment)
                var score = overlap * 8
                if (scope == "global" || scope == intent.scope || scope == "auto") score += 16
                if (looksInstructional(segment)) score += 12
                if (intent == AssistantMemoryIntent.ENGLISH_VOCABULARY && containsEnglishLearningSignal(segment)) score += 40
                segment to score
            }
            .filter { it.second >= 16 }
            .sortedByDescending { it.second }
            .take(6)
            .map { it.first }
    }

    private fun buildMemorySnapshot(
        intent: AssistantMemoryIntent,
        activeScopes: Set<String>,
        profiles: List<RankedMemory>,
        preferences: List<RankedMemory>,
        relevant: List<RankedMemory>,
    ): JSONObject? {
        if (profiles.isEmpty() && preferences.isEmpty() && relevant.isEmpty()) return null
        val profileSummary = buildString {
            profiles.forEach { ranked ->
                val next = ranked.item.content.trim()
                val separator = if (isEmpty()) "" else "；"
                if (length + separator.length + next.length <= PROFILE_SUMMARY_BUDGET) {
                    append(separator).append(next)
                }
            }
        }
        return JSONObject()
            .put("schema", "ai_ledger_memory_snapshot_v3")
            .put("intent", intent.id)
            .put("activeScopes", JSONArray(activeScopes.toList()))
            .put("profileSummary", profileSummary)
            .put("preferences", JSONArray(preferences.map { it.item.content.take(180) }))
            .put("relevantMemories", JSONArray(relevant.map { it.item.content.take(220) }))
            .put("sessionSummary", "")
    }

    private fun hasEnglishLearningPreference(
        customInstructions: String?,
        selected: List<RankedMemory>,
    ): Boolean {
        return containsEnglishLearningSignal(customInstructions.orEmpty()) ||
            selected.any { containsEnglishLearningSignal(it.item.content) }
    }

    private fun instructionScopeMatches(scope: String, activeScopes: Set<String>): Boolean {
        return scope == "global" || scope == "auto" || scope in activeScopes
    }

    private fun isInstructionLike(item: AssistantMemoryItem): Boolean {
        return item.category in setOf("rule", "skill") || looksInstructional(item.content)
    }

    private fun looksInstructional(text: String): Boolean {
        val clean = text.lowercase()
        return INSTRUCTION_SIGNAL.any(clean::contains)
    }

    private fun normalizeInstructionText(text: String): String =
        text.trim().replace(Regex("\\s+"), " ")

    private fun effectiveScope(item: AssistantMemoryItem): String {
        val explicit = normalizeMemoryScope(item.scope)
        if (explicit != "auto") return explicit
        val inferred = inferScope(item.content)
        return when {
            inferred != "auto" -> inferred
            isInstructionLike(item) -> "global"
            else -> "auto"
        }
    }

    internal fun inferScope(text: String): String {
        val clean = text.lowercase()
        return when {
            containsEnglishLearningSignal(clean) -> "english"
            containsAndroidSignal(clean) -> "android"
            PROGRAMMING_SIGNAL.any(clean::contains) -> "coding"
            MATH_SIGNAL.any(clean::contains) -> "math"
            WRITING_SIGNAL.any(clean::contains) -> "writing"
            FINANCE_SIGNAL.any(clean::contains) -> "finance"
            TRAVEL_SIGNAL.any(clean::contains) -> "travel"
            RESPONSE_STYLE_SIGNAL.any(clean::contains) -> "global"
            looksInstructional(clean) -> "global"
            else -> "auto"
        }
    }

    private fun containsEnglishLearningSignal(text: String): Boolean {
        val clean = text.lowercase()
        return ENGLISH_LEARNING_MEMORY_SIGNAL.any(clean::contains)
    }

    private fun containsAndroidSignal(text: String): Boolean {
        val clean = text.lowercase()
        return ANDROID_SIGNAL.any(clean::contains)
    }

    private fun semanticSlot(item: AssistantMemoryItem): String? {
        val clean = canonicalText(item.content)
        return when {
            NAME_SLOT_SIGNAL.any(clean::contains) -> "profile:name"
            MAJOR_SLOT_SIGNAL.any(clean::contains) -> "profile:major"
            GRADE_SLOT_SIGNAL.any(clean::contains) -> "profile:grade"
            containsEnglishLearningSignal(clean) && isInstructionLike(item) -> "instruction:english_learning"
            RESPONSE_STYLE_SIGNAL.any(clean::contains) -> "preference:response_style"
            containsAndroidSignal(clean) && item.category == "project" -> "project:android"
            else -> null
        }
    }

    private fun tokenize(text: String): Set<String> {
        val clean = text.lowercase()
        val tokens = linkedSetOf<String>()
        LATIN_WORD_REGEX.findAll(clean).forEach { match ->
            val token = match.value.trim('\'')
            if (token.length >= 2 && token !in TOKEN_STOP_WORDS) tokens += token
        }
        CHINESE_RUN_REGEX.findAll(clean).map { it.value }.forEach { run ->
            if (run.length <= 4 && run !in TOKEN_STOP_WORDS) tokens += run
            listOf(2, 3).forEach { size ->
                if (run.length >= size) {
                    for (index in 0..run.length - size) {
                        val token = run.substring(index, index + size)
                        if (token !in TOKEN_STOP_WORDS) tokens += token
                    }
                }
            }
        }
        return tokens
    }

    private fun recencyBonus(updatedAt: String, nowMillis: Long): Int {
        val updatedMillis = parseIsoInstantMillis(updatedAt) ?: return 0
        val ageDays = ((nowMillis - updatedMillis).coerceAtLeast(0L) / 86_400_000L).toInt()
        return when {
            ageDays <= 7 -> 5
            ageDays <= 30 -> 3
            ageDays <= 180 -> 1
            else -> 0
        }
    }

    private fun preferenceThreshold(intent: AssistantMemoryIntent): Int =
        if (intent == AssistantMemoryIntent.GENERAL) 16 else 20

    private fun relevantThreshold(intent: AssistantMemoryIntent): Int =
        if (intent == AssistantMemoryIntent.GENERAL) 24 else 18

    private fun canonicalText(value: String): String =
        value.lowercase().replace(Regex("[\\s，。！？、；：,.!?;:'\"()（）【】\\[\\]_-]+"), "")

    private fun RankedMemory.toSource(role: String): AssistantMemorySource = AssistantMemorySource(
        id = item.id,
        category = item.category,
        scope = scope,
        role = role,
        score = score,
        reason = reason,
    )

    private data class RankedMemory(
        val item: AssistantMemoryItem,
        val scope: String,
        val score: Int,
        val reason: String,
    )

    private data class ConflictResolution(
        val selected: List<RankedMemory>,
        val suppressedCount: Int,
    )

    private val LATIN_WORD_REGEX = Regex("[a-zA-Z][a-zA-Z'-]{1,48}")
    private val CHINESE_RUN_REGEX = Regex("[\\u4e00-\\u9fff]{2,}")
    private val FORMULA_SIGNAL = Regex("[=+*/^√∫∑]|\\b(sin|cos|tan|log|lim)\\b")

    private val ENGLISH_VOCABULARY_SIGNAL = listOf(
        "什么意思", "怎么用", "例句", "翻译", "中文", "读音", "发音", "单词", "短语", "meaning", "example",
    )
    private val ENGLISH_LEARNING_SIGNAL = listOf(
        "学英语", "学习英语", "英语学习", "语法", "四六级", "四级", "六级", "雅思", "托福", "英文写作",
    )
    private val ENGLISH_LEARNING_MEMORY_SIGNAL = listOf(
        "英语", "英文", "单词", "词汇", "例句", "语法", "english", "vocabulary", "phrase", "pronunciation",
    )
    private val ANDROID_SIGNAL = listOf(
        "android", "compose", "kotlin", "apk", "gradle", "viewmodel", "无障碍", "opengl", "github actions",
    )
    private val PROGRAMMING_SIGNAL = listOf(
        "代码", "编程", "程序", "函数", "类", "bug", "报错", "python", "java", "javascript", "typescript", "api",
    )
    private val PROGRAMMING_CONTEXT_SIGNAL = listOf(
        "代码", "编程", "程序", "怎么写", "实现", "调用", "参数", "返回值", "方法", "类", "bug", "报错",
        "python", "kotlin", "java", "javascript", "typescript", "api", "compose", "android",
    )
    private val MATH_STRONG_SIGNAL = listOf(
        "数学", "极限", "导数", "积分", "微分", "矩阵", "概率", "证明", "方程", "几何", "偏导", "拐点",
        "定义域", "值域", "单调", "奇函数", "偶函数", "反常积分",
    )
    private val MATH_SIGNAL = MATH_STRONG_SIGNAL + "函数"
    private val WRITING_SIGNAL = listOf(
        "论文", "报告", "总结", "润色", "改写", "提纲", "写作", "摘要", "开题", "文献综述",
    )
    private val FINANCE_SIGNAL = listOf(
        "金融", "股票", "投资", "收益率", "现金流", "估值", "财务", "债券", "期权", "portfolio",
    )
    private val TRAVEL_SIGNAL = listOf(
        "旅行", "旅游", "酒店", "机票", "行程", "景点", "自驾", "路线", "签证",
    )
    private val INSTRUCTION_SIGNAL = listOf(
        "必须", "务必", "始终", "以后", "每次", "都要", "不要", "禁止", "应当", "需要你", "请你", "尽量", "时常", "可以适当",
    )
    private val NAME_SLOT_SIGNAL = listOf("名字是", "我叫", "称呼我", "用户名字")
    private val MAJOR_SLOT_SIGNAL = listOf("专业是", "专业为", "电气工程", "计算机专业", "金融专业")
    private val GRADE_SLOT_SIGNAL = listOf("大一", "大二", "大三", "大四", "研一", "研二", "研三", "博士", "年级")
    private val RESPONSE_STYLE_SIGNAL = listOf("简洁", "详细", "少分点", "多分点", "回答风格", "表达自然", "通俗")
    private val TOKEN_STOP_WORDS = setOf(
        "什么", "怎么", "这个", "那个", "可以", "需要", "用户", "以后", "回答", "问题", "please", "the", "and", "for", "with",
    )

    private const val ENGLISH_VOCABULARY_SKILL =
        "当前是英语词汇或短语学习问题。必须给出通俗中文释义、最常见使用语境、至少2个自然英文例句及中文翻译，并补充常见搭配或易混淆点；最后给一句简短记忆提示。不要只给定义。"

    private const val ENGLISH_LEARNING_SKILL =
        "当前是英语学习问题。解释要通俗，并结合自然英文示例和中文翻译；在不打断主题的前提下补充一个真正有用的词汇、搭配或记忆方法。"
}

internal fun parseIsoInstantMillis(value: String): Long? {
    val clean = value.trim()
    if (clean.isBlank()) return null
    return runCatching { Instant.parse(clean).toEpochMilli() }
        .recoverCatching {
            val normalized = clean.replace(" ", "T").let { if (it.endsWith("Z")) it else "${it}Z" }
            Instant.parse(normalized).toEpochMilli()
        }
        .getOrNull()
}

internal fun AssistantMemoryItem.isActiveAt(nowMillis: Long = System.currentTimeMillis()): Boolean {
    if (!enabled || status != "active" || content.isBlank()) return false
    val startsAt = parseIsoInstantMillis(validFrom)
    if (startsAt != null && nowMillis < startsAt) return false
    val expiresAt = parseIsoInstantMillis(validUntil)
    if (expiresAt != null && nowMillis >= expiresAt) return false
    return true
}
