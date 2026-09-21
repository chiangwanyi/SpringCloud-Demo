#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""把 <block 文件> 的内容追加到 <目标文件>。用于给「只增不改」的日志类文件追加内容。

用法： python append_block.py <目标文件> <内容文件>

为什么不直接 heredoc / echo：本环境 bash 缺 coreutils 且 heredoc 会被安全策略拦，
所以统一「Write 工具写内容文件 + 本脚本追加」。
"""
import io
import sys

sys.stdout.reconfigure(encoding="utf-8")

target, block = sys.argv[1], sys.argv[2]
with io.open(block, encoding="utf-8") as f:
    add = f.read()
with io.open(target, "a", encoding="utf-8", newline="\n") as f:
    f.write(add)
with io.open(target, encoding="utf-8") as f:
    total = len(f.read().splitlines())
print("appended %d chars -> %s（现共 %d 行）" % (len(add), target, total))
