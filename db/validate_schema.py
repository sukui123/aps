"""
DDL 静态校验（无依赖）

沙箱里跑不了 PostgreSQL 时，用它做结构级检查：
  1. 语句可切分、括号配平
  2. CREATE TABLE 的表名/列名不重复
  3. 每个 REFERENCES 的目标表在其之前已定义
  4. CREATE INDEX 指向的表与列存在
  5. INSERT 的列名在目标表中存在
  6. ALTER TABLE ... ADD CONSTRAINT ... REFERENCES 的目标存在

用法：python3 db/validate_schema.py db/migration/V1__p0_baseline.sql
局限：不做类型检查、不做表达式求值，不能替代真实数据库执行。
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

IDENT = r"[A-Za-z_][A-Za-z0-9_]*"


def split_statements(sql: str) -> list[str]:
    """按分号切分，正确跳过字符串、行注释与 $$ 块。"""
    out: list[str] = []
    buf: list[str] = []
    i, n = 0, len(sql)
    in_str = in_comment = in_dollar = False
    while i < n:
        ch = sql[i]
        if in_comment:
            buf.append(ch)
            if ch == "\n":
                in_comment = False
            i += 1
            continue
        if in_dollar:
            if sql.startswith("$$", i):
                in_dollar = False
                buf.append("$$")
                i += 2
                continue
            buf.append(ch)
            i += 1
            continue
        if in_str:
            buf.append(ch)
            if ch == "'":
                if i + 1 < n and sql[i + 1] == "'":
                    buf.append("'")
                    i += 2
                    continue
                in_str = False
            i += 1
            continue
        if sql.startswith("--", i):
            in_comment = True
            buf.append("--")
            i += 2
            continue
        if ch == "'":
            in_str = True
            buf.append(ch)
            i += 1
            continue
        if sql.startswith("$$", i):
            in_dollar = True
            buf.append("$$")
            i += 2
            continue
        if ch == ";":
            text = "".join(buf).strip()
            if text:
                out.append(text)
            buf = []
            i += 1
            continue
        buf.append(ch)
        i += 1
    tail = "".join(buf).strip()
    if tail:
        out.append(tail)
    return out


def strip_comments(stmt: str) -> str:
    lines = []
    for line in stmt.splitlines():
        idx = line.find("--")
        lines.append(line if idx < 0 else line[:idx])
    return "\n".join(lines)


def split_top_level(body: str) -> list[str]:
    """按顶层逗号切分列定义。"""
    parts: list[str] = []
    depth = 0
    cur: list[str] = []
    for ch in body:
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
        if ch == "," and depth == 0:
            parts.append("".join(cur).strip())
            cur = []
            continue
        cur.append(ch)
    if "".join(cur).strip():
        parts.append("".join(cur).strip())
    return parts


def main(path: str) -> int:
    sql = Path(path).read_text(encoding="utf-8")
    stmts = split_statements(sql)
    errors: list[str] = []
    warnings: list[str] = []

    tables: dict[str, list[str]] = {}
    order: list[str] = []
    deferred_fk: list[tuple[str, str]] = []

    for stmt in stmts:
        body = strip_comments(stmt).strip()
        head = body.split(None, 1)[0].upper() if body else ""

        # ---- 括号配平
        if body.count("(") != body.count(")"):
            errors.append(f"括号不配平：{body[:60]}...")

        # ---- CREATE TABLE
        m = re.match(rf"CREATE\s+TABLE\s+({IDENT})\s*\((.*)\)\s*$", body, re.S | re.I)
        if m:
            name, cols = m.group(1), m.group(2)
            if name in tables:
                errors.append(f"表重复定义：{name}")
            parts = split_top_level(cols)
            colnames: list[str] = []
            known = set(tables) | {name}      # 允许自引用
            for part in parts:
                upper = part.upper()
                if upper.startswith(("CONSTRAINT", "PRIMARY KEY", "UNIQUE", "CHECK", "FOREIGN KEY")):
                    for ref in re.findall(rf"REFERENCES\s+({IDENT})", part, re.I):
                        if ref not in known:
                            errors.append(f"{name}: 表级约束引用了未定义的表 {ref}")
                    continue
                cm = re.match(rf"({IDENT})\s+(.+)", part, re.S)
                if not cm:
                    warnings.append(f"{name}: 无法解析的列定义 -> {part[:50]}")
                    continue
                colnames.append(cm.group(1))
                for ref in re.findall(rf"REFERENCES\s+({IDENT})", part, re.I):
                    if ref not in known:
                        errors.append(f"{name}.{cm.group(1)}: 引用未定义的表 {ref}")
            dup = {c for c in colnames if colnames.count(c) > 1}
            if dup:
                errors.append(f"{name}: 列名重复 {sorted(dup)}")
            tables[name] = colnames
            order.append(name)
            continue

        # ---- CREATE INDEX
        m = re.match(rf"CREATE\s+(?:UNIQUE\s+)?INDEX\s+({IDENT})\s+ON\s+({IDENT})\s*\(([^)]*)\)",
                     body, re.I | re.S)
        if m:
            tname, cols = m.group(2), m.group(3)
            if tname not in tables:
                errors.append(f"索引 {m.group(1)} 指向未定义的表 {tname}")
            else:
                for raw in cols.split(","):
                    col = raw.strip().split()[0]
                    if col and not col.startswith("(") and col not in tables[tname]:
                        errors.append(f"索引 {m.group(1)} 指向不存在的列 {tname}.{col}")
            continue

        # ---- ALTER TABLE ... ADD CONSTRAINT ... REFERENCES
        if head == "ALTER":
            m = re.match(rf"ALTER\s+TABLE\s+({IDENT})\s+ADD\s+CONSTRAINT\s+({IDENT})\s+FOREIGN\s+KEY\s*\(([^)]*)\)\s+REFERENCES\s+({IDENT})",
                         body, re.I | re.S)
            if m:
                src, cname, col, target = m.groups()
                if src not in tables:
                    errors.append(f"{cname}: 源表未定义 {src}")
                elif col.strip() not in tables[src]:
                    errors.append(f"{cname}: 源列不存在 {src}.{col.strip()}")
                if target not in tables:
                    deferred_fk.append((cname, target))
            continue

        # ---- INSERT INTO ... (cols) VALUES
        m = re.match(rf"INSERT\s+INTO\s+({IDENT})\s*\(([^)]*)\)", body, re.I | re.S)
        if m:
            tname, cols = m.group(1), [c.strip() for c in m.group(2).split(",")]
            if tname not in tables:
                errors.append(f"INSERT 指向未定义的表 {tname}")
            else:
                for c in cols:
                    if c not in tables[tname]:
                        errors.append(f"INSERT 列不存在 {tname}.{c}")
            continue

        if head in ("CREATE", "ALTER", "INSERT", "COMMENT", "DO"):
            continue

    # 延迟外键最终解析
    for cname, target in deferred_fk:
        if target not in tables:
            errors.append(f"{cname}: 延迟外键目标表不存在 {target}")

    print(f"文件      : {path}")
    print(f"语句数    : {len(stmts)}")
    print(f"表数量    : {len(tables)}")
    print(f"列总数    : {sum(len(v) for v in tables.values())}")
    print(f"错误      : {len(errors)}")
    for e in errors:
        print("  ✗", e)
    if warnings:
        print(f"警告      : {len(warnings)}")
        for w in warnings:
            print("  !", w)

    if not errors:
        print("\n静态校验通过（未连接数据库，类型与表达式未验证）")
    return 1 if errors else 0


if __name__ == "__main__":
    default = "db/migration/V1__p0_baseline.sql"
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else default))
