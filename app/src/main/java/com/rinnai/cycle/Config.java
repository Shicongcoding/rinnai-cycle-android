package com.rinnai.cycle;

import android.content.Context;
import android.content.SharedPreferences;

public final class Config {
    public String phone = "";
    public String password = "";
    public String mac = "";
    public String classId = "";
    public String authCode = "";
    public int minutes = 3;

    public static Config load(Context context) {
        SharedPreferences p = context.getSharedPreferences("rinnai", Context.MODE_PRIVATE);
        Config c = new Config();
        c.phone = p.getString("phone", "");
        c.password = p.getString("password", "");
        c.mac = p.getString("mac", "");
        c.classId = p.getString("classId", "");
        c.authCode = p.getString("authCode", "");
        c.minutes = p.getInt("minutes", 3);
        return c;
    }

    public void save(Context context) {
        context.getSharedPreferences("rinnai", Context.MODE_PRIVATE)
                .edit()
                .putString("phone", phone)
                .putString("password", password)
                .putString("mac", mac)
                .putString("classId", classId)
                .putString("authCode", authCode)
                .putInt("minutes", minutes)
                .apply();
    }
}
