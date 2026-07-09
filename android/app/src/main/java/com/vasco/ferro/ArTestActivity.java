package com.vasco.ferro;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Plane;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Prototype de faisabilité : lance une session ARCore et affiche en direct
 * l'état du suivi (TRACKING / PAUSED + raison) et le nombre de points suivis.
 * Objectif : vérifier si le suivi tient quand on approche d'un mur.
 */
public class ArTestActivity extends AppCompatActivity implements GLSurfaceView.Renderer {

    private GLSurfaceView glView;
    private TextView info;
    private Session session;
    private boolean installRequested = false;
    private int textureId = -1;
    private volatile boolean resumed = false;   // vrai seulement quand la session est réellement reprise
    private int retries = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);

        glView = new GLSurfaceView(this);
        glView.setEGLContextClientVersion(2);
        glView.setRenderer(this);
        glView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        root.addView(glView);

        info = new TextView(this);
        info.setTextColor(Color.WHITE);
        info.setBackgroundColor(0xCC000000);
        info.setPadding(40, 120, 40, 40);
        info.setTextSize(18);
        info.setText("Initialisation AR…");
        root.addView(info, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        Button close = new Button(this);
        close.setText("Fermer");
        close.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = 80;
        root.addView(close, lp);

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
            return;
        }
        startSession();
    }

    private void startSession() {
        if (session == null) {
            try {
                switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    case INSTALL_REQUESTED:
                        installRequested = true;
                        return;
                    case INSTALLED:
                        break;
                }
                session = new Session(this);
                Config config = new Config(session);
                config.setFocusMode(Config.FocusMode.AUTO);
                session.configure(config);
            } catch (Exception e) {
                info.setText("AR indisponible :\n" + e.getClass().getSimpleName() + " : " + e.getMessage());
                return;
            }
        }
        try {
            session.resume();
            glView.onResume();
            resumed = true;
        } catch (CameraNotAvailableException e) {
            resumed = false;
            retries++;
            if (retries <= 8) {
                info.setText("Caméra pas prête, tentative " + retries + "…");
                new android.os.Handler(getMainLooper()).postDelayed(this::startSession, 400);
            } else {
                info.setText("Caméra indisponible après plusieurs tentatives.\nFerme les autres apps caméra puis rouvre.");
            }
        } catch (Exception e) {
            session = null;
            info.setText("Erreur démarrage :\n" + e.getClass().getSimpleName() + " : " + e.getMessage());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        if (session != null) {
            glView.onPause();
            session.pause();
        }
    }

    @Override
    public void onRequestPermissionsResult(int rc, @NonNull String[] p, @NonNull int[] r) {
        super.onRequestPermissionsResult(rc, p, r);
        // onResume() relancera l'init une fois la permission accordée
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig c) {
        GLES20.glClearColor(0.04f, 0.05f, 0.08f, 1f);
        int[] t = new int[1];
        GLES20.glGenTextures(1, t, 0);
        textureId = t[0];
        // ARCore exige une texture EXTERNAL_OES pour l'image caméra
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int w, int h) {
        GLES20.glViewport(0, 0, w, h);
        if (session != null) session.setDisplayGeometry(0, w, h);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        if (session == null || !resumed) return;   // n'interroge la session que si elle est reprise
        try {
            session.setCameraTextureName(textureId);
            Frame frame = session.update();
            Camera cam = frame.getCamera();
            TrackingState st = cam.getTrackingState();

            int pts = 0;
            try (PointCloud pc = frame.acquirePointCloud()) {
                pts = pc.getPoints().remaining() / 4;
            } catch (Exception ignored) {}
            int planes = session.getAllTrackables(Plane.class).size();

            final StringBuilder sb = new StringBuilder();
            sb.append("SUIVI : ").append(st).append("\n");
            if (st != TrackingState.TRACKING) {
                sb.append("Raison : ").append(cam.getTrackingFailureReason()).append("\n");
            }
            sb.append("Points suivis : ").append(pts).append("\n");
            sb.append("Plans détectés : ").append(planes).append("\n\n");
            sb.append(st == TrackingState.TRACKING
                    ? "✓ le suivi tient"
                    : "✗ suivi perdu — approche/éloigne du mur");
            runOnUiThread(() -> info.setText(sb.toString()));
        } catch (Throwable e) {
            final String msg = e.getClass().getSimpleName() + " : " + e.getMessage();
            runOnUiThread(() -> info.setText("Erreur frame :\n" + msg));
        }
    }
}
