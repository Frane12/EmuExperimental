#define UNICODE
#define _UNICODE
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <windowsx.h>
#include <commdlg.h>
#include <stdlib.h>
#include <string.h>

/* DRNAS Retro Paint: small original Win32/GDI guest program.
   No dependency on Microsoft Paint or third-party executables. */
enum { W=1024, H=640, BAR=43, CMD_NEW=100, CMD_SAVE, CMD_BLACK, CMD_RED,
       CMD_BLUE, CMD_GREEN, CMD_ERASER, CMD_THICK };
static HDC canvas;
static HBITMAP bitmap,oldBitmap;
static COLORREF ink=RGB(12,31,51);
static int brush=3,painting=0,lastX,lastY;
static HWND root;

static void paintWhite(void) {
    RECT r={0,0,W,H};HBRUSH b=CreateSolidBrush(RGB(255,255,255));
    FillRect(canvas,&r,b);DeleteObject(b);
}
static void stroke(int x,int y) {
    HPEN pen=CreatePen(PS_SOLID,brush,ink);
    HGDIOBJ old=SelectObject(canvas,pen);
    MoveToEx(canvas,lastX,lastY,NULL);LineTo(canvas,x,y);
    SelectObject(canvas,old);DeleteObject(pen);
    lastX=x;lastY=y;
    InvalidateRect(root,NULL,FALSE);
}
static void saveBmp(HWND hwnd) {
    wchar_t file[MAX_PATH]=L"DRNAS Painting.bmp";
    OPENFILENAMEW of;ZeroMemory(&of,sizeof(of));of.lStructSize=sizeof(of);
    of.hwndOwner=hwnd;of.lpstrFilter=L"Bitmap image (*.bmp)\0*.bmp\0All files\0*.*\0";
    of.lpstrFile=file;of.nMaxFile=MAX_PATH;of.lpstrDefExt=L"bmp";
    of.Flags=OFN_OVERWRITEPROMPT|OFN_PATHMUSTEXIST;
    if(!GetSaveFileNameW(&of))return;
    BITMAPINFO info;ZeroMemory(&info,sizeof(info));
    info.bmiHeader.biSize=sizeof(BITMAPINFOHEADER);
    info.bmiHeader.biWidth=W;info.bmiHeader.biHeight=H;
    info.bmiHeader.biPlanes=1;info.bmiHeader.biBitCount=24;
    info.bmiHeader.biCompression=BI_RGB;
    DWORD stride=((W*3+3)&~3);
    DWORD total=stride*H;
    BYTE *data=(BYTE*)malloc(total);
    if(!data){MessageBoxW(hwnd,L"Out of memory",L"DRNAS Retro Paint",MB_ICONERROR);return;}
    // GDI requires that GetDIBits use a deselected source bitmap.
    HBITMAP restore=(HBITMAP)SelectObject(canvas,oldBitmap);
    int rows=GetDIBits(canvas,bitmap,0,H,data,&info,DIB_RGB_COLORS);
    SelectObject(canvas,restore);
    if(rows!=H) {
        free(data);MessageBoxW(hwnd,L"Cannot read bitmap",L"DRNAS Retro Paint",MB_ICONERROR);return;
    }
    BITMAPFILEHEADER head;ZeroMemory(&head,sizeof(head));
    head.bfType=0x4D42;head.bfOffBits=sizeof(head)+sizeof(info.bmiHeader);
    head.bfSize=head.bfOffBits+total;
    HANDLE f=CreateFileW(file,GENERIC_WRITE,0,NULL,CREATE_ALWAYS,FILE_ATTRIBUTE_NORMAL,NULL);
    if(f==INVALID_HANDLE_VALUE) {
        free(data);MessageBoxW(hwnd,L"Cannot save file",L"DRNAS Retro Paint",MB_ICONERROR);return;
    }
    DWORD written=0;BOOL ok=WriteFile(f,&head,sizeof(head),&written,NULL) &&
          WriteFile(f,&info.bmiHeader,sizeof(info.bmiHeader),&written,NULL) &&
          WriteFile(f,data,total,&written,NULL);
    CloseHandle(f);free(data);
    if(!ok)MessageBoxW(hwnd,L"Cannot write BMP",L"DRNAS Retro Paint",MB_ICONERROR);
}
static void addButton(HWND hwnd,const wchar_t *title,int id,int x,int width) {
    CreateWindowW(L"BUTTON",title,WS_CHILD|WS_VISIBLE|BS_PUSHBUTTON,
                 x,5,width,32,hwnd,(HMENU)(INT_PTR)id,
                 (HINSTANCE)GetWindowLongPtrW(hwnd,GWLP_HINSTANCE),NULL);
}
static LRESULT CALLBACK wndProc(HWND hwnd,UINT msg,WPARAM w,LPARAM l) {
    switch(msg) {
        case WM_CREATE: {
            HDC dc=GetDC(hwnd);canvas=CreateCompatibleDC(dc);
            bitmap=CreateCompatibleBitmap(dc,W,H);
            ReleaseDC(hwnd,dc);
            if(!canvas||!bitmap)return -1;
            oldBitmap=(HBITMAP)SelectObject(canvas,bitmap);
            paintWhite();
            addButton(hwnd,L"New",CMD_NEW,5,63);
            addButton(hwnd,L"Save BMP",CMD_SAVE,72,99);
            addButton(hwnd,L"Black",CMD_BLACK,176,66);
            addButton(hwnd,L"Red",CMD_RED,247,55);
            addButton(hwnd,L"Blue",CMD_BLUE,307,62);
            addButton(hwnd,L"Green",CMD_GREEN,374,65);
            addButton(hwnd,L"Eraser",CMD_ERASER,444,78);
            addButton(hwnd,L"Brush +/-",CMD_THICK,527,95);
            return 0;
        }
        case WM_COMMAND:
            switch(LOWORD(w)) {
                case CMD_NEW: paintWhite();InvalidateRect(hwnd,NULL,TRUE);break;
                case CMD_SAVE:saveBmp(hwnd);break;
                case CMD_BLACK:ink=RGB(12,31,51);break;
                case CMD_RED:ink=RGB(220,65,72);break;
                case CMD_BLUE:ink=RGB(27,109,213);break;
                case CMD_GREEN:ink=RGB(20,167,103);break;
                case CMD_ERASER:ink=RGB(255,255,255);break;
                case CMD_THICK:brush=brush==3?7:brush==7?13:3;break;
            }
            return 0;
        case WM_LBUTTONDOWN: {
            int x=GET_X_LPARAM(l),y=GET_Y_LPARAM(l)-BAR;
            if(x>=0&&x<W&&y>=0&&y<H) {
                painting=1;lastX=x;lastY=y;SetCapture(hwnd);
                stroke(x+1,y+1);
            }return 0;
        }
        case WM_MOUSEMOVE:
            if(painting) {
                int x=GET_X_LPARAM(l),y=GET_Y_LPARAM(l)-BAR;
                if(x<0)x=0;if(x>=W)x=W-1;
                if(y<0)y=0;if(y>=H)y=H-1;
                stroke(x,y);
            }return 0;
        case WM_LBUTTONUP:case WM_CAPTURECHANGED:
            if(painting){painting=0;if(GetCapture()==hwnd)ReleaseCapture();}
            return 0;
        case WM_PAINT: {
            PAINTSTRUCT ps;HDC dc=BeginPaint(hwnd,&ps);
            RECT client;GetClientRect(hwnd,&client);
            RECT area={0,BAR,client.right,client.bottom};
            HBRUSH bg=CreateSolidBrush(RGB(235,239,246));FillRect(dc,&area,bg);DeleteObject(bg);
            BitBlt(dc,0,BAR,W,H,canvas,0,0,SRCCOPY);
            EndPaint(hwnd,&ps);return 0;
        }
        case WM_DESTROY:
            if(canvas){SelectObject(canvas,oldBitmap);DeleteObject(bitmap);DeleteDC(canvas);}
            PostQuitMessage(0);return 0;
    }
    return DefWindowProcW(hwnd,msg,w,l);
}
int WINAPI wWinMain(HINSTANCE h,HINSTANCE prev,LPWSTR cmd,int show) {
    (void)prev;(void)cmd;
    WNDCLASSW wc;ZeroMemory(&wc,sizeof(wc));wc.lpfnWndProc=wndProc;
    wc.hInstance=h;wc.lpszClassName=L"DRNAS_RetroPaint";
    wc.hCursor=LoadCursor(NULL,IDC_CROSS);
    wc.hbrBackground=(HBRUSH)(COLOR_BTNFACE+1);
    if(!RegisterClassW(&wc))return 1;
    root=CreateWindowW(wc.lpszClassName,L"DRNAS Retro Paint — original Wine guest tool",
            WS_OVERLAPPEDWINDOW|WS_VISIBLE,CW_USEDEFAULT,CW_USEDEFAULT,
            1000,730,NULL,NULL,h,NULL);
    if(!root)return 2;
    ShowWindow(root,show);
    MSG msg;while(GetMessageW(&msg,NULL,0,0)>0){TranslateMessage(&msg);DispatchMessageW(&msg);}
    return (int)msg.wParam;
}
