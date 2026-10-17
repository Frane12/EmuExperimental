/* DRNAS D3D9 cube diagnostic, MIT license, Copyright (c) 2026 Frane Drnas.
 * Independently implemented equivalent of Winlator's Start > Test Direct3D.
 * No D3DX, external assets, or software rasterizer. Uses the guest d3d9.dll.
 */
#define COBJMACROS
#include <windows.h>
#include <d3d9.h>
#include <math.h>
#include <stdio.h>

typedef struct { float x,y,z; DWORD color; } Vertex;
static const Vertex vertices[] = {
    {-1,-1,-1,0xffff3030},{-1,1,-1,0xff30ff30},
    {1,1,-1,0xff3030ff},{1,-1,-1,0xffffff30},
    {-1,-1,1,0xffff30ff},{-1,1,1,0xff30ffff},
    {1,1,1,0xffffffff},{1,-1,1,0xffff8030}
};
static const WORD indices[] = {0,1,2,0,2,3,4,6,5,4,7,6,0,4,5,0,5,1,
    3,2,6,3,6,7,1,5,6,1,6,2,0,3,7,0,7,4};
static LRESULT CALLBACK window_proc(HWND w,UINT msg,WPARAM wp,LPARAM lp) {
    if(msg==WM_KEYDOWN && wp==VK_ESCAPE) { DestroyWindow(w);return 0; }
    if(msg==WM_DESTROY) { PostQuitMessage(0);return 0; }
    return DefWindowProcA(w,msg,wp,lp);
}
static void failure(const char *stage,HRESULT hr) {
    char text[256];snprintf(text,sizeof(text),"%s failed: HRESULT 0x%08lx\nExport the DRNAS log and DXVK log.",stage,(unsigned long)hr);
    MessageBoxA(NULL,text,"DRNAS Direct3D diagnostic",MB_OK|MB_ICONERROR);
}
int WINAPI WinMain(HINSTANCE instance,HINSTANCE previous,LPSTR command,int show) {
    (void)previous;(void)command;
    WNDCLASSA wc={0};wc.lpfnWndProc=window_proc;wc.hInstance=instance;
    wc.lpszClassName="DrnasD3D9Cube";wc.hCursor=LoadCursor(NULL,IDC_ARROW);
    if(!RegisterClassA(&wc))return 1;
    DWORD style=WS_OVERLAPPED|WS_CAPTION|WS_SYSMENU|WS_MINIMIZEBOX;
    RECT rect={0,0,800,600};AdjustWindowRect(&rect,style,FALSE);
    HWND window=CreateWindowA(wc.lpszClassName,"DRNAS Direct3D 9 cube - initializing",style,
        80,40,rect.right-rect.left,rect.bottom-rect.top,NULL,NULL,instance,NULL);
    if(!window)return 2;
    IDirect3D9 *api=Direct3DCreate9(D3D_SDK_VERSION);
    if(!api){failure("Direct3DCreate9",E_FAIL);DestroyWindow(window);return 3;}
    D3DADAPTER_IDENTIFIER9 adapter={0};
    IDirect3D9_GetAdapterIdentifier(api,D3DADAPTER_DEFAULT,0,&adapter);
    D3DPRESENT_PARAMETERS pp={0};pp.Windowed=TRUE;pp.SwapEffect=D3DSWAPEFFECT_DISCARD;
    pp.hDeviceWindow=window;pp.BackBufferWidth=800;pp.BackBufferHeight=600;
    pp.BackBufferFormat=D3DFMT_UNKNOWN;pp.EnableAutoDepthStencil=TRUE;
    pp.AutoDepthStencilFormat=D3DFMT_D16;pp.PresentationInterval=D3DPRESENT_INTERVAL_ONE;
    IDirect3DDevice9 *device=NULL;
    HRESULT hr=IDirect3D9_CreateDevice(api,0,D3DDEVTYPE_HAL,window,
        D3DCREATE_HARDWARE_VERTEXPROCESSING,&pp,&device);
    if(FAILED(hr)){failure("D3D9 HAL device/swapchain",hr);IDirect3D9_Release(api);DestroyWindow(window);return 4;}
    IDirect3DDevice9_SetRenderState(device,D3DRS_LIGHTING,FALSE);
    IDirect3DDevice9_SetRenderState(device,D3DRS_CULLMODE,D3DCULL_NONE);
    IDirect3DDevice9_SetRenderState(device,D3DRS_ZENABLE,TRUE);
    IDirect3DDevice9_SetFVF(device,D3DFVF_XYZ|D3DFVF_DIFFUSE);
    D3DMATRIX view={0};view._11=view._22=view._33=view._44=1;view._43=6;
    D3DMATRIX projection={0};float y=1.0f/tanf(0.5f);
    projection._11=y/(800.0f/600.0f);projection._22=y;
    projection._33=100.0f/99.9f;projection._34=1;projection._43=-0.1f*100.0f/99.9f;
    IDirect3DDevice9_SetTransform(device,D3DTS_VIEW,&view);
    IDirect3DDevice9_SetTransform(device,D3DTS_PROJECTION,&projection);
    ShowWindow(window,show);UpdateWindow(window);
    DWORD start=GetTickCount(),tick=start;unsigned frames=0;MSG message={0};int result=0;
    while(message.message!=WM_QUIT) {
        if(PeekMessageA(&message,NULL,0,0,PM_REMOVE)){TranslateMessage(&message);DispatchMessageA(&message);continue;}
        if(IsIconic(window)){Sleep(25);continue;}
        float a=(GetTickCount()-start)*0.0007f,c=cosf(a),s=sinf(a);
        D3DMATRIX world={0};world._11=c;world._13=-s;world._21=s*s;
        world._22=c;world._23=s*c;world._31=c*s;world._32=-s;world._33=c*c;world._44=1;
        IDirect3DDevice9_SetTransform(device,D3DTS_WORLD,&world);
        hr=IDirect3DDevice9_Clear(device,0,NULL,D3DCLEAR_TARGET|D3DCLEAR_ZBUFFER,0xff101b2c,1,0);
        if(SUCCEEDED(hr))hr=IDirect3DDevice9_BeginScene(device);
        if(SUCCEEDED(hr)){
            hr=IDirect3DDevice9_DrawIndexedPrimitiveUP(device,D3DPT_TRIANGLELIST,0,8,12,
                indices,D3DFMT_INDEX16,vertices,sizeof(Vertex));
            HRESULT end=IDirect3DDevice9_EndScene(device);if(SUCCEEDED(hr))hr=end;
        }
        if(SUCCEEDED(hr))hr=IDirect3DDevice9_Present(device,NULL,NULL,NULL,NULL);
        if(FAILED(hr)){failure("Render/Present",hr);result=5;break;}
        frames++;DWORD now=GetTickCount();
        if(now-tick>=1000){char title[512];snprintf(title,sizeof(title),
            "DRNAS D3D9 cube | %.1f FPS | %s | Esc closes",frames*1000.0/(now-tick),adapter.Description);
            SetWindowTextA(window,title);tick=now;frames=0;}
    }
    IDirect3DDevice9_Release(device);IDirect3D9_Release(api);
    if(IsWindow(window))DestroyWindow(window);
    return result;
}
