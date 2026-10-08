"""按明确范围导出项目源码并扫描敏感内容；不调用 Git，不删除已有目录，不复制本地数据库或密钥。

用法：python scripts/export-public-source.py --output <项目外的新目录>
可选 --private-env <私有配置> 只用于在内存中比对真实密钥，值不会写入报告。
检测失败时只报告相对路径、行号和规则，并且不创建发布目录。
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import sys


# 仅交付运行、构建和测试所需的项目目录。私人笔记、工作副本和工具配置不在范围内。
ROOT_FILES = {
    "README.md", "CHANGELOG.md", "LICENSE", "pom.xml", "compose.yml", "compose.external.yml",
    "Dockerfile", "Dockerfile.web", ".dockerignore", ".gitignore", ".env.example",
}
ROOT_DIRS = {"sky-common", "sky-pojo", "sky-server", "agent-service", "frontend", "deploy", "scripts", "observability"}
SKIP_DIRS = {
    ".git", ".idea", ".vscode", ".claude", ".worktrees", ".venv", "venv", "node_modules",
    "target", "dist", "build", "data", "__pycache__", ".pytest_cache", ".cache", "coverage",
    "test-results", "playwright-report", ".codex-tmp", ".codex-temp", "logs",
}
TEXT_SUFFIXES = {
    ".py", ".ps1", ".sh", ".bat", ".cmd", ".java", ".xml", ".properties", ".json", ".yml", ".yaml",
    ".js", ".cjs", ".mjs", ".ts", ".mts", ".vue", ".css", ".scss", ".sass", ".html",
    ".md", ".txt", ".sql", ".toml", ".ini", ".conf", ".http", ".lock", ".svg", ".csv",
}
ASSET_SUFFIXES = {".png", ".jpg", ".jpeg", ".gif", ".webp", ".ico", ".woff", ".woff2", ".ttf", ".mp3", ".wav", ".mp4"}
EXACT_NAMES = {"Dockerfile", "LICENSE", ".gitignore", ".dockerignore", ".npmrc", ".browserslistrc", ".editorconfig", ".eslintignore", ".prettierrc"}
PUBLIC_ENV_NAMES = {".env.development", ".env.production", ".env.production.uat", ".env.staging"}
FORMAT_SECRET = re.compile(r"(?<![A-Za-z0-9])(?:sk-[A-Za-z0-9_-]{20,}|AKIA[A-Z0-9]{16}|gh[pousr]_[A-Za-z0-9]{20,}|eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----)")
LOCAL_PATH = re.compile(r"(?i)(?:\b[A-Z]:[\\/](?:Users|workspace|develop|dev|Program Files)\b|/(?:Users|home)/[A-Za-z0-9_.-]+/)")
SENSITIVE_INSERT = re.compile(r"(?i)INSERT\s+INTO\s+`?(?:employee|user|address_book|orders|agent_message|agent_session)`?\s")
PRIVATE_KEY_NAME = re.compile(r"(?i)(?:password|secret|api[_-]?key|access[_-]?key|service[_-]?token)")


def is_link(path: Path) -> bool:
    """普通目录的链接不跟随，防止无意带入项目外数据。"""
    return path.is_symlink() or (hasattr(path, "is_junction") and path.is_junction())


def include_file(path: Path, relative: Path) -> bool:
    """过滤运行数据及生成文件；前端公开环境配置只允许已知文件名。"""
    name = path.name
    if name.startswith("report.") or name.endswith((".tsbuildinfo", ".sqlite3", ".db", ".log", ".map")):
        return False
    if name.startswith(".env"):
        return name == ".env.example" or (relative.parts[0] == "frontend" and name in PUBLIC_ENV_NAMES)
    return name in EXACT_NAMES or path.suffix.lower() in TEXT_SUFFIXES | ASSET_SUFFIXES


def candidates(project: Path):
    """枚举白名单内容；Embedding 独立项目仅复制明确列出的源码文件。"""
    for name in sorted(ROOT_FILES):
        p = project / name
        if p.is_file() and not is_link(p):
            yield p, Path(name)
    for name in sorted(ROOT_DIRS):
        start = project / name
        if not start.is_dir() or is_link(start):
            continue
        for base, dirs, files in os.walk(start, followlinks=False):
            dirs[:] = sorted(d for d in dirs if d not in SKIP_DIRS and not is_link(Path(base) / d))
            for filename in sorted(files):
                p = Path(base) / filename
                rel = p.relative_to(project)
                if not is_link(p) and include_file(p, rel):
                    yield p, rel
    # 本项目以链接挂载 Embedding 服务；只复制五个固定构建文件，不遍历模型、环境或 Git 目录。
    for name in ("app.py", "run.py", "Dockerfile", "requirements.txt", ".dockerignore", "model-manifest.example.json"):
        p = project / "sky-embedding" / name
        if p.is_file() and not p.is_symlink():
            yield p, Path("sky-embedding") / name
    # 公开说明仅纳入 Markdown；截图、Office 文件和私有数据库不会被包含。
    for folder, extensions in (("data/diet-seed", {".csv", ".json", ".md"}), ("知识库", {".md"})):
        start = project / folder
        if not start.is_dir() or is_link(start):
            continue
        for base, dirs, files in os.walk(start, followlinks=False):
            dirs[:] = sorted(d for d in dirs if not is_link(Path(base) / d))
            for name in sorted(files):
                p = Path(base) / name
                if p.suffix in extensions and not is_link(p):
                    yield p, p.relative_to(project)
    docs = project / "项目文档"
    if docs.is_dir() and not is_link(docs):
        for p in sorted(docs.rglob("*.md")):
            if not any(is_link(parent) for parent in p.parents if parent != project and project in parent.parents) and not p.is_symlink():
                yield p, p.relative_to(project)


def private_values(env_file: Path | None) -> list[str]:
    """仅从指定私有配置收集非占位密钥；不记录或返回其字段值到控制台。"""
    if env_file is None:
        return []
    values = []
    for line in env_file.read_text(encoding="utf-8-sig").splitlines():
        if line.lstrip().startswith("#") or "=" not in line:
            continue
        name, value = line.split("=", 1)
        value = value.strip().strip("\"'")
        if PRIVATE_KEY_NAME.search(name) and len(value) >= 6 and not re.search(r"(?i)replace-with|change-me|example|your[_-]", value):
            values.append(value)
    return list(set(values))


def scan_text(text: str, relative: Path, secrets: list[str]) -> list[dict]:
    """扫描源码与文档；只接受明确命名的虚构测试令牌，不豁免真实密钥命中。"""
    findings = []
    is_test = any(part in {"test", "tests", "e2e"} for part in relative.parts)
    for number, line in enumerate(text.splitlines(), 1):
        rules = []
        # 长密钥按完整字面值检查；短常见密码只检查配置赋值，避免把字段名或测试数字当作泄露。
        # 短密码的真实泄露无法仅靠字符串扫描可靠判断，需通过外部轮换最终消除风险。
        direct_assignment = re.search(r"(?i)(?:password|passwd|pwd|secret|api[_-]?key)[\w-]*[\"']?\s*[:=]\s*[\"']?([^\"'\s,;}]+)", line)
        short_config_match = (not is_test and direct_assignment and direct_assignment.group(1) in secrets)
        if any(value in line for value in secrets if len(value) >= 16) or short_config_match:
            rules.append("known-private-credential")
        if FORMAT_SECRET.search(line):
            rules.append("credential-format")
        if LOCAL_PATH.search(line):
            rules.append("machine-specific-path")
        if relative.suffix == ".sql" and SENSITIVE_INSERT.search(line) and not is_test:
            rules.append("private-table-data")
        if relative.name.startswith(".env") and relative.name != ".env.example" and "=" in line and not line.lstrip().startswith("#"):
            key = line.split("=", 1)[0].strip()
            if not key.startswith(("VUE_APP_", "VITE_", "NODE_ENV", "VUE_CLI_")) or PRIVATE_KEY_NAME.search(key):
                rules.append("non-public-frontend-environment")
        for rule in sorted(set(rules)):
            findings.append({"path": relative.as_posix(), "line": number, "rule": rule})
    return findings


def export(project: Path, output: Path, env_file: Path | None) -> dict:
    """先完成全部扫描再创建新目录；拒绝覆盖已有产物或输出到项目内。"""
    project = project.resolve()
    output = output.resolve()
    if output == project or project in output.parents:
        raise ValueError("发布目录必须位于项目目录之外")
    if output.exists():
        raise ValueError("发布目录已存在；请指定一个新的目录")
    files = list(candidates(project))
    secrets = private_values(env_file)
    findings = []
    expected = {}
    for source, relative in files:
        data = source.read_bytes()
        expected[relative.as_posix()] = hashlib.sha256(data).hexdigest()
        if source.suffix.lower() not in ASSET_SUFFIXES:
            try:
                findings.extend(scan_text(data.decode("utf-8-sig"), relative, secrets))
            except UnicodeDecodeError:
                findings.append({"path": relative.as_posix(), "line": 0, "rule": "unsupported-text-encoding"})
    if findings:
        return {"status": "blocked", "files_scanned": len(files), "findings": findings}
    output.mkdir(parents=True, exist_ok=False)
    for source, relative in files:
        dest = output / relative
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, dest)
        if hashlib.sha256(dest.read_bytes()).hexdigest() != expected[relative.as_posix()]:
            raise RuntimeError("导出期间源文件发生变化；此目录不得发布，请重新导出")
    # 清单不包含本机路径或凭据；可复核实际交付文件是否被再次修改。
    manifest = {"status": "passed", "files": expected, "known_credentials_checked": bool(secrets),
                "limitations": ["Static media not OCR-reviewed", "Git history not inspected", "Heuristic scan is not proof that all secrets are absent"]}
    (output / "PUBLIC_SOURCE_MANIFEST.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    return {"status": "passed", "files_exported": len(files), "known_credentials_checked": bool(secrets), "findings": []}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--project-root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--private-env", type=Path)
    args = parser.parse_args()
    try:
        report = export(args.project_root, args.output, args.private_env)
    except (OSError, ValueError, RuntimeError):
        print(json.dumps({"status": "error", "message": "导出失败，请检查目标目录、文件权限和源文件是否发生变更；不要发布未完成目录。"}, ensure_ascii=False))
        return 2
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report["status"] == "passed" else 1


if __name__ == "__main__":
    sys.exit(main())
