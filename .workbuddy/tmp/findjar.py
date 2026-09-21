#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""在本地 maven 仓库里找 jar（Glob 对 .workbuddy 点目录/仓库路径不稳定）。"""
import os
import sys

sys.stdout.reconfigure(encoding="utf-8")

ROOTS = [r"C:\Users\jiang\.m2\repository"]
NEEDLES = sys.argv[1:] or ["mysql-connector"]
LIMIT = 20


def walk(root, needle):
    hits = []
    for dirpath, dirnames, filenames in os.walk(root):
        # 跳过 sources/javadoc 目录，减少噪声
        dirnames[:] = [d for d in dirnames if not d.endswith("-sources")]
        for f in filenames:
            if needle in f and f.endswith(".jar"):
                hits.append(os.path.join(dirpath, f))
                if len(hits) >= LIMIT:
                    return hits
    return hits


for root in ROOTS:
    if not os.path.isdir(root):
        print("(不存在) %s" % root)
        continue
    for needle in NEEDLES:
        for h in walk(root, needle):
            print(h)
print("done")
