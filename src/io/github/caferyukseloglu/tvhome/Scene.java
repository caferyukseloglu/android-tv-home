package io.github.caferyukseloglu.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.Random;

/**
 * The scene themes: a mountain landscape drawn in code, once, into a bitmap that becomes Home's
 * backdrop (a still picture costs the TV less per frame than a live gradient). Sky, stars, a moon or
 * a low sun with its halo, three ridges from far and pale to near and dark, and a darker floor so
 * the cards stay readable. No photos: a few KB of code instead of megabytes of pictures.
 */
final class Scene {
    private final int[] sky;    // top, middle, horizon
    private final int[] ridges; // far, middle, near
    private final int light;    // moon or sun
    private final float lightX, lightY, lightR; // fractions of the picture
    private final int stars;
    private final long seed;

    private Scene(int[] sky, int[] ridges, int light, float lightX, float lightY, float lightR, int stars, long seed) {
        this.sky = sky;
        this.ridges = ridges;
        this.light = light;
        this.lightX = lightX;
        this.lightY = lightY;
        this.lightR = lightR;
        this.stars = stars;
        this.seed = seed;
    }

    /** null for themes without a scene. */
    static Scene of(String id) {
        switch (id) {
            case "night": return new Scene(new int[] {0xFF04060F, 0xFF0D1733, 0xFF2A3A63}, new int[] {0xFF223154, 0xFF151F3A, 0xFF0A0F1F},
                    0xFFF3F0DE, 0.66f, 0.36f, 0.032f, 160, 11); // the moon: right of the title, clear of the pills
            case "dusk": return new Scene(new int[] {0xFF170C2E, 0xFF5E2A57, 0xFFEE8A4E}, new int[] {0xFF4A2550, 0xFF2C1534, 0xFF150A1B},
                    0xFFFFC27A, 0.72f, 0.46f, 0.06f, 40, 23);
            case "dawn": return new Scene(new int[] {0xFF0D2636, 0xFF4A7A89, 0xFFE9CBA3}, new int[] {0xFF3B6273, 0xFF234252, 0xFF122833},
                    0xFFFFE7BC, 0.78f, 0.45f, 0.055f, 0, 37);
            default: return null;
        }
    }

    Bitmap render(int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, h * 0.72f, sky, new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        Random r = new Random(seed);
        for (int i = 0; i < stars; i++) { // fewer and fainter toward the horizon
            float y = (float) Math.pow(r.nextFloat(), 1.6) * h * 0.6f;
            p.setColor(0xFFFFFFFF);
            p.setAlpha((int) (40 + 180 * r.nextFloat() * (1 - y / (h * 0.6f))));
            c.drawCircle(r.nextFloat() * w, y, 0.4f + r.nextFloat() * 1.1f, p);
        }

        float lx = lightX * w, ly = lightY * h, lr = lightR * h;
        p.setAlpha(255);
        p.setShader(new RadialGradient(lx, ly, lr * 7, (light & 0xFFFFFF) | 0x55000000, light & 0xFFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(lx, ly, lr * 7, p); // the halo
        p.setShader(null);
        p.setColor(light);
        c.drawCircle(lx, ly, lr, p);

        float[] base = {0.60f, 0.70f, 0.82f}, height = {0.20f, 0.17f, 0.13f}, rough = {0.58f, 0.52f, 0.46f};
        for (int layer = 0; layer < 3; layer++) {
            float[] line = Ridges.line(seed * 31 + layer, 65, rough[layer]);
            Path ridge = new Path();
            ridge.moveTo(0, h);
            for (int i = 0; i < line.length; i++) {
                ridge.lineTo(i * w / (float) (line.length - 1), (base[layer] - line[i] * height[layer]) * h);
            }
            ridge.lineTo(w, h);
            ridge.close();
            p.setColor(ridges[layer]);
            c.drawPath(ridge, p);
            if (layer == 0) { // haze over the far range, so the layers read as distance
                p.setShader(new LinearGradient(0, h * 0.4f, 0, h * 0.75f, (sky[2] & 0xFFFFFF) | 0x40000000, sky[2] & 0xFFFFFF, Shader.TileMode.CLAMP));
                c.drawRect(0, h * 0.4f, w, h * 0.75f, p);
                p.setShader(null);
            }
        }

        p.setShader(new LinearGradient(0, h * 0.5f, 0, h, 0x00000000, 0xB3000000, Shader.TileMode.CLAMP));
        c.drawRect(0, h * 0.5f, w, h, p); // the floor the cards stand on
        return b;
    }
}
