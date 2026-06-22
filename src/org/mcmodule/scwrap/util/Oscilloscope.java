package org.mcmodule.scwrap.util;

/**
 * @author github @IzumiiKonata
 * */
public class Oscilloscope {

    private static final int OSC_TARGET_TAPS = 384;
    private static final float OSC_EDGE_STRENGTH = 0.8f;
    private static final float OSC_BUFFER_STRENGTH = 1.0f;
    private static final float OSC_RESPONSIVENESS = 0.4f;
    private static final float OSC_AGC_SPEED = 0.12f;
    private static final float OSC_FILL = 0.86f;

    private final int captureSamples;
    private final int displaySamples;

    private final int stride;
    private final int wd;
    private final int nd;

    private final float[] corrected;
    private final float[] ds;
    private final float[] corrBuffer;
    private final float[] kernel;
    private final float[] slopeFinder;
    private final float[] bufferWindow;

    public final float[] output;

    private float gain = 0f;

    public Oscilloscope(int captureSamples, int displaySamples) {
        this.captureSamples = captureSamples;
        this.displaySamples = displaySamples;

        int s = Math.max(1, Math.round(displaySamples / (float) OSC_TARGET_TAPS));
        this.stride = s;
        this.wd = Math.max(8, displaySamples / s);
        this.nd = captureSamples / s;

        this.corrected = new float[captureSamples];
        this.ds = new float[nd];
        this.corrBuffer = new float[wd];
        this.kernel = new float[wd];
        this.slopeFinder = new float[wd];
        this.bufferWindow = new float[wd];

        this.output = new float[displaySamples];

        float center = (wd - 1) * 0.5f;
        float slopeStd = Math.max(1f, wd * 0.10f);
        float winStd = Math.max(1f, wd * 0.35f);
        int half = wd / 2;

        for (int k = 0; k < wd; k++) {
            float dsx = (k - center) / slopeStd;
            float g = (float) Math.exp(-0.5f * dsx * dsx);
            slopeFinder[k] = (k < half ? -OSC_EDGE_STRENGTH : OSC_EDGE_STRENGTH) * g;

            float dw = (k - center) / winStd;
            bufferWindow[k] = (float) Math.exp(-0.5f * dw * dw);
        }
    }

    public int getCaptureSamples() {
        return captureSamples;
    }

    public int getDisplaySamples() {
        return displaySamples;
    }

    public float getGain() {
        return gain;
    }

    public int compute(float[] input, double cellWidth, double cellHeight) {
        int n = captureSamples;
        int display = output.length;

        int wd = this.wd;
        int nd = Math.min(this.nd, n / stride);
        int searchD = Math.max(1, nd - wd);

        float[] corrected = this.corrected;
        float[] ds = this.ds;
        float[] buf = this.corrBuffer;
        float[] kernel = this.kernel;
        float[] slope = this.slopeFinder;
        float[] window = this.bufferWindow;

        double sum = 0;
        for (int i = 0; i < n; i++) {
            sum += input[i];
        }
        float mean = (float) (sum / n);
        for (int i = 0; i < n; i++) {
            corrected[i] = input[i] - mean;
        }

        double energy = 0;
        for (int j = 0; j < nd; j++) {
            int base = j * stride;
            float s = 0;
            int cnt = 0;
            for (int k = 0; k < stride; k++) {
                int idx = base + k;
                if (idx < n) {
                    s += corrected[idx];
                    cnt++;
                }
            }
            float v = cnt > 0 ? s / cnt : 0f;
            ds[j] = v;
            energy += (double) v * v;
        }
        float dsRms = (float) Math.sqrt(energy / nd);

        int triggerFull;

        if (dsRms < 1.0e-6f) {
            triggerFull = 0;
        } else {
            for (int k = 0; k < wd; k++) {
                kernel[k] = slope[k] + OSC_BUFFER_STRENGTH * buf[k];
            }

            int bestOff = 0;
            double bestScore = -Double.MAX_VALUE;
            for (int off = 0; off <= searchD; off++) {
                double score = 0;
                for (int k = 0; k < wd; k++) {
                    score += ds[off + k] * kernel[k];
                }
                if (score > bestScore) {
                    bestScore = score;
                    bestOff = off;
                }
            }

            triggerFull = bestOff * stride;

            double a2 = 0;
            for (int k = 0; k < wd; k++) {
                float v = ds[bestOff + k];
                a2 += (double) v * v;
            }
            float aRms = (float) Math.sqrt(a2 / wd);
            if (aRms > 1.0e-6f) {
                float ainv = 1f / aRms;
                for (int k = 0; k < wd; k++) {
                    float val = ds[bestOff + k] * ainv * window[k];
                    buf[k] += OSC_RESPONSIVENESS * (val - buf[k]);
                }

                double b2 = 0;
                for (int k = 0; k < wd; k++) {
                    b2 += (double) buf[k] * buf[k];
                }
                if (b2 > 1.0e-9) {
                    float bn = (float) (1.0 / Math.sqrt(b2 / wd));
                    for (int k = 0; k < wd; k++) {
                        buf[k] *= bn;
                    }
                }
            }
        }

        if (triggerFull > n - display) {
            triggerFull = n - display;
        }
        if (triggerFull < 0) {
            triggerFull = 0;
        }

        float peak = 0f;
        for (int j = 0; j < display; j++) {
            float v = corrected[triggerFull + j];
            output[j] = v;
            float a = Math.abs(v);
            if (a > peak) {
                peak = a;
            }
        }

        float target = peak > 1.0e-4f ? 1f / peak : 0f;
        gain = getGain() + OSC_AGC_SPEED * (target - gain);

        return display;
    }
}