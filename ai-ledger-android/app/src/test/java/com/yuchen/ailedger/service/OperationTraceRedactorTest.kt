package com.yuchen.ailedger.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationTraceRedactorTest {
    @Test
    fun visibleTextRedactsPersonalIdentifiers() {
        val result = OperationTraceRedactor.redactVisibleText(
            "联系 13812345678，邮箱 demo@example.com，卡号 6222021234567890",
        )

        assertTrue(result.contains("[phone-redacted]"))
        assertTrue(result.contains("[email-redacted]"))
        assertTrue(result.contains("[number-redacted]"))
        assertFalse(result.contains("13812345678"))
        assertFalse(result.contains("demo@example.com"))
        assertFalse(result.contains("6222021234567890"))
    }

    @Test
    fun shortEnglishCredentialKeywordUsesWordBoundary() {
        assertTrue(OperationTraceRedactor.containsCredentialHint("Enter PIN"))
        assertFalse(OperationTraceRedactor.containsCredentialHint("MaterialSpinner"))
        assertFalse(OperationTraceRedactor.containsCredentialHint("spinning loader"))
    }

    @Test
    fun ordinaryPaymentLabelIsRiskHintNotCredentialHint() {
        assertTrue(OperationTraceRedactor.containsPaymentHint("确认付款"))
        assertFalse(OperationTraceRedactor.containsCredentialHint("确认付款"))
    }

    @Test
    fun credentialLabelsAreDetectedAcrossLanguages() {
        assertTrue(OperationTraceRedactor.containsCredentialHint("请输入登录密码"))
        assertTrue(OperationTraceRedactor.containsCredentialHint("verification code"))
    }
}
