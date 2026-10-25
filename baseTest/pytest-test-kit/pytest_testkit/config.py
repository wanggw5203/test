"""配置与载荷加载，对应 Java 的 ConfigLoader。

按文件后缀选择解析器：.yaml/.yml 用 PyYAML，.json 用标准库 json。
Java 版额外支持 JSON5（注释和尾逗号），Python 标准库不支持，
因此本包的示例载荷统一使用标准 JSON。
"""

import json
from pathlib import Path
from typing import Any

import yaml

_YAML_SUFFIXES = {".yaml", ".yml"}
_JSON_SUFFIXES = {".json"}


def load_config(path: Path | str) -> Any:
    """按后缀选择解析器读取配置文件。"""
    path = Path(path)
    suffix = path.suffix.lower()
    if suffix in _YAML_SUFFIXES:
        with path.open(encoding="utf-8") as handle:
            return yaml.safe_load(handle)
    if suffix in _JSON_SUFFIXES:
        with path.open(encoding="utf-8") as handle:
            return json.load(handle)
    raise ValueError(f"Supported formats are YAML and JSON: {path}")


def read_payload(path: Path | str) -> Any:
    """读取测试载荷（请求体/期望响应），规则与 load_config 相同。"""
    return load_config(path)
