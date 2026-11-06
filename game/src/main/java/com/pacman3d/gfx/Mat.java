package com.pacman3d.gfx;

/** Matrices 3x4 (fila-mayor): 3x3 + traslacion, como las usa Renderer.mesh(). */
public final class Mat {
    private Mat() {}

    public static float[] identity() { return new float[]{1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0}; }

    /** Escala -> rotX(pitch) -> rotZ(roll) -> rotY(yaw) -> traslacion. */
    public static float[] trs(float x, float y, float z, float yaw, float pitch, float roll, float sx, float sy, float sz) {
        float[] m = identity();
        rotY(m, yaw); rotZ(m, roll); rotX(m, pitch);
        for (int i = 0; i < 3; i++) { m[i * 3] *= sx; m[i * 3 + 1] *= sy; m[i * 3 + 2] *= sz; }
        m[9] = x; m[10] = y; m[11] = z;
        return m;
    }

    public static float[] at(float x, float y, float z, float yaw) { return trs(x, y, z, yaw, 0, 0, 1, 1, 1); }

    /** a * b (aplica b primero, luego a). */
    public static float[] mul(float[] a, float[] b) {
        float[] o = new float[12];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) o[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
            o[9 + i] = a[i * 3] * b[9] + a[i * 3 + 1] * b[10] + a[i * 3 + 2] * b[11] + a[9 + i];
        }
        return o;
    }

    private static void rotY(float[] m, float a) { mulR(m, new float[]{(float) Math.cos(a), 0, (float) Math.sin(a), 0, 1, 0, -(float) Math.sin(a), 0, (float) Math.cos(a)}); }
    private static void rotX(float[] m, float a) { float c = (float) Math.cos(a), s = (float) Math.sin(a); postR(m, new float[]{1, 0, 0, 0, c, -s, 0, s, c}); }
    private static void rotZ(float[] m, float a) { float c = (float) Math.cos(a), s = (float) Math.sin(a); postR(m, new float[]{c, -s, 0, s, c, 0, 0, 0, 1}); }

    private static void mulR(float[] m, float[] r) { postR(m, r); }

    private static void postR(float[] m, float[] r) {
        float[] o = new float[9];
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) o[i * 3 + j] = m[i * 3] * r[j] + m[i * 3 + 1] * r[3 + j] + m[i * 3 + 2] * r[6 + j];
        System.arraycopy(o, 0, m, 0, 9);
    }
}
