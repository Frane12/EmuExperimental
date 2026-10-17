/* Manual Wine guest verification: a real game-compatible DLL must load. */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdio.h>
#include <xinput.h>
typedef DWORD (WINAPI *GetStateFn)(DWORD,XINPUT_STATE*);
int main(void) {
    HMODULE dll=LoadLibraryA("xinput1_3.dll");
    GetStateFn get_state;
    XINPUT_STATE state;
    DWORD status;
    if(!dll){puts("DRNAS XInput: DLL load FAILED");return 1;}
    get_state=(GetStateFn)GetProcAddress(dll,"XInputGetState");
    if(!get_state){puts("DRNAS XInput: export missing");return 2;}
    ZeroMemory(&state,sizeof(state));
    status=get_state(0,&state);
    printf("DRNAS XInput: status=%lu (0=connected, 1167=disconnected)\\n",status);
    printf("packet=%lu buttons=0x%04x LT=%u RT=%u LX=%d LY=%d RX=%d RY=%d\\n",
           state.dwPacketNumber,state.Gamepad.wButtons,
           state.Gamepad.bLeftTrigger,state.Gamepad.bRightTrigger,
           state.Gamepad.sThumbLX,state.Gamepad.sThumbLY,
           state.Gamepad.sThumbRX,state.Gamepad.sThumbRY);
    FreeLibrary(dll);
    return status==ERROR_SUCCESS ? 0:3;
}
