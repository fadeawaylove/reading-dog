package com.example.yuejie;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** A single read-only APK, shared solely by a temporary installer URI grant. */
public class UpdateApkProvider extends ContentProvider {
    static Uri uri(Context c){return Uri.parse("content://"+c.getPackageName()+".updates/update.apk");}
    public boolean onCreate(){return true;}
    private File file(Uri uri)throws FileNotFoundException {
        if(!uri(getContext()).equals(uri))throw new FileNotFoundException("Unknown update file");
        File f=AppUpdater.apk(getContext());if(!f.isFile())throw new FileNotFoundException("Update no longer available");return f;
    }
    public String getType(Uri uri){return "application/vnd.android.package-archive";}
    public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException {
        if(!"r".equals(mode))throw new FileNotFoundException("Read only");
        return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
        try{File f=file(uri);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor cursor=new MatrixCursor(columns);Object[] values=new Object[columns.length];for(int i=0;i<columns.length;i++){if(OpenableColumns.DISPLAY_NAME.equals(columns[i]))values[i]="reading-dog-update.apk";else if(OpenableColumns.SIZE.equals(columns[i]))values[i]=f.length();}cursor.addRow(values);return cursor;}catch(FileNotFoundException e){throw new IllegalArgumentException(e);}
    }
    public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
    public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
