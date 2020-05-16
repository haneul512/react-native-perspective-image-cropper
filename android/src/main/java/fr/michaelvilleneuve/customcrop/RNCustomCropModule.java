
package fr.michaelvilleneuve.customcrop;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.MediaStore;
import android.util.Base64;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.pm.PackageManager;
import java.io.FileNotFoundException;
import java.io.IOException;
import android.database.Cursor;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import java.io.OutputStream;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.imgcodecs.*;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import org.opencv.calib3d.Calib3d;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.io.FileOutputStream;
import android.os.Environment;

public class RNCustomCropModule extends ReactContextBaseJavaModule {

  public Point pointMethod(Point point, double width, double height, double imageWidth, double imageHeight) {
    double x = imageWidth * (point.x / width);
    double y = imageHeight * (point.y / height);
    return new Point(x, y);
  }

  private final ReactApplicationContext reactContext;

  public RNCustomCropModule(ReactApplicationContext reactContext) {
    super(reactContext);
    OpenCVLoader.initDebug();
    this.reactContext = reactContext;
  }

  private String getRealPathFromURI(String contentURIString) {
    String filePath;
    Uri contentURI = Uri.parse(contentURIString);
    Cursor cursor = this.getCurrentActivity().getContentResolver().query(contentURI, null, null, null, null);
    if (cursor == null) {
      filePath = contentURI.getPath();
    } else {
      cursor.moveToFirst();
      int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
      filePath = cursor.getString(idx);
      cursor.close();
    }
    return filePath;
  }

  public static final String insertImage(ContentResolver cr,
                                         Bitmap source,
                                         String title,
                                         String description) {

    ContentValues values = new ContentValues();
    values.put(Images.Media.TITLE, title);
    values.put(Images.Media.DISPLAY_NAME, title);
    values.put(Images.Media.DESCRIPTION, description);
    values.put(Images.Media.MIME_TYPE, "image/jpeg");
    // Add the date meta data to ensure the image is added at the front of the gallery
    values.put(Images.Media.DATE_ADDED, System.currentTimeMillis());
    values.put(Images.Media.DATE_TAKEN, System.currentTimeMillis());

    Uri url = null;
    String stringUrl = null;    /* value to be returned */

    try {
      url = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

      if (source != null) {
        OutputStream imageOut = cr.openOutputStream(url);
        try {
          source.compress(Bitmap.CompressFormat.JPEG, 50, imageOut);
        } finally {
          imageOut.close();
        }

        long id = ContentUris.parseId(url);
        // Wait until MINI_KIND thumbnail is generated.
        Bitmap miniThumb = Images.Thumbnails.getThumbnail(cr, id, Images.Thumbnails.MINI_KIND, null);
        // This is for backward compatibility.
        storeThumbnail(cr, miniThumb, id, 50F, 50F,Images.Thumbnails.MICRO_KIND);
      } else {
        cr.delete(url, null, null);
        url = null;
      }
    } catch (Exception e) {
      if (url != null) {
        cr.delete(url, null, null);
        url = null;
      }
    }

    if (url != null) {
      stringUrl = url.toString();
    }

    return stringUrl;
  }

  /**
   * A copy of the Android internals StoreThumbnail method, it used with the insertImage to
   * populate the android.provider.MediaStore.Images.Media#insertImage with all the correct
   * meta data. The StoreThumbnail method is private so it must be duplicated here.
   * @see android.provider.MediaStore.Images.Media (StoreThumbnail private method)
   */
  private static final Bitmap storeThumbnail(
          ContentResolver cr,
          Bitmap source,
          long id,
          float width,
          float height,
          int kind) {

    // create the matrix to scale it
    Matrix matrix = new Matrix();

    float scaleX = width / source.getWidth();
    float scaleY = height / source.getHeight();

    matrix.setScale(scaleX, scaleY);

    Bitmap thumb = Bitmap.createBitmap(source, 0, 0,
            source.getWidth(),
            source.getHeight(), matrix,
            true
    );

    ContentValues values = new ContentValues(4);
    values.put(Images.Thumbnails.KIND,kind);
    values.put(Images.Thumbnails.IMAGE_ID,(int)id);
    values.put(Images.Thumbnails.HEIGHT,thumb.getHeight());
    values.put(Images.Thumbnails.WIDTH,thumb.getWidth());

    Uri url = cr.insert(Images.Thumbnails.EXTERNAL_CONTENT_URI, values);

    try {
      OutputStream thumbOut = cr.openOutputStream(url);
      thumb.compress(Bitmap.CompressFormat.JPEG, 100, thumbOut);
      thumbOut.close();
      return thumb;
    } catch (FileNotFoundException ex) {
      return null;
    } catch (IOException ex) {
      return null;
    }
  }

  @Override
  public String getName() {
    return "CustomCropManager";
  }

  @ReactMethod
  public void crop(ReadableMap points, String imageUri, Callback callback) {

    if (ContextCompat.checkSelfPermission(this.getCurrentActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
      // Permission is not granted
      ActivityCompat.requestPermissions(this.getCurrentActivity(), new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);

      callback.invoke("Please try again after turning the permission on", null);
    }
    else {

      // request runtime permission first
//    if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
//      Log.v(TAG,"Permission is granted");
//      //File write logic here
//      return true;
//    }


      try {
        //FileOutputStream fOut = new FileOutputStream(file);

        //bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fOut); // saving the Bitmap to a file compressed as a JPEG with 85% compression rate
        //MediaStore.Images.Media.insertImage(getContentResolver(),file.getAbsolutePath(),file.getName(),file.getName());


        //WritableMap map = Arguments.createMap();
        // bitmap.putString("image", Base64.encodeToString(byteArray, Base64.DEFAULT));
        // String retVAl = file.getAbsolutePath();

        Point newLeft = new Point(points.getMap("topLeft").getDouble("x"), points.getMap("topLeft").getDouble("y"));
        Point newRight = new Point(points.getMap("topRight").getDouble("x"), points.getMap("topRight").getDouble("y"));
        Point newBottomLeft = new Point(points.getMap("bottomLeft").getDouble("x"), points.getMap("bottomLeft").getDouble("y"));
        Point newBottomRight = new Point(points.getMap("bottomRight").getDouble("x"), points.getMap("bottomRight").getDouble("y"));

//        String cleanSource = imageUri.replace("file://", "");
//        cleanSource = cleanSource.replace("content://", "");
        String cleanSource  = this.getRealPathFromURI(imageUri);

        Mat src = Imgcodecs.imread(cleanSource, Imgproc.COLOR_BGR2RGB);
        Imgproc.cvtColor(src, src, Imgproc.COLOR_BGR2RGB);

        Point tl = pointMethod(newLeft, points.getDouble("width"), points.getDouble("height"), src.size().width, src.size().height);
        Point tr = pointMethod(newRight, points.getDouble("width"), points.getDouble("height"), src.size().width, src.size().height);
        Point bl = pointMethod(newBottomLeft, points.getDouble("width"), points.getDouble("height"), src.size().width, src.size().height);
        Point br = pointMethod(newBottomRight, points.getDouble("width"), points.getDouble("height"), src.size().width, src.size().height);

        // boolean ratioAlreadyApplied = tr.x * (src.size().width / 500) < src.size().width;
        // double ratio = ratioAlreadyApplied ? src.size().width / 500 : 1;

        // get the new width dimension
        double widthA = Math.sqrt(Math.pow(br.x - bl.x, 2) + Math.pow(br.y - bl.y, 2));
        double widthB = Math.sqrt(Math.pow(tr.x - tl.x, 2) + Math.pow(tr.y - tl.y, 2));

        double dw = Math.max(widthA, widthB);
        int maxWidth = Double.valueOf(dw).intValue();

        // get the new height dimension
        double heightA = Math.sqrt(Math.pow(tr.x - br.x, 2) + Math.pow(tr.y - br.y, 2));
        double heightB = Math.sqrt(Math.pow(tl.x - bl.x, 2) + Math.pow(tl.y - bl.y, 2));

        double dh = Math.max(heightA, heightB);
        int maxHeight = Double.valueOf(dh).intValue();

        Mat doc = new Mat(maxHeight, maxWidth, CvType.CV_8UC4);

        Mat src_mat = new Mat(4, 1, CvType.CV_32FC2);
        Mat dst_mat = new Mat(4, 1, CvType.CV_32FC2);

        src_mat.put(0, 0, tl.x, tl.y, tr.x, tr.y, br.x, br.y, bl.x, bl.y);
        dst_mat.put(0, 0, 0.0, 0.0, dw, 0.0, dw, dh, 0.0, dh);

        Mat m = Imgproc.getPerspectiveTransform(src_mat, dst_mat);

        Imgproc.warpPerspective(src, doc, m, doc.size());

        Bitmap bitmap = Bitmap.createBitmap(doc.cols(), doc.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(doc, bitmap);

        // ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        // bitmap.compress(Bitmap.CompressFormat.JPEG, 70, byteArrayOutputStream);
        // byte[] byteArray = byteArrayOutputStream.toByteArray();


        // Assume block needs to be inside a Try/Catch block.
        String path = Environment.getExternalStorageDirectory().toString();
        Long tsLong = System.currentTimeMillis() / 1000;
        String ts = tsLong.toString();
        String title = "rr-" + ts + ".jpg";
        File file = new File(path, title); // the File to save , append increasing numeric counter to prevent files from getting overwritten.

        String newImageUri = this.insertImage(this.getCurrentActivity().getContentResolver(),bitmap,title,title);

        WritableMap retVal = new WritableNativeMap();

        retVal.putString("image", newImageUri);

        callback.invoke(null, retVal);

//        fOut.flush();
//        fOut.close();

        m.release();
      } catch (Exception e) {
        callback.invoke(e.getMessage(), null);
        e.printStackTrace();
      }

    }
  }
}

