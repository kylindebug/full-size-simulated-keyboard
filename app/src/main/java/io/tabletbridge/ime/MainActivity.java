package io.tabletbridge.ime;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Switch;

public final class MainActivity extends Activity {
    private TextView state;
    private int dp(int n) { return (int)(n*getResources().getDisplayMetrics().density); }
    private TextView text(LinearLayout parent,String value,int size,int color) {
        TextView view=new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setPadding(0,dp(10),0,dp(10)); parent.addView(view); return view;
    }
    private void button(LinearLayout parent,String label,View.OnClickListener listener) {
        Button button=new Button(this); button.setText(label); button.setAllCaps(false);
        button.setOnClickListener(listener); parent.addView(button,new LinearLayout.LayoutParams(-1,dp(56)));
    }
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xff111b26); getWindow().setNavigationBarColor(0xff111b26);
        ScrollView scroll=new ScrollView(this); scroll.setBackgroundColor(0xff111b26);
        LinearLayout column=new LinearLayout(this); column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(28),dp(32),dp(28),dp(32)); scroll.addView(column); setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)-> {
            v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom()); return insets;
        });
        text(column,"TABLET BRIDGE  /  0.3 · 甘雨樱花",13,0xffdcb7dc);
        text(column,"把平板变成全尺寸键盘",30,Color.WHITE);
        text(column,"为 Moonlight + Sunshine 远程桌面设计\n104 键 · 多指组合 · 可拖动悬浮",16,0xffb5c4d7);
        state=text(column,"",14,0xff69e2c2);
        button(column,"1  启用「全键盘模拟」",v->startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        button(column,"2  切换到「全键盘模拟」",v->((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker());
        button(column,"3  打开本机按键测试",v->startActivity(new Intent(this,DiagnosticActivity.class)));
        text(column,"在 Moonlight 中使用",21,Color.WHITE);
        text(column,"连接 Sunshine 后，在 Moonlight 串流画面用三指轻点呼出输入法（也可使用客户端的键盘入口）。远端 Windows 建议先使用英文 US 布局。",16,0xffc7d2df);
        text(column,"按下与弹起",21,Color.WHITE);
        Switch repeatSwitch=new Switch(this);
        repeatSwitch.setText("Moonlight 文本连发兼容"); repeatSwitch.setTextColor(Color.WHITE);
        repeatSwitch.setChecked(getSharedPreferences("panel",MODE_PRIVATE).getBoolean("compatibilityRepeat",false));
        repeatSwitch.setOnCheckedChangeListener((button,checked)->
                getSharedPreferences("panel",MODE_PRIVATE).edit().putBoolean("compatibilityRepeat",checked).apply());
        column.addView(repeatSwitch,new LinearLayout.LayoutParams(-1,dp(56)));
        text(column,"如果串流中长按只删除或输入一次，可开启此项，再重新呼出键盘。普通键长按会连续点击，修饰键仍保持按住。此模式用于文本输入；玩游戏时请关闭，以保持 WASD 等按键持续按住。主机自身也连发时，速度可能叠加。",14,0xffc7d2df);
        text(column,"默认物理模式：手指按下发送 DOWN，保持接触就保持按住，松开发送 UP。可同时按住 Ctrl、Shift、Alt、Win 和其他按键；移出键帽会释放，不会滑动输入邻键。长按重复由远端主机处理。",16,0xffc7d2df);
        text(column,"悬浮与单手操作",21,Color.WHITE);
        text(column,"控件位于小键盘上方。按住「移动」拖动键盘；点「大小」进入调整模式，拖边改变宽高比例、拖角等比缩放，点「完成」恢复输入。\n\n「透明」打开滑条，左右拖动调整背景透明度，文字与键帽保留可读性；「收起」变为小浮条；「物理」切换修饰键锁定；「释放」松开全部按键。",16,0xffc7d2df);
        text(column,"使用边界",21,Color.WHITE);
        text(column,"这是通过目标应用输入连接发送事件的输入法，不是系统级硬件注入。需要目标应用接收输入法事件；个别系统或 Moonlight 保留快捷键可能被拦截。锁定灯仅是本地提示，不能读取远端 Caps / Num / Scroll 状态。\n\n不提供拼音联想。无网络、无悬浮窗、无无障碍权限，不保存输入内容。键盘隐藏、失焦或旋转时会尽力释放按键；链路已断开时无法保证释放到达主机。",14,0xff94a8bf);
    }
    @Override protected void onResume() {
        super.onResume(); boolean enabled=false;
        InputMethodManager imm=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        for(InputMethodInfo item:imm.getEnabledInputMethodList()) if(item.getPackageName().equals(getPackageName())) enabled=true;
        String current=Settings.Secure.getString(getContentResolver(),Settings.Secure.DEFAULT_INPUT_METHOD);
        state.setText(!enabled?"状态：尚未启用":current!=null&&current.startsWith(getPackageName()+"/")?"状态：已启用，当前已选中":"状态：已启用，请切换输入法");
    }
}
