# restore.ps1 - 一键恢复康佳 KKTV U65Q 到改造前的状态
# 用法:
#   1. 电视上重新打开 设置 > 开发者选项 > 网络调试(ADB), 确认 IP 仍是 10.10.10.27
#      (本机固件重启后网络 ADB 不会自动恢复, 必须手动重新打开!)
#   2. powershell -ExecutionPolicy Bypass -File restore.ps1 [-TvIp 10.10.10.27]
# 作用: 卸载自研启动器; 恢复( unhide )当贝桌面; 重启电视

param([string]$TvIp = "10.10.10.27")

$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$adb = Join-Path $here "platform-tools\adb.exe"

# 本次改造安装的包 (恢复时卸载)
$ours = @(
    "com.appletvstyle.launcher",   # 自研归藏风格启动器
    "com.konka.canary"             # 侧载可行性金丝雀测试包 (已卸载, 占位)
)

# 按需卸载的应用 (默认保留; 如不需要, 取消注释)
# $optional = @(
#     "com.xiaodianshi.tv.yst",    # 哔哩哔哩 TV 版 (云视听小电视)
#     "com.quark.yun.tv"           # 夸克网盘 TV 版
# )
# foreach ($p in $optional) {
#     $r = & $adb shell "pm uninstall $p" 2>$null
#     Write-Host ("  {0,-40} {1}" -f $p, ($r -join " "))
# }

# 说明: 本机固件拒绝了所有 pm disable-user (SecurityException),
# 因此没有任何系统包被禁用; 唯一被改动的是当贝桌面被 pm hide。
$hidden = @(
    "com.dangbei.leradlauncher.rom"
)

& $adb connect "${TvIp}:5555" | Out-Null
Start-Sleep -Seconds 1

Write-Host "== 1/3 卸载自研启动器 =="
foreach ($p in $ours) {
    $r = & $adb shell "pm uninstall $p" 2>$null
    Write-Host ("  {0,-40} {1}" -f $p, ($r -join " "))
}

Write-Host "== 2/3 恢复被隐藏的当贝桌面 =="
foreach ($p in $hidden) {
    $r = & $adb shell "pm unhide $p" 2>$null
    Write-Host ("  {0,-40} {1}" -f $p, ($r -join " "))
    # 双保险: 若曾用 pm disable-user 成功过 (本机并未成功), 也尝试 enable
    & $adb shell "pm enable $p" 2>$null | Out-Null
}

Write-Host "== 3/3 重启电视 =="
& $adb reboot
Write-Host "完成。重启后按 Home 键, 若弹出选择框请选 当贝桌面 -> 始终。"
