package com.example.taptranslate;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TranslateAccessibilityService extends AccessibilityService {
    private WindowManager wm;
    private TextView bubble;
    private WindowManager.LayoutParams bubbleParams;
    private Translator translator;
    private android.view.View card;
    private float downX, downY;
    private int startX, startY;
    private boolean dragging;

    private static class Candidate {
        final String text;
        final Rect rect;
        Candidate(String text, Rect rect) { this.text = text; this.rect = rect; }
    }

    @Override protected void onServiceConnected() {
        translator = Translation.getClient(new TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.CHINESE)
                .build());
        showBubble();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    private GradientDrawable bg(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private void showBubble() {
        if (bubble != null) return;
        wm = (WindowManager)getSystemService(WINDOW_SERVICE);
        bubble = new TextView(this);
        bubble.setText("译");
        bubble.setTextSize(19);
        bubble.setTextColor(Color.WHITE);
        bubble.setGravity(Gravity.CENTER);
        bubble.setBackground(bg(Color.rgb(30,136,229), 28));

        bubbleParams = new WindowManager.LayoutParams(
                dp(52), dp(52),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = getResources().getDisplayMetrics().widthPixels - dp(68);
        bubbleParams.y = getResources().getDisplayMetrics().heightPixels / 3;

        bubble.setOnTouchListener((v,e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX(); downY = e.getRawY();
                    startX = bubbleParams.x; startY = bubbleParams.y;
                    dragging = false; return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX()-downX, dy = e.getRawY()-downY;
                    if (Math.abs(dx)>dp(6) || Math.abs(dy)>dp(6)) dragging = true;
                    if (dragging) {
                        bubbleParams.x = startX + (int)dx;
                        bubbleParams.y = startY + (int)dy;
                        int sw = getResources().getDisplayMetrics().widthPixels;
                        int sh = getResources().getDisplayMetrics().heightPixels;
                        bubbleParams.x = Math.max(0, Math.min(bubbleParams.x, sw-dp(52)));
                        bubbleParams.y = Math.max(0, Math.min(bubbleParams.y, sh-dp(72)));
                        wm.updateViewLayout(bubble, bubbleParams);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!dragging) translateNearest();
                    return true;
            }
            return false;
        });
        wm.addView(bubble, bubbleParams);
    }

    private void translateNearest() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) { toast("请先打开 YouTube 评论区"); return; }
        List<Candidate> list = new ArrayList<>();
        collect(root, list, 0);
        if (list.isEmpty()) { toast("这一屏没有识别到英文评论"); return; }

        int targetY = bubbleParams.y + dp(26);
        Candidate best = null;
        int bestDist = Integer.MAX_VALUE;
        for (Candidate c : list) {
            int d = Math.abs(c.rect.centerY() - targetY);
            if (d < bestDist) { bestDist = d; best = c; }
        }
        if (best == null || bestDist > dp(280)) { toast("把“译”拖到评论同一高度再点"); return; }
        doTranslate(best.text);
    }

    private void collect(AccessibilityNodeInfo node, List<Candidate> out, int depth) {
        if (node == null || depth > 30 || out.size() > 100) return;
        if (node.isVisibleToUser()) {
            CharSequence cs = node.getText();
            if (cs == null || cs.length()==0) cs = node.getContentDescription();
            if (cs != null) {
                String s = cs.toString().replace('\n',' ').replaceAll("\\s+"," ").trim();
                if (looksEnglish(s)) {
                    Rect r = new Rect(); node.getBoundsInScreen(r);
                    if (!r.isEmpty()) out.add(new Candidate(s,r));
                }
            }
        }
        for (int i=0;i<node.getChildCount();i++) collect(node.getChild(i), out, depth+1);
    }

    private boolean looksEnglish(String s) {
        if (s.length()<6 || s.length()>600 || s.startsWith("@")) return false;
        String low = s.toLowerCase(Locale.ROOT);
        String[] ui = {"comments","comment","reply","replies","read more","like","dislike","share","subscribe","subscribed","youtube","shorts","home","library"};
        for (String x: ui) if (low.equals(x)) return false;
        int latin=0, letters=0;
        for (int i=0;i<s.length();i++) {
            char ch=s.charAt(i);
            if (Character.isLetter(ch)) {
                letters++;
                if ((ch>='A'&&ch<='Z')||(ch>='a'&&ch<='z')) latin++;
            }
        }
        return letters>=4 && latin>=4 && latin >= letters*0.6;
    }

    private void doTranslate(String source) {
        showCard("正在翻译…");
        translator.downloadModelIfNeeded(new DownloadConditions.Builder().build())
                .addOnSuccessListener(v -> translator.translate(source)
                        .addOnSuccessListener(this::showCard)
                        .addOnFailureListener(e -> { hideCard(); toast("翻译失败"); }))
                .addOnFailureListener(e -> { hideCard(); toast("首次模型下载失败"); });
    }

    private void showCard(String text) {
        hideCard();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18),dp(14),dp(18),dp(12));
        box.setBackground(bg(Color.rgb(28,28,30),18));

        TextView t = new TextView(this);
        t.setText(text); t.setTextColor(Color.WHITE); t.setTextSize(18);
        box.addView(t);
        TextView h = new TextView(this);
        h.setText("轻点这里关闭"); h.setTextColor(Color.LTGRAY); h.setTextSize(12); h.setPadding(0,dp(8),0,0);
        box.addView(h);

        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                getResources().getDisplayMetrics().widthPixels-dp(24),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        p.y = dp(24);
        box.setOnClickListener(v -> hideCard());
        card = box;
        wm.addView(box,p);
    }

    private void hideCard() {
        if (card != null && wm != null) {
            try { wm.removeView(card); } catch (Exception ignored) {}
            card = null;
        }
    }

    private void toast(String s) { Toast.makeText(this,s,Toast.LENGTH_SHORT).show(); }

    @Override public void onDestroy() {
        hideCard();
        if (bubble != null && wm != null) try { wm.removeView(bubble); } catch (Exception ignored) {}
        if (translator != null) translator.close();
        super.onDestroy();
    }
}
