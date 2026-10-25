# 康佳 KKTV U65Q (RTD299O / Android 5.1.1 / SDK 22) Debloat 清单

电视: Konka Android TV 2991 · ro.build.version.release=5.1.1 · sdk=22 · 1080p · density 240
网络: Wi-Fi (USB 口空, 无 USB 网卡) · 当前唯一第三方包: com.konka.canary (测试用)

## 用户已确认的偏好
- 语音助手 (讯飞 Xiri): 不用 -> 禁用
- 投屏/多屏互动 (kkmultiscreen): 用 -> 保留
- 当贝市场 (应用商店): 用 -> 保留
- 蓝牙音箱 A2DP sink: 用 -> 保留
- Dock: 5 槽 · 启动器: 自研 minSdk 22 (Apple TV 风格)

## 分类与处置

### A. 厂商遥测/内容/广告服务 -> 禁用 (pm disable-user --user 0)
| 包 | 作用 | 理由 |
|---|---|---|
| com.iflytek.xiri | 讯飞语音助手主程序 | 用户不用语音; 含辅助功能服务 |
| com.iflytek.xiri2.system | 讯飞语音系统插件 | 同上; 提供 SystemPluginAccessableS |
| com.iflytek.itvs | 讯飞 TV 服务平台 | 同上; IflytekTVService/XiriLiveService |
| com.konka.tvsir | 康佳 TVSir 内容推荐/热榜 | 推荐位与广告内容源, 非电视核心功能 |

### B. 后台服务/推送 -> 禁用
| 包 | 作用 | 理由 |
|---|---|---|
| com.konka.localserver | 本地服务: 腾讯信鸽推送、静默安装、远程控制广播 | 后台常驻+推送, 电视功能不依赖 |
| com.konka.tvmanager | 电视管理器: 网络测速、APK 静默安装管理 | 后台服务, 非核心 |

### C. 工厂/工程菜单 -> 禁用 (全程未打开, 仅禁用)
| 包 | 作用 | 理由 |
|---|---|---|
| com.rtk.engmenu | Realtek 工程菜单 | 产线调试; 打开有风险故从未启动 |
| com.konka.kkfactory | 康佳工厂产测 | 同上; 含烧录 MAC/序列号 Receiver |

### D. 用户明确保留
com.konka.kkmultiscreen (投屏), com.dangbeimarket (当贝市场),
com.konka.a2dpsink (蓝牙音箱), com.iflytek.xiri.ime (屏幕键盘, 系统唯一 IME 之一)

### E. 电视运行必需, 永不禁用
com.konka.tvsettings (信源/HDMI/菜单), com.konka.systemsetting (系统设置),
com.konka.kksystemui (系统 UI/虚拟遥控), com.konka.multimedia (模拟/数字电视+本地播放),
com.konka.epgserver (EPG 节目单), com.konka.setupwizard (开机向导),
com.konka.bootlogicproxy (开机逻辑), com.konka.konkabtctlbind_5 + com.rtk.btpc (蓝牙遥控配对),
android, com.android.systemui, com.android.shell, com.android.settings 相关 providers,
com.android.bluetooth, com.android.packageinstaller, com.android.webview/htmlviewer,
com.android.providers.tv, com.android.defcontainer, com.android.externalstorage,
com.android.provision, com.android.providers.settings/downloads

## 备注
- Android 5.1 无 cmd package / 无 pm resolve-activity: HOME 唯一候选为当贝桌面,
  禁用后自研启动器自动成为唯一 HOME (无 set-home-activity 可用, 也不需要)
- package_verifier_enable=1 但侧载实测通过 (金丝雀 Success), 无验证失败
- 无 Google 服务 (无 Play Store/Play Services/Chromecast), 无 Private DNS (需 Android 9+)
- ro.adb.secure=1: 仅授权主机可连接, 无局域网裸奔风险
