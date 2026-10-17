package com.drnas.console;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;

/** Localizes the Android dialog shell while leaving editable user content intact. */
final class AppDialogBuilder extends AlertDialog.Builder {
    private final Context context;
    AppDialogBuilder(Context context){super(context);this.context=context;}
    @Override public AppDialogBuilder setTitle(CharSequence value){super.setTitle(AppLanguage.t(context,value));return this;}
    @Override public AppDialogBuilder setMessage(CharSequence value){super.setMessage(AppLanguage.t(context,value));return this;}
    @Override public AppDialogBuilder setPositiveButton(CharSequence text,DialogInterface.OnClickListener action){super.setPositiveButton(AppLanguage.t(context,text),action);return this;}
    @Override public AppDialogBuilder setNegativeButton(CharSequence text,DialogInterface.OnClickListener action){super.setNegativeButton(AppLanguage.t(context,text),action);return this;}
    @Override public AppDialogBuilder setNeutralButton(CharSequence text,DialogInterface.OnClickListener action){super.setNeutralButton(AppLanguage.t(context,text),action);return this;}
    @Override public AppDialogBuilder setItems(CharSequence[] items,DialogInterface.OnClickListener action){
        String[] translated=new String[items.length];for(int i=0;i<items.length;i++)translated[i]=AppLanguage.t(context,items[i]);
        super.setItems(translated,action);return this;
    }
    @Override public AppDialogBuilder setView(View view){AppLanguage.tree(view);super.setView(view);return this;}
}
