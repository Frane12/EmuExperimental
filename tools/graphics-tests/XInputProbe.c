/* Manual Wine GUI probe for the source-built DRNAS XInput driver. */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdio.h>
#include <xinput.h>
typedef DWORD (WINAPI *GetStateFn)(DWORD,XINPUT_STATE*);

int main(void) {
    char report[512];
    HMODULE dll=LoadLibraryA("xinput1_3.dll");
    GetStateFn get_state=NULL;
    XINPUT_STATE state;
    DWORD status=ERROR_MOD_NOT_FOUND;
    ZeroMemory(&state,sizeof(state));
    if(dll) {
        get_state=(GetStateFn)GetProcAddress(dll,"XInputGetState");
        if(get_state) status=get_state(0,&state);
        else status=ERROR_PROC_NOT_FOUND;
    }
    snprintf(report,sizeof(report),
             "XInputGetState(0): %lu\n\n"
             "0 = Controller connected\n"
             "1167 = Controller disconnected\n"
             "126/127 = DLL missing or export not found\n\n"
             "Packet: %lu\nButtons: 0x%04X\n"
             "LT: %u    RT: %u\n"
             "LX: %d   LY: %d\nRX: %d   RY: %d\n\n"
             "Turn on ⋮ > Xbox XInput before launching this test.",
             status,state.dwPacketNumber,state.Gamepad.wButtons,
             state.Gamepad.bLeftTrigger,state.Gamepad.bRightTrigger,
             state.Gamepad.sThumbLX,state.Gamepad.sThumbLY,
             state.Gamepad.sThumbRX,state.Gamepad.sThumbRY);
    puts(report);
    MessageBoxA(NULL,report,"DRNAS XInput Controller Test",
                MB_OK|(status==0?MB_ICONINFORMATION:MB_ICONWARNING));
    if(dll)FreeLibrary(dll);
    return status==0?0:3;
}
