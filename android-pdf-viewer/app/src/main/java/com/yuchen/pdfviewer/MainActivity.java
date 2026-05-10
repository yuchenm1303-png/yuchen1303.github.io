package com.yuchen.pdfviewer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int REQUEST_OPEN_PDF = 1001;

    private TextView fileNameText;
    private TextView pageInfoText;
    private ImageView pageImage;
    private Button previousButton;
    private Button nextButton;
    private Button zoomOutButton;
    private Button zoomInButton;

    private ParcelFileDescriptor fileDescriptor;
    private PdfRenderer pdfRenderer;
    private int currentPageIndex = 0;
    private float zoomFactor = 1.0f;
    private final ExecutorService renderExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.setBackgroundColor(Color.rgb(250, 250, 250));

        TextView title = new TextView(this);
        title.setText("PDF 查看器");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(30, 30, 30));
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setPadding(0, 0, 0, dp(12));
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        Button openButton = new Button(this);
        openButton.setText("选择 PDF 文件");
        openButton.setOnClickListener(v -> openPdfPicker());
        root.addView(openButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        fileNameText = new TextView(this);
        fileNameText.setText("尚未选择文件");
        fileNameText.setTextSize(14);
        fileNameText.setTextColor(Color.DKGRAY);
        fileNameText.setPadding(0, dp(10), 0, dp(8));
        root.addView(fileNameText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.WHITE);

        LinearLayout pageContainer = new LinearLayout(this);
        pageContainer.setGravity(Gravity.CENTER);
        pageContainer.setPadding(dp(8), dp(8), dp(8), dp(8));

        pageImage = new ImageView(this);
        pageImage.setAdjustViewBounds(true);
        pageImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        pageContainer.addView(pageImage, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        scrollView.addView(pageContainer, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        );
        scrollParams.setMargins(0, 0, 0, dp(12));
        root.addView(scrollView, scrollParams);

        pageInfoText = new TextView(this);
        pageInfoText.setText("第 0 / 0 页");
        pageInfoText.setTextSize(15);
        pageInfoText.setGravity(Gravity.CENTER);
        pageInfoText.setPadding(0, 0, 0, dp(8));
        root.addView(pageInfoText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);

        previousButton = new Button(this);
        previousButton.setText("上一页");
        previousButton.setEnabled(false);
        previousButton.setOnClickListener(v -> showPage(currentPageIndex - 1));
        controls.addView(previousButton, weightedButtonParams());

        nextButton = new Button(this);
        nextButton.setText("下一页");
        nextButton.setEnabled(false);
        nextButton.setOnClickListener(v -> showPage(currentPageIndex + 1));
        controls.addView(nextButton, weightedButtonParams());

        zoomOutButton = new Button(this);
        zoomOutButton.setText("缩小");
        zoomOutButton.setEnabled(false);
        zoomOutButton.setOnClickListener(v -> {
            zoomFactor = Math.max(0.6f, zoomFactor - 0.2f);
            showPage(currentPageIndex);
        });
        controls.addView(zoomOutButton, weightedButtonParams());

        zoomInButton = new Button(this);
        zoomInButton.setText("放大");
        zoomInButton.setEnabled(false);
        zoomInButton.setOnClickListener(v -> {
            zoomFactor = Math.min(3.0f, zoomFactor + 0.2f);
            showPage(currentPageIndex);
        });
        controls.addView(zoomInButton, weightedButtonParams());

        root.addView(controls, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        setContentView(root);
    }

    private LinearLayout.LayoutParams weightedButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        return params;
    }

    private void openPdfPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        startActivityForResult(intent, REQUEST_OPEN_PDF);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OPEN_PDF && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            final int takeFlags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            try {
                getContentResolver().takePersistableUriPermission(uri, takeFlags);
            } catch (SecurityException ignored) {
                // 某些文件提供方不支持持久权限，不影响本次打开。
            }
            openPdf(uri);
        }
    }

    private void openPdf(Uri uri) {
        closeCurrentPdf();
        try {
            fileDescriptor = getContentResolver().openFileDescriptor(uri, "r");
            if (fileDescriptor == null) {
                Toast.makeText(this, "无法打开该文件", Toast.LENGTH_SHORT).show();
                return;
            }
            pdfRenderer = new PdfRenderer(fileDescriptor);
            if (pdfRenderer.getPageCount() == 0) {
                Toast.makeText(this, "该 PDF 没有可显示页面", Toast.LENGTH_SHORT).show();
                return;
            }
            currentPageIndex = 0;
            zoomFactor = 1.0f;
            fileNameText.setText(getDisplayName(uri));
            zoomOutButton.setEnabled(true);
            zoomInButton.setEnabled(true);
            showPage(currentPageIndex);
        } catch (IOException e) {
            Toast.makeText(this, "打开 PDF 失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void showPage(int pageIndex) {
        if (pdfRenderer == null || pageIndex < 0 || pageIndex >= pdfRenderer.getPageCount()) {
            return;
        }

        currentPageIndex = pageIndex;
        updateControls();
        pageInfoText.setText("第 " + (currentPageIndex + 1) + " / " + pdfRenderer.getPageCount() + " 页");
        pageImage.setImageDrawable(null);

        final int targetPage = pageIndex;
        final float targetZoom = zoomFactor;

        renderExecutor.execute(() -> {
            PdfRenderer.Page page = null;
            try {
                page = pdfRenderer.openPage(targetPage);
                int screenWidth = getResources().getDisplayMetrics().widthPixels - dp(48);
                int bitmapWidth = Math.max(1, Math.round(screenWidth * targetZoom));
                int bitmapHeight = Math.max(1, Math.round(bitmapWidth * (page.getHeight() / (float) page.getWidth())));
                Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.WHITE);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

                runOnUiThread(() -> {
                    if (targetPage == currentPageIndex && Math.abs(targetZoom - zoomFactor) < 0.001f) {
                        pageImage.setImageBitmap(bitmap);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "页面渲染失败", Toast.LENGTH_SHORT).show());
            } finally {
                if (page != null) {
                    page.close();
                }
            }
        });
    }

    private void updateControls() {
        if (pdfRenderer == null) {
            previousButton.setEnabled(false);
            nextButton.setEnabled(false);
            return;
        }
        previousButton.setEnabled(currentPageIndex > 0);
        nextButton.setEnabled(currentPageIndex < pdfRenderer.getPageCount() - 1);
    }

    private String getDisplayName(Uri uri) {
        String result = "已选择 PDF 文件";
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        if (cursor != null) {
            try {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    result = cursor.getString(nameIndex);
                }
            } finally {
                cursor.close();
            }
        }
        return result;
    }

    private void closeCurrentPdf() {
        if (pdfRenderer != null) {
            pdfRenderer.close();
            pdfRenderer = null;
        }
        if (fileDescriptor != null) {
            try {
                fileDescriptor.close();
            } catch (IOException ignored) {
            }
            fileDescriptor = null;
        }
    }

    @Override
    protected void onDestroy() {
        closeCurrentPdf();
        renderExecutor.shutdownNow();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
