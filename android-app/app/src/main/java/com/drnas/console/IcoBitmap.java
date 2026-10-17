package com.drnas.console;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.*;

final class IcoBitmap {
    static Bitmap read(InputStream input) throws IOException {
        if(input==null)throw new IOException("Ikona se ne može otvoriti");
        byte[] data;
        try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buf=new byte[8192];int n;
            while((n=in.read(buf))!=-1){
                if(out.size()+n>IcoImage.MAX_BYTES)throw new IOException("ICO je veći od 8 MiB");
                out.write(buf,0,n);
            }data=out.toByteArray();
        }
        for(IcoImage.Frame frame:IcoImage.read(data)){
            Bitmap bitmap;
            if(frame.png!=null){
                BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
                BitmapFactory.decodeByteArray(frame.png,0,frame.png.length,bounds);
                if(bounds.outWidth<1||bounds.outWidth>512||bounds.outHeight<1||bounds.outHeight>512)continue;
                bitmap=BitmapFactory.decodeByteArray(frame.png,0,frame.png.length);
            }else bitmap=Bitmap.createBitmap(frame.argb,frame.width,frame.height,Bitmap.Config.ARGB_8888);
            if(bitmap==null)continue;
            int largest=Math.max(bitmap.getWidth(),bitmap.getHeight());
            if(largest>128){
                Bitmap thumb=Bitmap.createScaledBitmap(bitmap,Math.max(1,bitmap.getWidth()*128/largest),
                        Math.max(1,bitmap.getHeight()*128/largest),true);
                if(thumb!=bitmap)bitmap.recycle();bitmap=thumb;
            }
            return bitmap;
        }
        throw new IOException("ICO slika se ne može prikazati");
    }
    private IcoBitmap(){}
}
