package com.example.taptranslate;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(24));
        root.setGravity(Gravity.TOP);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("点一下翻译");
        title.setTextSize(28);
        title.setTextColor(Color.BLACK);
        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView desc = new TextView(this);
        desc.setText("先看 YouTube 英文评论，不会时再翻译。\n\n1. 点下面按钮开启无障碍服务。\n2. 打开 YouTube 评论区。\n3. 右侧会出现蓝色“译”按钮。\n4. 把按钮拖到评论同一高度并轻点。\n5. 中文会显示在底部，原英文不变。\n\n首次翻译会下载英→中模型，之后可离线使用。\n\n本应用的无障碍服务只配置读取 YouTube 当前屏幕可见文字。");
        desc.setTextSize(17);
        desc.setTextColor(Color.DKGRAY);
        desc.setPadding(0, dp(16), 0, 0);
        desc.setLineSpacing(0, 1.2f);
        root.addView(desc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button open = new Button(this);
        open.setText("打开无障碍设置");
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(24);
        root.addView(open, p);
        open.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        setContentView(root);
    }
}
