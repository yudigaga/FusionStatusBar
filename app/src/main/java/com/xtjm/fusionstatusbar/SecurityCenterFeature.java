package com.xtjm.fusionstatusbar;

import java.util.Locale;

/** Independently stored options for the verified phone-manager host. */
public enum SecurityCenterFeature {
    SMART_PRIVACY("隐私与安全", "解锁智能防窥", "显示手机管家设置中的智能防窥入口，实际效果依赖屏幕和系统服务"),
    CAR_CRASH_DETECTION("隐私与安全", "解锁车祸检测", "仍需原生检测传感器；不自动开启检测或同意隐私条款"),
    MOTION_SICKNESS("出行辅助", "解锁晕车缓解", "解锁圆点提示时机，仍需真实运动传感器，部分设备不可用"),
    SCORE_666("扫描与确认", "锁定 666 分", "仅修改界面分数，不改变真实扫描结果"),
    SKIP_TEN_SECONDS("扫描与确认", "跳过十秒", "跳过敏感权限确认的等待时间，仍需手动确认"),
    GAME_THREE_SECOND_LAUNCH("游戏工具箱", "解锁三秒进游戏（王者荣耀）", "启用原生启动加速路径，实际进入耗时以设备和游戏为准"),
    GAME_FILL_DISPLAY("游戏工具箱", "解锁填充展示", "显示填充模式；部分游戏横向视野可能变窄"),
    AIM_ASSIST("游戏工具箱", "解锁准星辅助", "显示工具箱准星入口，效果依赖游戏和设备"),
    GYROSCOPE_ACCELERATION("游戏工具箱", "解锁陀螺仪加速", "游戏设置中也需开启陀螺仪"),
    COOLING_FAN_UI("游戏工具箱", "解锁散热风扇（UI）", "仅显示界面，不会增加散热硬件"),
    GAME_SOUND_UI("游戏工具箱", "解锁游戏音效（UI）", "仅显示界面，实际音效依赖设备实现"),
    INDEPENDENT_DISPLAY_UI("游戏工具箱", "解锁独显增强（UI）", "仅显示界面，实际能力依赖硬件"),
    SUPER_RESOLUTION("游戏工具箱", "解锁超级分辨率", "显示超级分辨率选项，实际效果请自行验证"),
    FRAME_INTERPOLATION("游戏工具箱", "解锁智能插帧", "显示插帧选项，保留宿主与超分的互斥规则，效果请自行验证"),
    GAME_HDR("游戏工具箱", "解锁游戏 HDR", "实际显示效果依赖面板和游戏"),
    VIDEO_MORE("视频工具箱", "解锁视频工具箱更多功能", "显示更多选项，实际效果请自行验证"),
    VIDEO_TEMPERATURE_LIMIT("视频工具箱", "解除视频工具箱温度限制", "仅取消工具箱对效果的温度禁用，保留底层硬件温控；思路来自 @东边的何某"),
    CINEMA_MODE("视频工具箱", "解锁影院模式", "需要杜比全景声支持；不支持时可能导致手机管家崩溃"),
    SMART_REFRESH_RATE("视频工具箱", "解锁智能刷新率", "实际刷新率效果依赖设备实现"),
    IMAGE_ENHANCEMENT("视频工具箱", "解锁画质提升", "实际增强效果依赖设备实现"),
    BATTERY_DETAILS("电池与充电", "电池详细信息", "显示宿主可读取的真实数据，未知值显示 --"),
    CHARGE_FEATURES("电池与充电", "解锁充电功能", "显示原生充电设置，实际充电能力依赖硬件"),
    SMART_CHARGING("电池与充电", "解锁智能充电", "在手机管家的充电功能中开启，实际效果依赖设备"),
    BYPASS_CHARGING_PLUS("电池与充电", "解锁旁路充电 Plus", "恢复旁路入口，实际效果请自行验证"),
    SKIP_CHARGE_DIALOGS("电池与充电", "去除智能和极速充电弹窗", "跳过对应确认，沿用原来的开启动作"),
    SCREEN_ON_TIME("电池与充电", "恢复参考亮屏时间", "在电池信息页显示自上次充满的参考统计，显示粒度可调");

    final String section;
    final String title;
    final String summary;

    SecurityCenterFeature(String section, String title, String summary) {
        this.section = section; this.title = title; this.summary = summary;
    }

    String key() { return "security_center_" + name().toLowerCase(Locale.ROOT); }
}
