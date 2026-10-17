package com.drnas.console;

import java.io.*;
import java.util.*;

/** Bounded ICO container reader: PNG frames and uncompressed Windows DIB frames. */
final class IcoImage {
    static final int MAX_BYTES=8*1024*1024;
    static final class Frame {
        final int width,height;
        final byte[] png;
        final int[] argb;
        Frame(int w,int h,byte[] png,int[] argb){width=w;height=h;this.png=png;this.argb=argb;}
    }
    private static int u16(byte[] d,int p){return (d[p]&255)|((d[p+1]&255)<<8);}
    private static long u32(byte[] d,int p){return (long)(d[p]&255)|((long)(d[p+1]&255)<<8)|((long)(d[p+2]&255)<<16)|((long)(d[p+3]&255)<<24);}
    static List<Frame> read(byte[] data) throws IOException {
        if(data==null||data.length<22||data.length>MAX_BYTES||u16(data,0)!=0||u16(data,2)!=1)
            throw new IOException("Datoteka nije podržana ICO ikona");
        int count=u16(data,4);
        if(count<1||count>256||6L+16L*count>data.length)throw new IOException("Neispravno ICO zaglavlje");
        List<Frame> result=new ArrayList<>();
        // Decode largest candidates first; limit the total decoded image budget.
        List<Integer> order=new ArrayList<>();for(int i=0;i<count;i++)order.add(i);
        order.sort((a,b)->Integer.compare(area(data,6+b*16),area(data,6+a*16)));
        for(int index:order) {
            int pos=6+index*16,w=data[pos]&255,h=data[pos+1]&255;
            if(w==0)w=256;if(h==0)h=256;
            long length=u32(data,pos+8),offset=u32(data,pos+12);
            if(length<8||offset<6L+count*16L||offset>data.length||length>data.length-offset)continue;
            byte[] frame=Arrays.copyOfRange(data,(int)offset,(int)(offset+length));
            try{
                if(frame[0]==(byte)137&&frame[1]==80&&frame[2]==78&&frame[3]==71&&
                        frame[4]==13&&frame[5]==10&&frame[6]==26&&frame[7]==10)
                    result.add(new Frame(w,h,frame,null));
                else result.add(dib(frame,w,h));
            }catch(IOException ignored){}
            if(result.size()>=4)break;
        }
        if(result.isEmpty())throw new IOException("ICO nema podržanu PNG ili bitmap sliku");
        return result;
    }
    private static int area(byte[] d,int p){int w=d[p]&255,h=d[p+1]&255;return (w==0?256:w)*(h==0?256:h);}
    private static Frame dib(byte[] d,int width,int height) throws IOException {
        if(d.length<40)throw new IOException("Kratak bitmap zapis");
        long header=u32(d,0);int w=(int)u32(d,4),rawHeight=(int)u32(d,8);
        int bpp=u16(d,14);
        if(header<40||header>d.length||w!=width||
                (Math.abs((long)rawHeight)!=height*2L&&Math.abs((long)rawHeight)!=height)||
                u16(d,12)!=1||u32(d,16)!=0||
                !(bpp==1||bpp==4||bpp==8||bpp==16||bpp==24||bpp==32))
            throw new IOException("Nepodržan bitmap zapis");
        int paletteSize=0;
        if(bpp<=8){long used=u32(d,32);paletteSize=used==0?1<<bpp:(int)used;
            if(paletteSize<1||paletteSize>(1<<bpp))throw new IOException("Neispravna paleta");}
        long start=header+paletteSize*4L;
        int stride=((width*bpp+31)/32)*4,maskStride=((width+31)/32)*4;
        long mask=start+(long)stride*height;
        if(mask>d.length)throw new IOException("Nedostaju bitmap podaci");
        boolean hasMask=mask+(long)maskStride*height<=d.length;
        int[] pixels=new int[width*height];boolean anyAlpha=false;
        for(int y=0;y<height;y++){
            int row=rawHeight<0?y:height-1-y;
            int base=(int)start+row*stride;
            for(int x=0;x<width;x++){
                int r,g,b,a=255,p;
                if(bpp<=8){
                    int index=bpp==8?d[base+x]&255:bpp==4?
                            ((d[base+x/2]&255)>>(x%2==0?4:0))&15:
                            ((d[base+x/8]&255)>>(7-x%8))&1;
                    if(index>=paletteSize)throw new IOException("Neispravna boja palete");
                    p=(int)header+index*4;b=d[p]&255;g=d[p+1]&255;r=d[p+2]&255;
                }else if(bpp==16){
                    int value=u16(d,base+x*2);
                    r=((value>>10)&31)*255/31;g=((value>>5)&31)*255/31;b=(value&31)*255/31;
                }else{
                    p=base+x*(bpp/8);b=d[p]&255;g=d[p+1]&255;r=d[p+2]&255;
                    if(bpp==32){a=d[p+3]&255;anyAlpha|=a!=0;}
                }
                pixels[y*width+x]=(a<<24)|(r<<16)|(g<<8)|b;
            }
        }
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){
            int p=y*width+x;
            if(bpp==32&&!anyAlpha)pixels[p]|=0xff000000;
            int row=rawHeight<0?y:height-1-y;
            if(hasMask&&((d[(int)mask+row*maskStride+x/8]>>(7-x%8))&1)!=0)
                pixels[p]&=0x00ffffff;
        }
        return new Frame(width,height,null,pixels);
    }
    private IcoImage(){}
}
