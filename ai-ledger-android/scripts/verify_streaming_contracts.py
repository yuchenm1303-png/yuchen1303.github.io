#!/usr/bin/env python3
"""Protect the Android real-time streaming path from silent JSON or UI regressions."""

from __future__ import annotations

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]


def read(relative_path: str) -> str:
    path = ROOT / relative_path
    if not path.exists():
        raise FileNotFoundError(relative_path)
    return path.read_text(encoding="utf-8")


def require_tokens(label: str, text: str, tokens: list[str], errors: list[str]) -> None:
    for token in tokens:
        if token not in text:
            errors.append(f"{label} missing streaming contract: {token}")


def require_absent_tokens(label: str, text: str, tokens: list[str], errors: list[str]) -> None:
    for token in tokens:
        if token in text:
            errors.append(f"{label} contains forbidden streaming contract: {token}")


def section(text: str, start: str, end: str, label: str, errors: list[str]) -> str:
    if start not in text:
        errors.append(f"{label} missing start marker: {start}")
        return ""
    tail = text.split(start, 1)[1]
    if end not in tail:
        errors.append(f"{label} missing end marker: {end}")
        return ""
    return tail.split(end, 1)[0]


def main() -> int:
    errors: list[str] = []

    client = read("app/src/main/java/com/yuchen/ailedger/service/AiWorkerClient.kt")
    require_tokens(
        "AiWorkerClient",
        client,
        [
            'put("stream", true)',
            'put("streaming", true)',
            'put("streamFormat", "sse")',
            'put("responseMode", "stream")',
            "transport.postStreamChat(",
            "onDelta = onDelta",
        ],
        errors,
    )
    continuation = section(
        client,
        "private fun sendClientToolResultForFinalReply(",
        "private fun endpointPool(",
        "AiWorkerClient client-tool continuation",
        errors,
    )
    require_tokens(
        "AiWorkerClient client-tool continuation",
        continuation,
        [
            "if (onDelta != null)",
            "streamChatBlocking(",
            "onDelta = onDelta",
            "sendChat(",
        ],
        errors,
    )
    require_absent_tokens(
        "AiWorkerClient client-tool continuation",
        continuation,
        [
            "onDelta = null",
            'put("stream", false)',
        ],
        errors,
    )

    transport = read("app/src/main/java/com/yuchen/ailedger/service/AiWorkerHttpTransport.kt")
    stream_transport = section(
        transport,
        "fun postStreamChat(",
        "fun requestHeaders(",
        "AiWorkerHttpTransport streaming request",
        errors,
    )
    require_tokens(
        "AiWorkerHttpTransport streaming request",
        stream_transport,
        [
            '"text/event-stream, application/x-ndjson, application/json, text/plain"',
            "StreamingDeltaCoalescer(onDelta = onDelta)",
            "connection.inputStream.bufferedReader(Charsets.UTF_8)",
            "reader.forEachStreamPayload",
            "deltaCoalescer.append(event.delta)",
            "deltaCoalescer.drain()",
        ],
        errors,
    )
    require_absent_tokens(
        "AiWorkerHttpTransport streaming request",
        stream_transport,
        [
            "reader.readText()",
            "streamedReply.append(response.reply)",
        ],
        errors,
    )

    coalescer = read("app/src/main/java/com/yuchen/ailedger/service/StreamingDeltaCoalescer.kt")
    require_tokens(
        "StreamingDeltaCoalescer",
        coalescer,
        [
            "FIRST_MAX_DELAY_MS = 80L",
            "fun append(delta: String)",
            "fun drain()",
            "onDelta(chunk)",
        ],
        errors,
    )

    view_model = read("app/src/main/java/com/yuchen/ailedger/AssistantViewModel.kt")
    pending_request = section(
        view_model,
        "private fun sendPendingRequest(",
        "private fun markMessageStopped(",
        "AssistantViewModel streaming UI path",
        errors,
    )
    require_tokens(
        "AssistantViewModel streaming UI path",
        pending_request,
        [
            "aiWorkerClient.streamChat(",
            "streamBuffer.append(delta)",
            "flushStreamingText(false)",
            "flushStreamingText(true)",
            "updateStreamingMessage(",
            "status = MessageStatus.Sending",
        ],
        errors,
    )
    require_absent_tokens(
        "AssistantViewModel streaming UI path",
        pending_request,
        [
            "aiWorkerClient.sendChat(",
            "response.reply.takeIf",
        ],
        errors,
    )

    home = read("app/src/main/java/com/yuchen/ailedger/ui/AssistantHomePolished.kt")
    require_tokens(
        "AssistantHomePolished",
        home,
        [
            "StreamingAssistantContentV2",
            "rememberFluidStreamingTextStateV2",
            "streamRevealShouldAnimate",
            "StreamingLivePlainTextV2",
            "status == MessageStatus.Sending",
        ],
        errors,
    )

    if errors:
        print("Streaming contract verification failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print("Streaming contracts verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
