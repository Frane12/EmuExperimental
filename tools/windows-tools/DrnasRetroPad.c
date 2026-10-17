#define UNICODE
#define _UNICODE
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <commdlg.h>
#include <stdlib.h>
#include <string.h>

/* DRNAS RetroPad: native Win32 source-owned UTF-8 text editor for Wine.
 * Not an RTF/WordPad clone. Uses the system edit control, no binary downloads. */
enum { CMD_NEW=101,CMD_OPEN,CMD_SAVE,CMD_SAVEAS,CMD_EXIT,
       CMD_UNDO,CMD_CUT,CMD_COPY,CMD_PASTE,CMD_SELECTALL };
static HWND edit;
static wchar_t activeFile[MAX_PATH];
static HFONT font;

static void title(HWND hwnd) {
    wchar_t caption[MAX_PATH+60];
    wsprintfW(caption,L"DRNAS RetroPad — %s",
              activeFile[0]?activeFile:L"Untitled.txt");
    SetWindowTextW(hwnd,caption);
}
static int chooseFile(HWND hwnd,int save) {
    OPENFILENAMEW of;ZeroMemory(&of,sizeof(of));
    of.lStructSize=sizeof(of);of.hwndOwner=hwnd;
    of.lpstrFilter=L"Text files (*.txt)\0*.txt\0All files\0*.*\0";
    of.lpstrFile=activeFile;of.nMaxFile=MAX_PATH;of.lpstrDefExt=L"txt";
    of.Flags=OFN_PATHMUSTEXIST | (save?OFN_OVERWRITEPROMPT:OFN_FILEMUSTEXIST);
    return save?GetSaveFileNameW(&of):GetOpenFileNameW(&of);
}
static int saveCurrent(HWND hwnd,int saveAs) {
    if((!activeFile[0]||saveAs)&&!chooseFile(hwnd,1))return 0;
    int len=GetWindowTextLengthW(edit);
    if(len<0||len>2*1024*1024)return 0;
    wchar_t *wide=(wchar_t*)calloc((size_t)len+1,sizeof(wchar_t));
    if(!wide)return 0;
    GetWindowTextW(edit,wide,len+1);
    int bytes=WideCharToMultiByte(CP_UTF8,0,wide,-1,NULL,0,NULL,NULL);
    char *utf8=(char*)malloc((size_t)bytes);
    if(bytes<=0||!utf8){free(wide);return 0;}
    WideCharToMultiByte(CP_UTF8,0,wide,-1,utf8,bytes,NULL,NULL);
    HANDLE f=CreateFileW(activeFile,GENERIC_WRITE,0,NULL,CREATE_ALWAYS,FILE_ATTRIBUTE_NORMAL,NULL);
    int ok=0;
    if(f!=INVALID_HANDLE_VALUE) {
        DWORD written=0;
        ok=WriteFile(f,utf8,(DWORD)(bytes-1),&written,NULL) && written==(DWORD)(bytes-1);
        CloseHandle(f);
    }
    free(wide);free(utf8);
    if(!ok)MessageBoxW(hwnd,L"Cannot save UTF-8 file",L"DRNAS RetroPad",MB_ICONERROR);
    if(ok)title(hwnd);
    return ok;
}
static void openCurrent(HWND hwnd) {
    if(!chooseFile(hwnd,0))return;
    HANDLE f=CreateFileW(activeFile,GENERIC_READ,FILE_SHARE_READ,NULL,OPEN_EXISTING,FILE_ATTRIBUTE_NORMAL,NULL);
    if(f==INVALID_HANDLE_VALUE){MessageBoxW(hwnd,L"Cannot open file",L"DRNAS RetroPad",MB_ICONERROR);return;}
    DWORD size=GetFileSize(f,NULL);
    if(size==INVALID_FILE_SIZE||size>2*1024*1024) {
        CloseHandle(f);MessageBoxW(hwnd,L"Text file too large (2 MiB limit)",L"DRNAS RetroPad",MB_ICONERROR);return;
    }
    char *data=(char*)malloc((size_t)size+1);
    if(!data){CloseHandle(f);return;}
    DWORD read=0;BOOL ok=ReadFile(f,data,size,&read,NULL);CloseHandle(f);
    if(!ok||read!=size){free(data);return;}
    data[size]=0;
    const char *str=data;int count=(int)size;
    if(size>=3&&(unsigned char)data[0]==0xEF&&
            (unsigned char)data[1]==0xBB&&(unsigned char)data[2]==0xBF) {
        str+=3;count-=3;
    }
    int codepage=CP_UTF8;
    int wcharCount=MultiByteToWideChar(codepage,MB_ERR_INVALID_CHARS,str,count,NULL,0);
    if(!wcharCount&&count) {
        codepage=CP_ACP;
        wcharCount=MultiByteToWideChar(codepage,0,str,count,NULL,0);
    }
    wchar_t *wide=(wchar_t*)calloc((size_t)wcharCount+1,sizeof(wchar_t));
    if(wide) {
        if(count)MultiByteToWideChar(codepage,0,str,count,wide,wcharCount);
        SetWindowTextW(edit,wide);
        SendMessageW(edit,EM_SETMODIFY,FALSE,0);
        free(wide);title(hwnd);
    }
    free(data);
}
static HMENU menus(void) {
    HMENU main=CreateMenu(),file=CreatePopupMenu(),tools=CreatePopupMenu();
    AppendMenuW(file,MF_STRING,CMD_NEW,L"&New");
    AppendMenuW(file,MF_STRING,CMD_OPEN,L"&Open...");
    AppendMenuW(file,MF_STRING,CMD_SAVE,L"&Save");
    AppendMenuW(file,MF_STRING,CMD_SAVEAS,L"Save &As...");
    AppendMenuW(file,MF_SEPARATOR,0,NULL);
    AppendMenuW(file,MF_STRING,CMD_EXIT,L"E&xit");
    AppendMenuW(tools,MF_STRING,CMD_UNDO,L"&Undo");
    AppendMenuW(tools,MF_STRING,CMD_CUT,L"Cu&t");
    AppendMenuW(tools,MF_STRING,CMD_COPY,L"&Copy");
    AppendMenuW(tools,MF_STRING,CMD_PASTE,L"&Paste");
    AppendMenuW(tools,MF_STRING,CMD_SELECTALL,L"Select &All");
    AppendMenuW(main,MF_POPUP,(UINT_PTR)file,L"&File");
    AppendMenuW(main,MF_POPUP,(UINT_PTR)tools,L"&Edit");
    return main;
}
static LRESULT CALLBACK proc(HWND hwnd,UINT msg,WPARAM w,LPARAM l) {
    switch(msg) {
        case WM_CREATE:
            edit=CreateWindowExW(WS_EX_CLIENTEDGE,L"EDIT",L"",
                    WS_CHILD|WS_VISIBLE|WS_VSCROLL|WS_HSCROLL|
                    ES_LEFT|ES_MULTILINE|ES_AUTOVSCROLL|ES_AUTOHSCROLL|ES_WANTRETURN,
                    0,0,800,500,hwnd,(HMENU)1,
                    (HINSTANCE)GetWindowLongPtrW(hwnd,GWLP_HINSTANCE),NULL);
            if(!edit)return -1;
            font=CreateFontW(-17,0,0,0,FW_NORMAL,0,0,0,DEFAULT_CHARSET,
                    OUT_DEFAULT_PRECIS,CLIP_DEFAULT_PRECIS,CLEARTYPE_QUALITY,
                    FIXED_PITCH,L"Consolas");
            SendMessageW(edit,WM_SETFONT,(WPARAM)font,TRUE);
            SetMenu(hwnd,menus());title(hwnd);
            return 0;
        case WM_SIZE:
            if(edit)MoveWindow(edit,0,0,LOWORD(l),HIWORD(l),TRUE);
            return 0;
        case WM_SETFOCUS:if(edit)SetFocus(edit);return 0;
        case WM_COMMAND:
            if(LOWORD(w)==1)return 0;
            switch(LOWORD(w)) {
                case CMD_NEW:activeFile[0]=0;SetWindowTextW(edit,L"");title(hwnd);break;
                case CMD_OPEN:openCurrent(hwnd);break;
                case CMD_SAVE:saveCurrent(hwnd,0);break;
                case CMD_SAVEAS:saveCurrent(hwnd,1);break;
                case CMD_EXIT:DestroyWindow(hwnd);break;
                case CMD_UNDO:SendMessageW(edit,WM_UNDO,0,0);break;
                case CMD_CUT:SendMessageW(edit,WM_CUT,0,0);break;
                case CMD_COPY:SendMessageW(edit,WM_COPY,0,0);break;
                case CMD_PASTE:SendMessageW(edit,WM_PASTE,0,0);break;
                case CMD_SELECTALL:SendMessageW(edit,EM_SETSEL,0,-1);break;
            }return 0;
        case WM_DESTROY:if(font)DeleteObject(font);PostQuitMessage(0);return 0;
    }
    return DefWindowProcW(hwnd,msg,w,l);
}
int WINAPI wWinMain(HINSTANCE h,HINSTANCE prev,LPWSTR command,int show) {
    (void)prev;(void)command;
    WNDCLASSW wc;ZeroMemory(&wc,sizeof(wc));wc.lpfnWndProc=proc;
    wc.hInstance=h;wc.lpszClassName=L"DRNAS_RetroPad";
    wc.hCursor=LoadCursor(NULL,IDC_IBEAM);
    wc.hbrBackground=(HBRUSH)(COLOR_WINDOW+1);
    if(!RegisterClassW(&wc))return 1;
    HWND hwnd=CreateWindowW(wc.lpszClassName,L"DRNAS RetroPad",
            WS_OVERLAPPEDWINDOW|WS_VISIBLE,CW_USEDEFAULT,CW_USEDEFAULT,
            920,660,NULL,NULL,h,NULL);
    if(!hwnd)return 2;
    ShowWindow(hwnd,show);
    MSG msg;
    while(GetMessageW(&msg,NULL,0,0)>0){TranslateMessage(&msg);DispatchMessageW(&msg);}
    return (int)msg.wParam;
}
