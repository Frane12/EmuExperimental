package com.drnas.console;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/** App-owned labels only. Never translates paths, user input, guest programs or logs. */
final class AppLanguage {
    private static final String[][] LABELS={
        {"Bora-blown pollen on the dashboard","Pelud na sučelju · bura"},
        {"Live desktop wallpaper","Živa pozadina desktopa"},
        {"Larger Wine taskbar","Veći Wine taskbar"},
        {"Initialization failed","Inicijalizacija nije uspjela"},
        {"SHARE LOG","PODIJELI LOG"},
        {"Keyboard","Tipkovnica"},
        {"Console","Početna"},
        {"File Explorer","Datoteke"},
        {"Components","Dijelovi"},
        {"Shortcuts","Prečaci"},
        {"Options","Opcije"},
        {"EARLY ACCESS","RANI PRISTUP"},
        {"◈  WINDOWS ENVIRONMENT","◈  WINDOWS OKRUŽENJE"},
        {"◈  GAME & APP SHORTCUTS","◈  IGRE I PROGRAMI"},
        {"ANDROID FILES  /  R:","ANDROID DATOTEKE  /  R:"},
        {"ALL  ›","SVI  ›"},
        {"Added from Wine","Wine prečaci"},
        {"Preparing Windows desktop…","Priprema Windows desktopa…"},
        {"CHOOSE FOLDER","ODABERI MAPU"},
        {"↻  REFRESH R:","↻  OSVJEŽI R:"},
        {"▤  SHARE LOG","▤  PODIJELI LOG"},
        {"STARTING…","POKRETANJE…"},
        {"Edit joystick layout / XML","Uredi raspored joysticka / XML"},
        {"Show FPS / RAM / battery","Prikaži FPS / RAM / bateriju"},
        {"Show five desktop keys","Prikaži pet tipki na desktopu"},
        {"A840 H2.9 automatic","A840 H2.9 automatski"},
        {"Controls on startup","Kontrole pri paljenju"},
        {"Disabled","Isključeno"},
        {"WASD joystick","WASD joystick"},
        {"Relative camera","Relativna kamera"},
        {"Camera sensitivity","Osjetljivost kamere"},
        {"Unlimited","Bez limita"},
        {"Stretch · full screen","Stretch · puni zaslon"},
        {"Compact home screen","Kompaktan početni ekran"},
        {"APP LANGUAGE","JEZIK APLIKACIJE"},
        {"DISPLAY AND PERFORMANCE","PRIKAZ I IZVEDBA"},
        {"JOYSTICK / XINPUT","JOYSTICK / XINPUT"},
        {"Language applies to the Android interface, not Windows programs or diagnostic logs.","Jezik se odnosi na Android sučelje, ne na Windows programe i dijagnostičke logove."},
        {"Global settings apply on the next desktop start. Per-icon settings take priority.","Globalne postavke vrijede pri idućem paljenju desktopa. Postavke pojedine ikone imaju prednost."},
        {"Diagnostics / logs","Dijagnostika / logovi"},
        {"Configuration","Konfiguracija"},
        {"Wine configuration","Wine postavke"},
        {"Cancel","Odustani"},
        {"Save","Spremi"},
        {"Add","Dodaj"},
        {"Accept","Prihvati"},
        {"Discard","Odbaci"},
        {"Close","Zatvori"},
        {"Create","Stvori"},
        {"Exit the app?","Izaći iz aplikacije?"},
        {"Close this screen and the Windows session?","Zatvoriti ovaj ekran i Windows sesiju?"},
        {"Press Back again to confirm exit","Pritisni još jednom za potvrdu izlaza"},
        {"Shortcut name","Naziv prečaca"},
        {"Add to app home","Dodaj na početni ekran"},
        {"Choose .ico","Odaberi .ico"},
        {"Default icon","Zadana ikona"},
        {"Optional .ico icon","Neobavezna .ico ikona"},
        {"C:  WINE","C:  WINE"},
        {"R:  SELECTED FOLDER","R:  ODABRANA MAPA"},
        {"↑  UP","↑  GORE"},
        {"＋  NEW FOLDER","＋  NOVA MAPA"},
        {"COPY","KOPIRAJ"},
        {"PASTE HERE","ZALIJEPI OVDJE"},
        {"Choose Android folder","Odaberi Android mapu"},
        {"Import / refresh R:","Uvezi / osvježi R:"},
        {"Copy","Kopiraj"},
        {"New folder","Nova mapa"},
        {"Folder name","Naziv mape"},
        {"Empty folder","Prazna mapa"},
        {"Working…","Radim…"},
        {"Show 200 more","Prikaži još 200"},
        {"Select file → Copy → open destination → Paste.","Odaberi datoteku → Kopiraj → otvori odredište → Zalijepi."},
        {"R: is a private copy of the selected Android folder. Copying to R: does not change the original. Tap an .exe to add it to app home. Hold a folder to copy it.","R: je privatna kopija odabrane Android mape. Kopiranje u R: ne mijenja izvornu Android mapu. Dodir na .exe → Dodaj na početni ekran. Dugi dodir na mapu → Kopiraj."},
        {"ICON NAME","NAZIV IKONE"},
        {"DXVK FPS  ·  blank = global, 0 = unlimited","DXVK FPS  ·  prazno = globalno, 0 = bez limita"},
        {"DXVK VERSION","DXVK VERZIJA"},
        {"DISPLAY SCALING","PRILAGODBA ZASLONA"},
        {"CAMERA SENSITIVITY  ·  0.5×–10×","OSJETLJIVOST KAMERE  ·  0.5×–10×"},
        {"Global","Globalno"},
        {"Fit · keep aspect ratio","Fit · zadrži omjer"},
        {"Stretch · full screen","Stretch · cijeli zaslon"},
        {"Settings apply only to this icon on its next launch.","Postavke vrijede samo za ovu ikonu, od idućeg pokretanja."},
        {"Remove this shortcut","Ukloni ovaj prečac"},
        {"← Back","← Natrag"},
        {"Load XML","Učitaj XML"},
        {"💾 Save","💾 Spremi"},
        {"Save controller layout","Spremi raspored kontrolera"},
        {"Choose saved XML","Odaberi spremljeni XML"},
        {"XML saved and preset activated","XML spremljen i preset aktiviran"},
        {"No XML presets yet","Još nema XML preseta"},
        {"Keyboard","Tipkovnica"},
        {"Options","Opcije"},
        {"Task Manager","Upravitelj zadataka"},
        {"Send log","Pošalji log"},
        {"Save log","Spremi log"},
        {"Left mouse","Lijevi klik"},
        {"Right mouse","Desni klik"},
        {"Backspace","Backspace"},
        {"Exit desktop","Izađi iz desktopa"},
        {"Reset Wine","Reset Wine"},
        {"Turn Windows on and wait for Wine initialization first.","Prvo pokreni Windows i pričekaj Wine inicijalizaciju."},
        {"Turn on Windows first.","Prvo uključi Windows."},
        {"Windows is ready. Open Windows Explorer.","Windows je spreman. Otvori Explorer."},
        {"Your engine parts.","Komponente emulatora."},
        {"STAGE A COMPONENT ARCHIVE","UVEZI KOMPONENTU"},
        {"LOCAL VAULT","LOKALNE KOMPONENTE"},
        {"+ Import .wcp / Turnip .zip","+ Uvezi .wcp / Turnip .zip"},
        {"IMPORT DEPENDENCIES.ZIP (offline)","UVEZI DEPENDENCIES.ZIP (offline)"},
        {"RETRY FAILED DIRECTX / VC++ INSTALLS","PONOVI NEUSPJELE DIRECTX / VC++ INSTALACIJE"},
        {"＋  Add game / app (.exe)","＋  Dodaj igru / program (.exe)"},
        {"↻  Refresh Wine shortcuts","↻  Osvježi Wine prečace"},
        {"▣  Open Wine Explorer to install an app","▣  Otvori Wine Explorer za instalaciju programa"},
        {"DXVK  •  FPS LIMIT","DXVK  •  OGRANIČENJE FPS-a"},
        {"MANUAL INPUT  (0–240 FPS)","RUČNI UNOS  (0–240 FPS)"},
        {"✓  SAVE FOR NEXT RESET","✓  SPREMI ZA IDUĆI RESET"},
        {"▶  START WINDOWS","▶  POKRENI WINDOWS"},
        {"Keyboard","Tipkovnica"},
        {"Fullscreen / window (Alt+Enter)","Fullscreen / prozor (Alt+Enter)"},
        {"Switch window (Alt+Tab)","Promijeni prozor (Alt+Tab)"},
        {"Relative mouse / camera ON/OFF","Relativni miš / kamera ON/OFF"},
        {"Camera sensitivity","Osjetljivost kamere"},
        {"Edit XInput layout / XML","Uredi XInput raspored / XML"},
        {"Full screen / Stretch ON/OFF","Cijeli zaslon / Stretch ON/OFF"},
        {"DXVK FPS · next display reset","DXVK FPS · idući reset prikaza"},
        {"Virtual stick (WASD) ON/OFF","Virtual stick (WASD) ON/OFF"},
        {"Xbox XInput (virtual pad) ON/OFF","Xbox XInput (virtual pad) ON/OFF"}
    };
    static String normalize(String code){return "en".equals(code)?"en":"hr";}
    static String selected(Context c){return normalize(c.getSharedPreferences("drnas-ui",Context.MODE_PRIVATE).getString("language","hr"));}
    static void set(Context c,String code){c.getSharedPreferences("drnas-ui",Context.MODE_PRIVATE).edit().putString("language",normalize(code)).apply();}
    static String translate(String code,String text){
        if(text==null)return "";
        for(String[] pair:LABELS)if(text.equals(pair[0])||text.equals(pair[1]))return pair["en".equals(code)?0:1];
        int prefix=text.indexOf("  ");
        if(prefix>=0&&prefix<5){
            String tail=text.substring(prefix+2);
            for(String[] pair:LABELS)if(tail.equals(pair[0])||tail.equals(pair[1]))
                return text.substring(0,prefix+2)+pair["en".equals(code)?0:1];
        }
        return text;
    }
    static String t(Context c,CharSequence text){return translate(selected(c),text==null?"":text.toString());}
    static String[] array(Context c,String[] labels){
        String[] out=new String[labels.length];for(int i=0;i<labels.length;i++)out[i]=t(c,labels[i]);return out;
    }
    static void tree(View root){
        if(root instanceof TextView && !(root instanceof EditText) && !(root instanceof android.widget.TextClock)){
            TextView label=(TextView)root;
            if(!"user-text".equals(label.getTag()))label.setText(t(root.getContext(),label.getText()));
        }
        if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++)tree(group.getChildAt(i));}
    }
    static Toast toast(Context c,CharSequence message,int duration){return Toast.makeText(c,t(c,message),duration);}
    static Toast toast(Context c,int message,int duration){return Toast.makeText(c,message,duration);}
    private AppLanguage(){}
}
