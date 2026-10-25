package com.appletvstyle.launcher;

/**
 * 可分离高斯卷积（参考归藏电视 GaussianBlur 移植）。
 * 只处理小的不透明材质位图，全部在后台线程完成，不碰 UI 线程。
 */
final class GaussianBlur {
    private GaussianBlur() {}

    static int[] apply(int[] input, int width, int height, float sigma) {
        if (sigma <= 0) return input.clone();
        int radius = Math.max(1, (int) Math.ceil(sigma * 3));
        float[] kernel = new float[radius * 2 + 1];
        float sum = 0;
        for (int i = -radius; i <= radius; i++) {
            float n = (float) Math.exp(-i * i / (2 * sigma * sigma));
            kernel[i + radius] = n;
            sum += n;
        }
        for (int i = 0; i < kernel.length; i++) kernel[i] /= sum;
        int[] temp = new int[input.length];
        int[] output = new int[input.length];
        pass(input, temp, width, height, kernel, radius, true);
        pass(temp, output, width, height, kernel, radius, false);
        return output;
    }

    private static void pass(int[] in, int[] out, int w, int h, float[] k, int radius, boolean horizontal) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float r = 0, g = 0, b = 0;
                for (int n = -radius; n <= radius; n++) {
                    int xx = horizontal ? mirror(x + n, w) : x;
                    int yy = horizontal ? y : mirror(y + n, h);
                    int color = in[yy * w + xx];
                    float weight = k[n + radius];
                    r += ((color >> 16) & 255) * weight;
                    g += ((color >> 8) & 255) * weight;
                    b += (color & 255) * weight;
                }
                out[y * w + x] = 0xff000000
                        | (Math.round(r) << 16)
                        | (Math.round(g) << 8)
                        | Math.round(b);
            }
        }
    }

    private static int mirror(int x, int length) {
        if (length == 1) return 0;
        int period = length * 2;
        x = ((x % period) + period) % period;
        return x < length ? x : period - x - 1;
    }
}
