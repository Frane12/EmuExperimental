package com.drnas.console;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Tiny original retro desktop icons; painted on Android without external packs. */
final class RetroIconView extends View {
    private final String id;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    RetroIconView(Context ctx,String id){super(ctx);this.id=id;}
    private void fill(int c){p.setColor(c);p.setStyle(Paint.Style.FILL);p.setStrokeWidth(1);}
    private void box(Canvas c,float a,float b,float x,float y,float radius,int color){
        fill(color);c.drawRoundRect(new RectF(a,b,x,y),radius,radius,p);
    }
    private void circle(Canvas c,float x,float y,float r,int color){
        fill(color);c.drawCircle(x,y,r,p);
    }
    private void line(Canvas c,float x,float y,float x2,float y2,int color,float width){
        fill(color);p.setStrokeWidth(width);c.drawLine(x,y,x2,y2,p);
    }
    @Override protected void onDraw(Canvas target) {
        super.onDraw(target);
        int save=target.save();
        target.scale(getWidth()/64f,getHeight()/64f);
        String key=FreeCadPreview.ID.equals(id)?"freecad":
                id.startsWith("builtin:")?id.substring(8):id.startsWith("user:")?"game":"link";
        switch(key) {
            case "freecad":
                // Small original CAD wireframe logo, drawn locally without image assets.
                box(target,4,4,60,60,12,0xFF132D4B);
                box(target,7,7,57,57,10,0xFF438AC6);
                box(target,10,10,54,54,8,0xFF173855);
                line(target,17,26,31,18,0xFFB9F9FF,3);
                line(target,31,18,47,26,0xFFB9F9FF,3);
                line(target,47,26,32,35,0xFFB9F9FF,3);
                line(target,32,35,17,26,0xFFB9F9FF,3);
                line(target,17,26,17,43,0xFF63E7DA,3);
                line(target,17,43,32,52,0xFF63E7DA,3);
                line(target,32,52,47,43,0xFF63E7DA,3);
                line(target,47,43,47,26,0xFF63E7DA,3);
                line(target,32,35,32,52,0xFFE9FFFF,3);
                circle(target,31,18,2,0xFFFFBD64);
                circle(target,47,43,2,0xFFFFBD64);
                break;
            case "game":
                box(target,5,19,59,50,12,0xFF67E7C5);
                box(target,8,22,56,47,10,0xFF193C55);
                line(target,17,34,31,34,0xFFA8FFE3,4);
                line(target,24,27,24,41,0xFFA8FFE3,4);
                circle(target,42,30,3,0xFF71D5FF);
                circle(target,49,37,3,0xFFFF89A0);
                break;
            case "paint":
                circle(target,32,31,23,0xFFEBF5E5);
                circle(target,31,30,19,0xFFCEE7EA);
                circle(target,24,16,4,0xFFE85052);
                circle(target,38,18,4,0xFFF3BA35);
                circle(target,45,29,4,0xFF43C97E);
                circle(target,30,41,4,0xFF3293F4);
                circle(target,19,33,4,0xFF9462DC);
                line(target,38,45,55,18,0xFF765136,6);
                circle(target,55,17,4,0xFFCDD4DD);break;
            case "retropad":
            case "notepad":
            case "wordpad":
                box(target,13,5,49,58,4,0xFF6B91AA);
                box(target,16,7,46,54,3,0xFFF5F9ED);
                box(target,16,7,46,16,2,key.equals("wordpad")?0xFF3786D0:0xFF75D7B0);
                for(int y=24;y<52;y+=7)line(target,20,y,43,y,0xFF78A4A9,2);
                if(key.equals("retropad"))circle(target,48,45,8,0xFF3DDBCE);
                break;
            case "tasks":
                box(target,5,8,59,53,6,0xFFBBD3E5);
                box(target,9,12,55,49,4,0xFF152F52);
                for(int i=0;i<7;i++)
                    line(target,12+i*6,45,12+i*6,35-(i%3)*7,
                            i%2==0?0xFF63E0B4:0xFF50AAEE,3);
                break;
            case "cmd":
                box(target,5,9,59,54,4,0xFFABC8D7);
                box(target,9,14,55,50,2,0xFF132133);
                fill(0xFF6DE4CA);p.setTextSize(14);p.setFakeBoldText(true);
                target.drawText("C:\\>",13,36,p);p.setFakeBoldText(false);break;
            case "calc":
                box(target,13,5,51,59,6,0xFF80A5B6);
                box(target,17,9,47,24,2,0xFFEBFCE8);
                for(int r=0;r<3;r++)for(int k=0;k<3;k++)
                    box(target,18+k*10,30+r*9,26+k*10,37+r*9,2,
                            (r==2&&k==2)?0xFF59D1C8:0xFFE7EFF4);
                break;
            case "control":
            case "winecfg":
                circle(target,32,32,24,0xFF538AC4);
                circle(target,32,32,16,0xFFA6DBFF);
                circle(target,32,32,9,0xFF246393);
                circle(target,32,32,4,0xFFF3FFFF);break;
            case "files":
                box(target,6,19,58,50,5,0xFF4CA3D8);
                box(target,10,11,31,24,3,0xFF79D6EC);
                box(target,10,24,55,47,3,0xFF77C9E4);break;
            default:
                box(target,7,9,57,54,8,0xFF315F7A);
                box(target,11,13,53,50,7,0xFF1C3A56);
                fill(0xFF72F1C9);p.setTextSize(35);p.setFakeBoldText(true);
                target.drawText("↗",17,43,p);p.setFakeBoldText(false);
                break;
        }
        target.restoreToCount(save);
    }
}
