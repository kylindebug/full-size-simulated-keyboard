# 全尺寸模拟键盘

[下载 Release APK](https://github.com/kylindebug/full-size-simulated-keyboard/releases/latest)

### Num 数字 / 导航模式（0.3.4）

- 每次重新呼出输入会话默认进入数字模式。Num 与普通键颜色一致，按住 Num 也不会显示锁定高亮。点击 Num 后灰显并进入导航模式，再点恢复数字模式；灰显只是状态提示，仍可点击。
- 无修饰键时，数字模式的 0–9 和小数点发送明确的主键盘数字 / 句点按键；导航模式发送明确的 Insert、End、↓、PageDown、←、无操作（5）、→、Home、↑、PageUp、Delete。运算键和 Enter 保持原逻辑，布局、大小和比例不变。
- Num 只切换本地模式，不向主机发送 NumLock。这样普通数字 / 导航输入不依赖 Windows 或 Linux（麒麟）主机的 NumLock 初始状态，也不会因两端状态相反而颠倒功能。颜色直接读取决定映射的同一个模式状态。
- **原生组合键边界：** 数字模式下，先按住或锁定 Shift / Alt / Ctrl / Win，再按小键盘数字或小数点，会发送原生 `KEYCODE_NUMPAD_*`，保留小键盘身份，例如 Windows 的 Alt 数字码以及 Shift 的原生小键盘行为。它们仍由主机的 NumLock、键盘布局和应用决定；本地 Num 不同步主机锁定状态。在主机上设置所需 NumLock 后使用原生组合键。导航模式始终发送导航键，Shift / Ctrl + 导航可用于选择和跳转。
- 在数字模式下增减修饰键时，会先释放已按住的小键盘数字 / 小数点；手指需抬起再按，以免连发从数字变成 Shift 符号或 Alt 快捷键。切换 Num 也会先释放这些键，并停止它们的连发；修饰键、运算键和其他普通键保持按住。不会在旧手指仍按住时自动产生新模式按键。
- Android 输入法经 Moonlight 的 KeyEvent 通道没有远端 NumLock 状态回传；仅修改本应用无法同时保证远端原生小键盘锁定状态。上述普通模式使用明确键码，原生组合保留主机行为，不伪造同步。本版本的自动测试验证事件序列；Windows / 麒麟实际串流验收需在对应主机完成。

### 长按按键

- 普通键按下立即响应，持续按住后按照 Android 的键盘重复延迟和间隔连发，包括 Backspace、Delete、字母、数字、空格、方向键和功能键。
- Shift / Ctrl / Alt / Win 保持按住状态；Caps / Scroll Lock 每次按下只切换一次，Num 每次按下只切换一次本地模式，Print Screen 和 Pause 不连发。
- 同时按住多个普通键时，最后按下的键连发；松开它后不会自动恢复前一个键的连发。组合键的重复事件使用当前修饰键状态。
- 松手、滑出按键、取消触摸、隐藏键盘、切换输入目标或点「释放」时停止相应的连发。默认模式的重复期间不会插入松键事件。

接收应用决定如何处理按键事件。[Moonlight Android](https://github.com/moonlight-stream/moonlight-android/blob/master/app/src/main/java/com/limelight/Game.java) 会忽略已映射按键的 Android 重复事件；[Sunshine](https://github.com/LizardByte/Sunshine/blob/master/src/input.cpp) 也忽略重复 DOWN，由主机的 `key_repeat_delay` 和 `key_repeat_frequency` 控制连发。完整物理按住行为需要主机启用按键重复。

如果 Moonlight 中长按只响应一次，可在应用主页开启「Moonlight 文本连发兼容」，然后重新呼出键盘。此模式将普通键连发转换为 UP/DOWN（重复次数为零），使事件通过串流过滤；修饰键和锁定键不连发。兼容模式默认关闭，适合文本输入，不能等同于物理持续按住：玩游戏时请关闭，主机已启用连发时速度也可能叠加。

验证：运行 `./gradlew testDebugUnitTest assembleDebug lintDebug`。设备上可在「按键事件测试」查看 `repeat=0,1,2…` 的 DOWN 和最终一个 UP；长按 Backspace 检查连续删除，按住 Shift + 字母检查大写连发，并在滑出、收起和切换应用后确认停止。Moonlight 场景还需连接实际主机验证。

### 键盘样式

![键盘样式](images/keyboard.png)

### 纯背景

![纯背景](images/background.png)
