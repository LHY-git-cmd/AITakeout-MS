"""检查待推送提交的完整可达历史，阻止私有文件、明显凭据和硬编码配置再次进入远程。

不打印命中原文或凭据。默认扫描 HEAD；--pre-push 从 Git 钩子的标准输入读取推送目标。
此规则检查不能替代供应商端撤销凭据、人工隐私审查和托管平台缓存清理。
"""
from __future__ import annotations
import argparse
import collections
from pathlib import PurePosixPath
import re
import subprocess
import sys

PRIVATE_PARTS = {'.idea', '.vscode', '.worktrees', '.venv', 'node_modules', '__pycache__',
                 '.pytest_cache', 'target', 'dist', 'test-results', 'playwright-report', '.codex-tmp', '.codex-temp'}
SECRET_FORMAT = re.compile(rb'(?<![A-Za-z0-9])(?:sk-[A-Za-z0-9_-]{20,}|LTAI[A-Za-z0-9]{16,}|AKIA[A-Z0-9]{16}|gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----)')
CONFIG_SECRET = re.compile(r'''(?im)^[ \t]*["']?([\w.-]*(?:password|passwd|secret|api[_-]?key|access[_-]?key|service[_-]?token)[\w.-]*)["']?[ \t]*[:=][ \t]*([^\r\n]*)$''')


def git(*args: str, data: bytes | None = None) -> bytes:
    """读取 Git 对象，错误时返回通用提示，避免输出敏感配置。"""
    return subprocess.run(['git', '-c', 'core.quotepath=false', *args], input=data,
                          check=True, capture_output=True).stdout


def private_path(name: str) -> bool:
    """保留模拟饮食数据，排除真实运行数据、私有环境与构建输出。"""
    path = PurePosixPath(name)
    if any(part in PRIVATE_PARTS for part in path.parts): return True
    if path.name == '.env' or path.name.endswith('.env.local') or path.name.startswith('.env.') and path.name.endswith('.local'): return True
    if path.name.startswith('report.') and path.suffix == '.json': return True
    if path.name == 'project.private.config.json': return True
    if name.startswith('data/') and not name.startswith('data/diet-seed/'): return True
    if name.startswith('agent-service/data/'): return True
    return path.suffix in {'.log', '.pyc', '.sqlite', '.sqlite3', '.db', '.tsbuildinfo'} or bool(re.search(r'(?i)(?:^|/)[^/]*backup[^/]*\.sql$', name))


def configuration_leaks(name: str, content: bytes) -> bool:
    """配置字段允许环境变量、空值和明确占位值，拒绝固定密码及签名密钥。"""
    if not ('/src/main/resources/application' in name and name.endswith(('.yml', '.yaml')) or PurePosixPath(name).name.startswith('.env')):
        return False
    text = content.decode('utf-8-sig', errors='replace')
    for match in CONFIG_SECRET.finditer(text):
        value = match.group(2).strip().strip('"\'')
        if not value or value.startswith(('${', '#')): continue
        if re.search(r'(?i)replace-with|change-me|example|your[_-]|<[^>]+>', value): continue
        return True
    return False


def inspect(revisions: list[str]) -> tuple[int, list[tuple[str, str]]]:
    """批量扫描提交的对象内容及历史文件名，不读取工作区私有配置。"""
    revisions = list(dict.fromkeys(revisions))
    objects = {}
    for line in git('rev-list', '--objects', *revisions).decode('utf-8', errors='replace').splitlines():
        oid, _, name = line.partition(' ')
        objects[oid] = name
    metadata = git('cat-file', '--batch-check=%(objectname) %(objecttype) %(objectsize)', data=('\n'.join(objects)+'\n').encode()).decode().splitlines()
    findings = set()
    # 同一对象可能出现在多个路径；独立检查历史路径，避免别名导致漏报。
    paths = git('log', *revisions, '--format=', '--name-only').decode('utf-8', errors='replace').splitlines()
    for name in paths:
        if name and private_path(name): findings.add((name, 'private-file-in-history'))
    process = subprocess.Popen(['git', 'cat-file', '--batch'], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    scanned = 0
    try:
        for row in metadata:
            oid, kind, size = row.split()
            if kind not in {'blob', 'commit', 'tag'}: continue
            process.stdin.write((oid+'\n').encode()); process.stdin.flush()
            process.stdout.readline()
            content = process.stdout.read(int(size)); process.stdout.read(1)
            scanned += 1
            name = objects[oid] or ('<' + kind + '-metadata>')
            if SECRET_FORMAT.search(content): findings.add((name, 'credential-format'))
            if kind == 'blob' and configuration_leaks(name, content): findings.add((name, 'literal-credential-in-config'))
    finally:
        process.stdin.close()
        process.stdout.close()
        if process.wait() != 0:
            raise ValueError('Git object scan failed')
    return scanned, sorted(findings)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pre-push', action='store_true')
    parser.add_argument('revisions', nargs='*')
    args = parser.parse_args()
    revisions = args.revisions or ['HEAD']
    if args.pre_push:
        revisions = []
        for line in sys.stdin:
            fields = line.split()
            if len(fields) != 4: raise ValueError('Invalid pre-push input')
            if set(fields[1]) != {'0'}: revisions.append(fields[1])
        if not revisions:
            print('No new commits to check.'); return 0
    count, findings = inspect(revisions)
    for name, rule in findings[:40]: print(f'{rule}: {name}')
    print(f'Objects checked: {count}; findings: {len(findings)}')
    return 1 if findings else 0


if __name__ == '__main__':
    try: sys.exit(main())
    except (OSError, ValueError, subprocess.CalledProcessError):
        print('Security check could not complete; push blocked.'); sys.exit(2)
