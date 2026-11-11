#!/usr/bin/env python3
"""还原以 base64 文本保存在 assets/binaries/ 的少量二进制资源。

本仓库的上传通道只能写纯文本文件，因此启动图标 PNG 与
gradle/wrapper/gradle-wrapper.jar 以 `<原路径>.b64` 的形式随仓库附带。
clone 之后先运行一次本脚本，项目才能正常编译；脚本可重复运行，
已存在且内容一致的文件会被跳过。

    python tools/restore_binaries.py
"""

import base64
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "assets" / "binaries"


def main() -> None:
    if not SRC.is_dir():
        print("未找到 assets/binaries/，二进制资源可能已还原，跳过。")
        return

    restored = 0
    for b64_file in sorted(SRC.rglob("*.b64")):
        rel = b64_file.relative_to(SRC)
        # ic_launcher.png.b64 -> ic_launcher.png
        target_rel = rel.with_suffix("")
        target = ROOT / target_rel
        data = base64.b64decode(b64_file.read_text(encoding="ascii").strip())
        if target.exists() and target.read_bytes() == data:
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        print("还原:", target_rel.as_posix())
        restored += 1

    print(f"完成，共还原 {restored} 个文件。现在可以用 Android Studio 打开或运行 ./gradlew。")


if __name__ == "__main__":
    main()
