package com.ujjwal.proxivity;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.FileProvider;

import android.util.DisplayMetrics;
import android.util.Log;

import com.ujjwal.proxivity.receivers.DeleteFile;
import com.ujjwal.proxivity.receivers.OpenFile;
import com.ujjwal.proxivity.receivers.ShareFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ScreenshotService extends ScreenOnOffService {

    public static int mWidth, mHeight, mDensity, flags, NOTIFICATION_ID= 25, SCREENSHOT_ID = 35;
    public static DisplayMetrics metrics;
    public static final Handler handler = new Handler();
    public static MediaProjection mProjection = null;
    public static Intent data = null;
    public static File FILE_LOCATION;
    static MediaProjectionManager mProjectionManager;
    static Context context;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        mProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        FILE_LOCATION = new File(Environment.getExternalStorageDirectory(), "Pictures/Screenshots/");;
        super.onCreate();
        context = this;

        Point size = new Point();
        display.getRealSize(size);
        mWidth = size.x;
        mHeight = size.y;
        metrics = getApplicationContext().getResources().getDisplayMetrics();
        mDensity = metrics.densityDpi;
        flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;

//        builder.setContentIntent(PendingIntent.getBroadcast(this, 0, new Intent(this, PermissionCheckReceiver.class), 0));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("TEST", " In onStartCommand");
        state = true;
        this.appendLog("onStart is on ThreadID: " + Thread.currentThread().getId() +"\nService Started:");

        NotificationHelper.addAction(this);
        startForeground(NOTIFICATION_ID, builder.build());
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @TargetApi(21)
    public static void capture() {

        try {
            final ImageReader mImageReader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBA_8888, 1);

            mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {

                    Log.d("Snap", "Image available now");
                    reader.setOnImageAvailableListener(null, handler);

                    mProjection.stop();
                    Log.d("Snap", "projection stopped");

                    Image image = reader.acquireLatestImage();
                    Log.d("Snap", "Got the image");

                    final Image.Plane[] planes = image.getPlanes();
                    Log.d("Snap", "onImageAvailable: "+planes.length);
                    final ByteBuffer buffer = planes[0].getBuffer();

//                    int pixelStride = planes[0].getPixelStride();
//                    int rowStride = planes[0].getRowStride();
//                    int rowPadding = rowStride - pixelStride * metrics.widthPixels;

                    // create bitmap
                    Bitmap bmp = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
                    bmp.copyPixelsFromBuffer(buffer);

                    image.close();
                    reader.close();
                    Log.d("Snap", "Bitmap created");
                    // save image to file
                    Log.d("Snap", "Saving image to file");
                    saveImage(bmp);

//                    Bitmap realSizeBitmap = Bitmap.createBitmap(bmp, 0, 0, metrics.widthPixels, bmp.getHeight());
                    bmp.recycle();

                    /* do something with [realSizeBitmap] */

                }

            }, new Handler());

            Log.d("Snap", "New image listener attached");

            System.out.println("capturing");

            mProjection = mProjectionManager.getMediaProjection(Activity.RESULT_OK, data);

            final VirtualDisplay virtualDisplay = mProjection.createVirtualDisplay("proxivity", mWidth, mHeight, mDensity, flags, mImageReader.getSurface(), null, new Handler());
            Log.d("Snap", "Virtual display created");

//            handler.postDelayed(new Runnable() {
//                @Override
//                public void run() {
//                    mProjection.stop();
//                    Log.d("Snap", "projection stopped");
//                }
//            }, 100);

//             mProjection.stop();
//             Log.d("Snap", "projection stopped");

//        return mImageReader;
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private static void saveImage(Bitmap bmp) {
        File file = new File(FILE_LOCATION, System.currentTimeMillis()+".jpg");
        try {
            file.createNewFile();
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            bmp.compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStream);
            fileOutputStream.close();

            NotificationHelper.createScreenshotChannel(context);

            // show notification after saving file
            NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(context);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, context.getString(R.string.screenshot_channel_id))
                    .setAutoCancel(true)
                    .setStyle(new NotificationCompat.BigPictureStyle()
                        .bigLargeIcon(bmp)
                        .bigPicture(bmp)
                        .setBigContentTitle(file.getName()))
                    .setContentTitle(file.getName())
                    .setContentText("Tap to view the image.")
                    .setLargeIcon(BitmapFactory.decodeFile(file.getPath()))
                    .setSmallIcon(R.drawable.ic_launcher_background)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(PendingIntent.getActivity(context, 0,
                            new Intent(Intent.ACTION_VIEW)
                                .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                .setDataAndType(FileProvider.getUriForFile(context, "com.ujjwal.proxivity.fileprovider", file),
                            "image/*"), 0))

                    /***
                        setting independent receivers for actions due to incompatibility using common receiver
                        using same pendingIntent with extra, overrides the extras set for other actions
                        thus, three actions execute exact same intent call with same values of extras set

                        hence, decided to use separate receivers for each actions
                     ***/
                    .addAction(R.drawable.locate, "Locate",
                            PendingIntent.getBroadcast(context, 0,
                                    new Intent(context, OpenFile.class)
                                            .putExtra("id", SCREENSHOT_ID)
                                            .putExtra("", ""),
                                    0))
                    .addAction(R.drawable.delete, "Delete",
                            PendingIntent.getBroadcast(context, 0,
                                    new Intent(context, DeleteFile.class)
                                            .putExtra("path", file.getPath())
                                            .putExtra("id", SCREENSHOT_ID),
                                    0))
                    .addAction(R.drawable.share, "Share",
                            PendingIntent.getBroadcast(context, 0,
                                    new Intent(context, ShareFile.class)
                                            .putExtra("uri", FileProvider.getUriForFile(context,
                                                    "com.ujjwal.proxivity.fileprovider", file).toString())
                                            .putExtra("id", SCREENSHOT_ID),
                                    PendingIntent.FLAG_UPDATE_CURRENT));

//        if (Build.VERSION.SDK_INT >= 21 ) builder.addInvisibleAction(R.drawable.ic_launcher_background, "Restart Service", PendingIntent.getService(context 0, new Intent(context, ScreenshotService.class), 0));
//        else builder.addAction(R.drawable.ic_launcher_background, "Restart Service", PendingIntent.getService(context 0, new Intent(context, ScreenshotService.class), 0));
            notificationManagerCompat.notify(SCREENSHOT_ID, builder.build());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
