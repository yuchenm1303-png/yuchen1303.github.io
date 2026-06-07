import base64
import asyncio
import io
import os
import re
import time
from typing import Any, Dict, Optional, Tuple

import uvicorn
from fastapi import FastAPI, Header, HTTPException, Request
from PIL import Image


MODEL_ID = os.getenv("SHOWUI_MODEL_ID", "showlab/ShowUI-2B")
HOST = os.getenv("SHOWUI_HOST", "127.0.0.1")
PORT = int(os.getenv("SHOWUI_PORT", "9100"))
MIN_PIXELS = int(os.getenv("SHOWUI_MIN_PIXELS", "200704"))
MAX_PIXELS = int(os.getenv("SHOWUI_MAX_PIXELS", "602112"))
MAX_NEW_TOKENS = int(os.getenv("SHOWUI_MAX_NEW_TOKENS", "64"))
REQUEST_TIMEOUT_SEC = int(os.getenv("SHOWUI_REQUEST_TIMEOUT_SEC", "180"))
API_KEY = os.getenv("SHOWUI_PROVIDER_API_KEY", "").strip()

app = FastAPI(title="showui-local-provider")

_model = None
_processor = None
_device = "cpu"


def current_device() -> str:
    try:
        import torch

        return "cuda" if torch.cuda.is_available() else "cpu"
    except Exception:
        return "cpu"


def load_model() -> Tuple[Any, Any, str]:
    global _model, _processor, _device
    if _model is not None and _processor is not None:
        return _model, _processor, _device

    started_at = time.time()
    import torch
    from qwen_vl_utils import process_vision_info
    from transformers import AutoProcessor, Qwen2VLForConditionalGeneration

    # Keep the import visible for packaging checks; generation uses it below.
    _ = process_vision_info

    _device = "cuda" if torch.cuda.is_available() else "cpu"
    dtype = torch.float16 if _device == "cuda" else torch.float32
    print(f"[ShowUI] Loading model: {MODEL_ID}", flush=True)
    print(f"[ShowUI] Device: {_device}", flush=True)
    print(f"[ShowUI] dtype: {dtype}", flush=True)
    print(
        f"[ShowUI] max_pixels={MAX_PIXELS}, min_pixels={MIN_PIXELS}, max_new_tokens={MAX_NEW_TOKENS}",
        flush=True,
    )

    _processor = AutoProcessor.from_pretrained(
        MODEL_ID,
        min_pixels=MIN_PIXELS,
        max_pixels=MAX_PIXELS,
        trust_remote_code=True,
    )
    _model = Qwen2VLForConditionalGeneration.from_pretrained(
        MODEL_ID,
        torch_dtype=dtype,
        device_map="auto" if _device == "cuda" else None,
        trust_remote_code=True,
    )
    if _device != "cuda":
        _model = _model.to(_device)
    _model.eval()
    elapsed_ms = int((time.time() - started_at) * 1000)
    print(f"[ShowUI] Model loaded, elapsedMs={elapsed_ms}", flush=True)
    return _model, _processor, _device


def compact_error(goal: str, message: str, raw: str = "") -> Dict[str, Any]:
    return {
        "s": "u",
        "a": "need_user_help",
        "x": None,
        "y": None,
        "t": goal,
        "c": 0.0,
        "e": message[:160],
        "raw": raw[:1000],
    }


def strip_data_url(value: str) -> str:
    if "," in value and value.lower().startswith("data:"):
        return value.split(",", 1)[1]
    return value


def decode_image(image_base64: str) -> Image.Image:
    raw = base64.b64decode(strip_data_url(image_base64), validate=False)
    image = Image.open(io.BytesIO(raw)).convert("RGB")
    return image


def payload_goal(body: Dict[str, Any]) -> str:
    return str(
        body.get("goal")
        or body.get("agentGoal")
        or body.get("task")
        or body.get("message")
        or ""
    ).strip()


def payload_image(body: Dict[str, Any]) -> Tuple[Optional[Image.Image], int, int, str, int]:
    screen = body.get("screen") if isinstance(body.get("screen"), dict) else {}
    screenshot = screen.get("screenshot") if isinstance(screen.get("screenshot"), dict) else {}
    image_base64 = (
        body.get("imageBase64")
        or body.get("screenshotBase64")
        or screenshot.get("base64")
        or screenshot.get("imageBase64")
        or ""
    )
    if not image_base64:
        return None, 0, 0, "", 0

    image = decode_image(str(image_base64))
    width = int(screenshot.get("displayWidth") or screenshot.get("width") or image.width)
    height = int(screenshot.get("displayHeight") or screenshot.get("height") or image.height)
    mime_type = str(screenshot.get("mimeType") or body.get("mimeType") or "image/jpeg")
    return image, width, height, mime_type, len(str(image_base64))


def clamp01(value: float) -> float:
    return max(0.0, min(1.0, float(value)))


def normalize_coordinate_pair(x: float, y: float, width: int, height: int) -> Tuple[float, float]:
    if abs(x) <= 1.0 and abs(y) <= 1.0:
        return clamp01(x), clamp01(y)
    if 1.0 < abs(x) <= 100.0 and 1.0 < abs(y) <= 100.0:
        return clamp01(x / 100.0), clamp01(y / 100.0)
    if width > 0 and height > 0:
        return clamp01(x / float(width)), clamp01(y / float(height))
    return clamp01(x), clamp01(y)


def parse_coordinate(raw: str, width: int, height: int) -> Optional[Tuple[float, float]]:
    text = str(raw or "")
    bracket_match = re.search(
        r"[\[\(]\s*(-?\d+(?:\.\d+)?)\s*[,，]\s*(-?\d+(?:\.\d+)?)\s*[\]\)]",
        text,
    )
    if bracket_match:
        x, y = float(bracket_match.group(1)), float(bracket_match.group(2))
        return normalize_coordinate_pair(x, y, width, height)

    named_x = re.search(r"\bx\s*[:=]\s*(-?\d+(?:\.\d+)?)", text, flags=re.I)
    named_y = re.search(r"\by\s*[:=]\s*(-?\d+(?:\.\d+)?)", text, flags=re.I)
    if named_x and named_y:
        return normalize_coordinate_pair(float(named_x.group(1)), float(named_y.group(1)), width, height)

    numbers = re.findall(r"-?\d+(?:\.\d+)?", text)
    if len(numbers) >= 2:
        return normalize_coordinate_pair(float(numbers[0]), float(numbers[1]), width, height)
    return None


def build_messages(goal: str, image: Image.Image) -> Any:
    prompt = (
        "You are a GUI grounding model. Given the screenshot and target task, "
        "return only the click coordinate as [x, y]. Prefer normalized 0-1 "
        "coordinates. Task: "
        f"{goal}"
    )
    return [
        {
            "role": "user",
            "content": [
                {"type": "image", "image": image},
                {"type": "text", "text": prompt},
            ],
        }
    ]


def run_showui(goal: str, image: Image.Image, width: int, height: int) -> str:
    import torch
    from qwen_vl_utils import process_vision_info

    model, processor, device = load_model()
    started_at = time.time()
    print("[ShowUI] Inference start", flush=True)
    messages = build_messages(goal, image)
    text = processor.apply_chat_template(messages, tokenize=False, add_generation_prompt=True)
    image_inputs, video_inputs = process_vision_info(messages)
    inputs = processor(
        text=[text],
        images=image_inputs,
        videos=video_inputs,
        padding=True,
        return_tensors="pt",
    )
    inputs = inputs.to(device if device == "cuda" else "cpu")

    with torch.inference_mode():
        generated_ids = model.generate(**inputs, max_new_tokens=MAX_NEW_TOKENS)
    generated_ids_trimmed = [
        out_ids[len(in_ids) :] for in_ids, out_ids in zip(inputs.input_ids, generated_ids)
    ]
    output_text = processor.batch_decode(
        generated_ids_trimmed,
        skip_special_tokens=True,
        clean_up_tokenization_spaces=False,
    )[0]
    elapsed_ms = int((time.time() - started_at) * 1000)
    print(f"[ShowUI] Inference done, elapsedMs={elapsed_ms}", flush=True)
    print(f"[ShowUI] raw output={str(output_text).strip()[:1000]}", flush=True)
    return str(output_text).strip()


def check_auth(authorization: Optional[str]) -> None:
    if not API_KEY:
        return
    expected = f"Bearer {API_KEY}"
    if authorization != expected:
        raise HTTPException(status_code=401, detail="unauthorized")


@app.get("/health")
def health() -> Dict[str, Any]:
    return {
        "ok": True,
        "name": "showui-local-provider",
        "model": MODEL_ID,
        "device": current_device(),
        "coordinateSystem": "normalized_full_screenshot_0_1",
        "pid": os.getpid(),
        "maxPixels": MAX_PIXELS,
        "maxNewTokens": MAX_NEW_TOKENS,
    }


@app.post("/")
async def plan(request: Request, authorization: Optional[str] = Header(default=None)) -> Dict[str, Any]:
    check_auth(authorization)
    try:
        body = await request.json()
    except Exception:
        return compact_error("", "invalid_json")

    if not isinstance(body, dict):
        return compact_error("", "payload must be an object")

    goal = payload_goal(body)
    image, width, height, _mime_type, base64_size = payload_image(body)
    print(
        f"[ShowUI] POST / goal={goal[:120]!r}, hasImage={image is not None}, "
        f"imageSize={image.size if image is not None else None}, base64Size={base64_size}",
        flush=True,
    )
    if not goal:
        return compact_error(goal, "missing goal")
    if image is None:
        return compact_error(goal, "missing screenshot image")

    try:
        raw = await asyncio.wait_for(
            asyncio.to_thread(run_showui, goal, image, width or image.width, height or image.height),
            timeout=REQUEST_TIMEOUT_SEC,
        )
        point = parse_coordinate(raw, width or image.width, height or image.height)
        if point is None:
            print("[ShowUI] parse_failed", flush=True)
            return compact_error(goal, "unable to parse coordinate from model output", raw)
        x, y = point
        return {
            "s": "p",
            "a": "tap_xy",
            "x": round(x, 4),
            "y": round(y, 4),
            "t": goal,
            "c": 0.8,
            "e": "ShowUI predicted clickable coordinate.",
            "raw": raw[:1000],
        }
    except HTTPException:
        raise
    except asyncio.TimeoutError:
        print(f"[ShowUI] inference timeout after {REQUEST_TIMEOUT_SEC}s", flush=True)
        return compact_error(goal, "ShowUI local provider inference timeout")
    except Exception as exc:
        message = str(exc).lower()
        if "out of memory" in message or ("cuda" in message and "memory" in message):
            print("[ShowUI] CUDA OOM detected", flush=True)
            try:
                import torch

                if torch.cuda.is_available():
                    torch.cuda.empty_cache()
            except Exception:
                pass
            return compact_error(goal, "ShowUI local provider CUDA out of memory")
        print(f"[ShowUI] inference error: {type(exc).__name__}: {str(exc)[:160]}", flush=True)
        return compact_error(goal, f"showui inference failed: {type(exc).__name__}")


if __name__ == "__main__":
    uvicorn.run("server:app", host=HOST, port=PORT, reload=False)
