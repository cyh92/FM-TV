package com.fongmi.android.tv.ui.activity;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.ParentalControl;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LockActivity extends AppCompatActivity {

    private TextView tvTime;
    private EditText etPassword;
    private View rootLayout; // 背景布局
    private ObjectAnimator colorAnim; // 动画控制器
    private Handler handler = new Handler(Looper.getMainLooper());
    
    // 定时任务：每秒更新一次屏幕中间的时间
    private Runnable timeRunnable = new Runnable() {
        @Override
        public void run() {
            updateTime();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lock);

        // 初始化控件
        tvTime = findViewById(R.id.tv_current_time);
        etPassword = findViewById(R.id.et_password);
        rootLayout = findViewById(R.id.root_layout); // 拿到背景布局

        // === 启动呼吸动画 ===
        startBreathingAnimation();

        // 按钮逻辑：解锁
        findViewById(R.id.btn_unlock).setOnClickListener(v -> {
            String pwd = etPassword.getText().toString();
            if ("8888".equals(pwd)) { // 这里设置你的密码
                ParentalControl.get().setUnlocked(true); // 告诉管家已解锁
                finish(); // 关闭页面
            } else {
                Toast.makeText(this, "密码错误", Toast.LENGTH_SHORT).show();
                etPassword.setText("");
            }
        });

        // 按钮逻辑：退出 App
        findViewById(R.id.btn_exit).setOnClickListener(v -> {
            finishAffinity(); // 关闭所有 Activity
            System.exit(0);   // 杀掉进程
        });
    }

    // 🌟 核心代码：呼吸特效 🌟
    private void startBreathingAnimation() {
        // 定义颜色：从 黑色(#000000) 到 绿色(#00C853)
        int colorBlack = Color.BLACK;
        int colorGreen = 0xFF00C853; // 充满生机的绿

        // 创建动画：改变 backgroundColor 属性
        colorAnim = ObjectAnimator.ofInt(rootLayout, "backgroundColor", colorBlack, colorGreen);
        
        // 设置时长：4秒完成一次呼吸（变亮再变暗）
        colorAnim.setDuration(4000); 
        
        // 颜色计算器：让颜色过渡丝滑
        colorAnim.setEvaluator(new ArgbEvaluator());
        
        // 循环模式：无限循环，且是“往返”模式（黑->绿->黑->绿...）
        colorAnim.setRepeatCount(ValueAnimator.INFINITE);
        colorAnim.setRepeatMode(ValueAnimator.REVERSE);
        
        // 插值器：模拟呼吸节奏，两头慢中间快
        colorAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        
        colorAnim.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(timeRunnable); // 开始更新时间
        // 恢复动画
        if (colorAnim != null && !colorAnim.isRunning()) {
            colorAnim.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(timeRunnable); // 停止更新时间
        // 暂停动画，省电
        if (colorAnim != null) {
            colorAnim.pause();
        }
    }

    // 更新屏幕中间的大时间
    private void updateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        tvTime.setText(sdf.format(new Date()));
    }

    // 屏蔽返回键
    @Override
    public void onBackPressed() {
        // 留空，禁止退出
    }
}
