"""CNN water-meter reader.

Place the three exported Keras models and meter_reader_config.json in
backend/models/meter_reader/. The models are exported by Part 7.10 of the
project notebook. Loading is lazy so the API can still start when model files
have not yet been copied to the server.
"""
from __future__ import annotations

from dataclasses import dataclass
import json
import os
from pathlib import Path
from threading import Lock

import cv2
import numpy as np

from ..config import settings


@dataclass
class ReadingSuggestion:
    value: float
    confidence: float
    per_digit_confidence: list[float]
    digits: str


_configured_model_dir = Path(os.getenv("METER_MODEL_DIR", settings.meter_model_dir))
_MODEL_DIR = _configured_model_dir if _configured_model_dir.is_absolute() else (
    Path(__file__).resolve().parents[2] / _configured_model_dir
)
_MODEL_LOCK = Lock()
_MODELS: dict | None = None
_MODEL_ERROR: str | None = None

SEG_SIZE = 256
CROP_H, CROP_W = 64, 256
N_DIGITS = 8
DIGIT_H, DIGIT_W = 64, 32
CLAHE = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(4, 8))


def _load_models() -> dict | None:
    global _MODELS, _MODEL_ERROR
    if _MODELS is not None:
        return _MODELS
    with _MODEL_LOCK:
        if _MODELS is not None:
            return _MODELS
        names = {
            "unet": "unet_window_locator.keras",
            "orientation": "orientation_cnn.keras",
            "digits": "single_digit_cnn.keras",
        }
        missing = [name for name in names.values() if not (_MODEL_DIR / name).is_file()]
        if missing:
            _MODEL_ERROR = "Missing model files: " + ", ".join(missing)
            return None
        try:
            import tensorflow as tf
            loaded = {
                key: tf.keras.models.load_model(_MODEL_DIR / filename, compile=False)
                for key, filename in names.items()
            }
            config_path = _MODEL_DIR / "meter_reader_config.json"
            config = json.loads(config_path.read_text()) if config_path.exists() else {}
            loaded["threshold"] = float(config.get("best_threshold", 0.5))
            loaded["orientation_cnn_enabled"] = bool(config.get("orientation_cnn_enabled", True))
            _MODELS = loaded
            _MODEL_ERROR = None
            return _MODELS
        except Exception as exc:
            _MODEL_ERROR = f"Could not load the meter CNN models: {exc}"
            return None


def model_status() -> dict:
    models = _load_models()
    return {
        "available": models is not None,
        "model_dir": str(_MODEL_DIR),
        "error": _MODEL_ERROR,
    }


def _window_geometry(mask: np.ndarray) -> dict | None:
    count, labels, stats, _ = cv2.connectedComponentsWithStats(mask.astype(np.uint8), connectivity=8)
    if count <= 1:
        return None
    component = 1 + int(np.argmax(stats[1:, cv2.CC_STAT_AREA]))
    if stats[component, cv2.CC_STAT_AREA] < 30:
        return None
    points = cv2.findNonZero((labels == component).astype(np.uint8))
    if points is None:
        return None
    rect = cv2.minAreaRect(cv2.convexHull(points))
    box = cv2.boxPoints(rect)
    e1, e2 = box[1] - box[0], box[2] - box[1]
    n1, n2 = float(np.linalg.norm(e1)), float(np.linalg.norm(e2))
    direction, length, short = (e1 / n1, n1, n2) if n1 >= n2 else (e2 / n2, n2, n1)
    if abs(direction[0]) >= abs(direction[1]):
        if direction[0] < 0:
            direction = -direction
    elif direction[1] < 0:
        direction = -direction
    return {
        "center": (float(rect[0][0]), float(rect[0][1])),
        "direction": direction.astype(np.float32),
        "length": float(length),
        "short": float(max(short, 1.0)),
    }


def _clahe(rgb: np.ndarray) -> np.ndarray:
    lab = cv2.cvtColor(rgb, cv2.COLOR_RGB2LAB)
    lab[..., 0] = CLAHE.apply(lab[..., 0])
    return cv2.cvtColor(lab, cv2.COLOR_LAB2RGB)


def _warp_window(rgb: np.ndarray, geo: dict) -> np.ndarray:
    center = np.asarray(geo["center"], np.float32)
    direction = np.asarray(geo["direction"], np.float32)
    normal = np.array([-direction[1], direction[0]], np.float32)
    half_l = geo["length"] * (1 + 2 * 0.04) / 2
    half_s = geo["short"] * (1 + 2 * 0.18) / 2
    src = np.stack([
        center - direction * half_l - normal * half_s,
        center + direction * half_l - normal * half_s,
        center + direction * half_l + normal * half_s,
        center - direction * half_l + normal * half_s,
    ]).astype(np.float32)
    scale = 2
    dst = np.array([
        [0, 0], [CROP_W * scale - 1, 0],
        [CROP_W * scale - 1, CROP_H * scale - 1],
        [0, CROP_H * scale - 1],
    ], np.float32)
    warped = cv2.warpPerspective(
        rgb, cv2.getPerspectiveTransform(src, dst), (CROP_W * scale, CROP_H * scale),
        flags=cv2.INTER_LINEAR, borderMode=cv2.BORDER_REPLICATE,
    )
    return _clahe(cv2.resize(warped, (CROP_W, CROP_H), interpolation=cv2.INTER_AREA))


def _weak_flip(crop: np.ndarray) -> bool:
    c = crop.astype(np.float32)
    red = np.clip(c[..., 0] - (c[..., 1] + c[..., 2]) / 2, 0, None)
    width = crop.shape[1] / N_DIGITS
    left = red[:, :int(3 * width)].mean()
    right = red[:, int(5 * width):].mean()
    return bool(left > right * 1.5 + 3)


def _digit_slots(crop: np.ndarray) -> np.ndarray:
    slots = []
    for position in range(N_DIGITS):
        x0 = round(position * CROP_W / N_DIGITS)
        x1 = round((position + 1) * CROP_W / N_DIGITS)
        x0, x1 = max(0, x0 - 2), min(CROP_W, x1 + 2)
        patch = crop[:, x0:x1]
        slots.append(cv2.resize(patch, (DIGIT_W, DIGIT_H), interpolation=cv2.INTER_AREA))
    return np.asarray(slots, dtype=np.uint8)


def read_meter(image_bytes: bytes) -> ReadingSuggestion | None:
    """Read an uploaded full meter photograph. Returns None when model files are absent."""
    models = _load_models()
    if models is None:
        return None
    if not image_bytes:
        raise ValueError("The uploaded photo is empty.")
    encoded = np.frombuffer(image_bytes, dtype=np.uint8)
    bgr = cv2.imdecode(encoded, cv2.IMREAD_COLOR)
    if bgr is None:
        raise ValueError("The uploaded file is not a readable image.")
    height, width = bgr.shape[:2]
    if height < 80 or width < 80:
        raise ValueError("The photo is too small. Take a clearer photo of the meter.")
    rgb = cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB)

    model_input = cv2.resize(rgb, (SEG_SIZE, SEG_SIZE), interpolation=cv2.INTER_AREA)[None].astype(np.float32) / 255.0
    probability = models["unet"].predict(model_input, verbose=0)[0]
    if probability.ndim == 3:
        probability = probability[..., 0]
    mask = (cv2.resize(probability, (width, height), interpolation=cv2.INTER_LINEAR) > models["threshold"]).astype(np.uint8)
    geometry = _window_geometry(mask)
    if geometry is None:
        raise ValueError("Could not locate the meter display. Retake the photo straight-on with the full display visible.")

    crop = _warp_window(rgb, geometry)
    if models["orientation_cnn_enabled"]:
        batch = crop[None].astype(np.float32) / 255.0
        probability_upside_down = float(models["orientation"].predict(batch, verbose=0).reshape(-1)[0])
        if probability_upside_down > 0.5:
            crop = np.ascontiguousarray(crop[::-1, ::-1])
    elif _weak_flip(crop):
        crop = np.ascontiguousarray(crop[::-1, ::-1])

    digit_images = _digit_slots(crop).astype(np.float32)
    probabilities = models["digits"].predict(digit_images, batch_size=8, verbose=0)
    if probabilities.shape != (N_DIGITS, 10):
        raise ValueError(f"Digit CNN returned an unexpected output shape: {probabilities.shape}")
    predicted = probabilities.argmax(axis=1)
    confidence = probabilities.max(axis=1)
    digits = "".join(str(int(d)) for d in predicted)
    value = int(digits) / 1000.0
    return ReadingSuggestion(
        value=round(value, 3),
        confidence=round(float(confidence.mean()), 4),
        per_digit_confidence=[round(float(x), 4) for x in confidence],
        digits=digits,
    )
