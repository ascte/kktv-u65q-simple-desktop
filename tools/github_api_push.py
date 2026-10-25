#!/usr/bin/env python3
"""github_api_push.py - 通过 GitHub REST API 把本地 git 仓库内容推送到远端。

适用场景：所在网络只放行 api.github.com，而 github.com 网页与 git smart-http
（github.com/<owner>/<repo>.git）被拦截，`git push` 与 `gh auth login --web`
都不可用时。本项目的发布就是用它完成的。

原理：Git Data API（blobs -> tree -> commit -> ref）。blob 的 SHA 是内容哈希，
与本地一致；目录按 git 的规则自底向上构建嵌套 tree。因此远端提交与本地提交
内容完全相同（tree SHA 一致），本地之后 `git pull` 不会产生差异。

用法:
    GH_TOKEN=<pat> python tools/github_api_push.py <owner/repo> [branch]

要求:
    - 令牌具备 repo 权限（classic token 勾 repo 即可，无需 read:org）
    - 本地已完成 git commit；文件清单取自 `git ls-files -s`（尊重 .gitignore）
    - 远端仓库已存在且为空（或目标分支不存在）

注意: git 对 tree 条目的排序规则是「目录名按带尾部 / 比较」，本脚本按此排序；
若同一目录下同时存在文件 foo 与目录 foo（git 本身不允许），结果会不一致。
"""
import base64
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request

API = "https://api.github.com"


class ApiError(SystemExit):
    pass


def api(method, path, body=None, retries=3):
    headers = {
        "Authorization": "token " + TOKEN,
        "Accept": "application/vnd.github+json",
        "User-Agent": "github-api-push",
    }
    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    last = None
    for attempt in range(retries):
        req = urllib.request.Request(API + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                payload = r.read()
                return json.loads(payload) if payload else {}
        except urllib.error.HTTPError as e:
            detail = e.read().decode("utf-8", "replace")[:400]
            # 4xx（除 429）直接失败，重试无意义
            if 400 <= e.code < 500 and e.code != 429:
                raise ApiError(f"API {method} {path} -> {e.code}: {detail}")
            last = f"{e.code}: {detail}"
        except urllib.error.URLError as e:
            last = str(e)
        time.sleep(2 * (attempt + 1))
    raise ApiError(f"API {method} {path} 重试 {retries} 次后仍失败: {last}")


def git_out(*args):
    return subprocess.run(["git", "-c", "core.quotepath=false"] + list(args),
                          capture_output=True, text=True, check=True).stdout


def upload_blob(repo, sha):
    """上传指定 blob 的内容。

    内容从 git 对象库取（git cat-file），而不是读工作区文件——Windows 上
    core.autocrlf 会把工作区的文本文件转成 CRLF，直接读盘上传会得到与索引
    不一致的 blob SHA。
    """
    content = subprocess.run(["git", "cat-file", "blob", sha],
                             capture_output=True, check=True).stdout
    encoded = base64.b64encode(content).decode("ascii")
    r = api("POST", f"/repos/{repo}/git/blobs",
            {"content": encoded, "encoding": "base64"})
    if r.get("sha") != sha:
        print(f"  警告: 远端 SHA {r.get('sha')} != 本地 {sha}")
    return r["sha"]


def build_and_upload_tree(repo, node):
    """node: {name: ('blob', mode, sha) 或 嵌套 dict} -> 返回 tree SHA。

    git 的 tree 条目排序：目录名视为带尾部 '/' 参与比较（如 "foo/" < "foo.txt"）。
    """
    items = []
    for name in sorted(node, key=lambda n: n + "/" if isinstance(node[n], dict) else n):
        val = node[name]
        if isinstance(val, dict):
            sha = build_and_upload_tree(repo, val)
            items.append({"path": name, "mode": "040000", "type": "tree", "sha": sha})
        else:
            _, mode, sha = val
            items.append({"path": name, "mode": mode, "type": "blob", "sha": sha})
    r = api("POST", f"/repos/{repo}/git/trees", {"tree": items})
    return r["sha"]


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    repo = sys.argv[1]
    branch = sys.argv[2] if len(sys.argv) > 2 else "main"

    # 本地索引: <mode> <sha> <stage>\t<path>
    entries = []
    for line in git_out("ls-files", "-s").splitlines():
        meta, path = line.split("\t", 1)
        mode, sha, _stage = meta.split()
        entries.append((mode, sha, path))

    # 提交元数据（与本地保持一致，尽量让远端 SHA 与本地相同）
    meta = git_out("log", "-1", "--date=iso-strict",
                   "--format=%an%n%ae%n%aI%n%cn%n%ce%n%cI%n%B").split("\n")
    an, ae, ad, cn, ce, cd = meta[:6]
    message = "\n".join(meta[6:]).rstrip("\n")

    # 1) 上传所有 blob（SHA 是内容哈希，远端与本地必然一致）
    print(f"上传 {len(entries)} 个文件到 {repo} ...")
    root = {}
    for i, (mode, sha, path) in enumerate(entries, 1):
        upload_blob(repo, path, sha)
        node = root
        parts = path.split("/")
        for p in parts[:-1]:
            node = node.setdefault(p, {})
        node[parts[-1]] = ("blob", mode, sha)
        if i % 25 == 0 or i == len(entries):
            print(f"  {i}/{len(entries)}")

    # 2) 自底向上构建 tree
    tree_sha = build_and_upload_tree(repo, root)
    local_tree = git_out("rev-parse", "HEAD^{tree}").strip()
    print(f"远端 tree: {tree_sha}")
    print(f"本地 tree: {local_tree}")
    if tree_sha == local_tree:
        print("tree 一致：远端内容与本地完全相同")
    else:
        print("警告: tree 不一致（见上方 SHA 比对）")

    # 3) 创建提交并建立分支
    commit = api("POST", f"/repos/{repo}/git/commits", {
        "message": message,
        "tree": tree_sha,
        "parents": [],
        "author": {"name": an, "email": ae, "date": ad},
        "committer": {"name": cn, "email": ce, "date": cd},
    })
    api("POST", f"/repos/{repo}/git/refs",
        {"ref": f"refs/heads/{branch}", "sha": commit["sha"]})
    print(f"分支 {branch} -> {commit['sha']}")
    print("完成:", commit.get("html_url", ""))


TOKEN = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
if not TOKEN:
    raise SystemExit("请先设置 GH_TOKEN（需要 repo 权限的 GitHub PAT）")

if __name__ == "__main__":
    main()
