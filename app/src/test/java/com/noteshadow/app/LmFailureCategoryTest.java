package com.noteshadow.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

public class LmFailureCategoryTest {
    @Test public void classifiesHttpWithoutCopyingServerText() {
        assertEquals("HTTP_402", LmFailureCategory.of(
                new IllegalStateException("账户余额不足（402）")));
        assertEquals("HTTP_422", LmFailureCategory.of(
                new IllegalStateException("接口参数不兼容（422）")));
    }

    @Test public void classifiesNetworkFailuresWithoutAddresses() {
        assertEquals("DNS", LmFailureCategory.of(new UnknownHostException("private.example")));
        assertEquals("TIMEOUT", LmFailureCategory.of(new SocketTimeoutException("secret")));
    }

    @Test public void neverReturnsRawExceptionMessages() {
        assertEquals("UNKNOWN", LmFailureCategory.of(new Exception("sk-secret")));
        assertEquals("NO_KEY", LmFailureCategory.of(
                new IllegalStateException("尚未配置模型密钥")));
    }
}
