# 全尺寸模拟键盘

[下载 Release APK](https://github.com/kylindebug/full-size-simulated-keyboard/releases/latest)

### 数字键盘模式（0.3.5）

- 每次重新呼出键盘或切换输入目标时，默认数字模式，Num 按钮与普通键同色，不使用红色高亮。默认大小和比例保持原版不变。
- 点 Num 后按钮变灰（仍可点击），小键盘切换到标注的导航功能（Home、方向键、PgUp/PgDn、End、Ins、Del）；5 在此模式不发送按键。再点一次恢复数字。
- Num 现在是此软键盘自己的模式，不再向远端发送 NumLock，也不改变实体键盘的锁状态。数字模式发送主数字行的 0–9 和句点，导航模式发送专用导航键，避免 Windows 和 Linux 主机 NumLock 状态不同导致灯号相反。
- Shift / Ctrl / Alt 等组合键仍保留，数字行会按远端键盘布局解释，例如 Shift + 1 可能输入符号；这不是原生小键盘扫描码模式，Alt 数字码或专门区分小键盘的应用可能不兼容，非美式键盘布局也由远端解释。小键盘运算符、Enter 和长按兼容模式保持原行为。
- 按住某键时切换模式，该次按下、连发和松开仍使用原映射，重新按键才使用新模式。多个手指按同一个 Num 按钮只切换一次。

回归验证：分别连接 Windows 和麒麟，在远端 NumLock 开/关两种初始状态下检查默认 0–9、句点；Num 灰色时检查导航及 5 无输出；再检查切换模式、长按、Shift/Ctrl/Alt、松键、收起/重新呼出。云端单元测试不能替代实际串流主机验证。

云端 debug 测试包使用独立包名 `io.tabletbridge.ime.numlocktest`，显示为「全键盘模拟·Num测试」，可以与原正式版共存，不需要卸载原版；需单独启用并选择此测试输入法。release 包名保持不变，正式覆盖更新仍须原签名密钥。

### 长按按键

- 普通键按下立即响应，持续按住后按照 Android 的键盘重复延迟和间隔连发，包括 Backspace、Delete、字母、数字、空格、方向键和功能键。
- Shift / Ctrl / Alt / Win 保持按住状态；Caps / Num / Scroll Lock 每次按下只切换一次，Print Screen 和 Pause 不连发。
- 同时按住多个普通键时，最后按下的键连发；松开它后不会自动恢复前一个键的连发。组合键的重复事件使用当前修饰键状态。
- 松手、滑出按键、取消触摸、隐藏键盘、切换输入目标或点「释放」时停止相应的连发。默认模式的重复期间不会插入松键事件。

接收应用决定如何处理按键事件。[Moonlight Android](https://github.com/moonlight-stream/moonlight-android/blob/master/app/src/main/java/com/limelight/Game.java) 会忽略已映射按键的 Android 重复事件；[Sunshine](https://github.com/LizardByte/Sunshine/blob/master/src/input.cpp) 也忽略重复 DOWN，由主机的 `key_repeat_delay` 和 `key_repeat_frequency` 控制连发。完整物理按住行为需要主机启用按键重复。

如果 Moonlight 中长按只响应一次，可在应用主页开启「Moonlight 文本连发兼容」，然后重新呼出键盘。此模式将普通键连发转换为 UP/DOWN（重复次数为零），使事件通过串流过滤；修饰键和锁定键不连发。兼容模式默认关闭，适合文本输入，不能等同于物理持续按住：玩游戏时请关闭，主机已启用连发时速度也可能叠加。

验证：运行 `./gradlew testDebugUnitTest assembleDebug lintDebug`。设备上可在「按键事件测试」查看 `repeat=0,1,2…` 的 DOWN 和最终一个 UP；长按 Backspace 检查连续删除，按住 Shift + 字母检查大写连发，并在滑出、收起和切换应用后确认停止。Moonlight 场景还需连接实际主机验证。

### 键盘样式

![键盘样式](images/keyboard.png)

### 纯背景

![纯背景](images/background.png)
