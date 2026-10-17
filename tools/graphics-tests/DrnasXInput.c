/* DRNAS XInput virtual Xbox 360 controller — independent clean-room bridge.
 *
 * The Android application writes a 64-byte file mapped as shared memory.
 * This native Windows PE DLL maps that same file read-only in each Wine game.
 * Game polls take only shared-memory loads, no network, file IO, or scheduler
 * wait on the input hot path. A missing/stale state returns disconnected.
 *
 * Intended for xinput1_3, xinput1_4 and xinput9_1_0 through Wine overrides.
 * No Wine, Winlator or Microsoft sources have been copied.
 */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <xinput.h>
#include <stdint.h>
#include <string.h>

#ifndef ERROR_DEVICE_NOT_CONNECTED
#define ERROR_DEVICE_NOT_CONNECTED 1167L
#endif
#ifndef ERROR_EMPTY
#define ERROR_EMPTY 4306L
#endif
#ifndef BATTERY_TYPE_WIRED
#define BATTERY_TYPE_WIRED 0x01
#endif
#ifndef BATTERY_LEVEL_FULL
#define BATTERY_LEVEL_FULL 0x03
#endif

#define DRNAS_MAGIC 0x49585244u /* little-endian 'DRXI' */
#define DRNAS_VERSION 1u
#define DRNAS_STATE_SIZE 64

#pragma pack(push, 1)
typedef struct {
    uint32_t magic;           /* 0 */
    uint32_t version;         /* 4 */
    volatile uint32_t seq;    /* 8, odd while Android writes */
    uint16_t buttons;         /* 12 */
    uint8_t left_trigger;     /* 14 */
    uint8_t right_trigger;    /* 15 */
    int16_t lx, ly, rx, ry;   /* 16,18,20,22 */
    uint32_t connected;       /* 24 */
    uint32_t packet;          /* 28, increases when state changes */
    uint32_t reserved[8];     /* 32..63 */
} DrnasState;
#pragma pack(pop)

typedef char DrnasStateMustBe64[(sizeof(DrnasState) == DRNAS_STATE_SIZE) ? 1 : -1];

static HANDLE file_handle=INVALID_HANDLE_VALUE;
static HANDLE mapping_handle=NULL;
static volatile const DrnasState *state=NULL;
static DWORD last_attempt;
static volatile LONG xinput_enabled=1;
static INIT_ONCE init_once=INIT_ONCE_STATIC_INIT;
static CRITICAL_SECTION open_lock;

static BOOL CALLBACK init_lock(PINIT_ONCE once,PVOID parameter,PVOID *context) {
    (void)once;(void)parameter;(void)context;
    InitializeCriticalSection(&open_lock);
    return TRUE;
}

static const DrnasState *get_state_map(void) {
    DWORD now;
    if(state)return (const DrnasState*)state;
    now=GetTickCount();
    InitOnceExecuteOnce(&init_once,init_lock,NULL,NULL);
    EnterCriticalSection(&open_lock);
    if(!state && (last_attempt==0 || now-last_attempt>=250u)) {
        HANDLE file, map;
        DrnasState *view;
        last_attempt=now ? now : 1;
        file=CreateFileW(L"C:\\DRNAS\\Input\\xinput-state.bin",
                         GENERIC_READ,FILE_SHARE_READ|FILE_SHARE_WRITE,
                         NULL,OPEN_EXISTING,FILE_ATTRIBUTE_NORMAL,NULL);
        if(file!=INVALID_HANDLE_VALUE) {
            LARGE_INTEGER size;
            if(GetFileSizeEx(file,&size) && size.QuadPart>=DRNAS_STATE_SIZE) {
                map=CreateFileMappingW(file,NULL,PAGE_READONLY,0,0,NULL);
                if(map!=NULL) {
                    view=(DrnasState*)MapViewOfFile(map,FILE_MAP_READ,0,0,DRNAS_STATE_SIZE);
                    if(view!=NULL) {
                        file_handle=file;mapping_handle=map;state=view;
                    } else CloseHandle(map);
                }
            }
            if(!state)CloseHandle(file);
        }
    }
    LeaveCriticalSection(&open_lock);
    return (const DrnasState*)state;
}

/* Writer publishes odd sequence -> data -> even sequence. Return a stable
 * snapshot; never block on Android if the writer is interrupted. */
static BOOL read_state(DrnasState *out) {
    const DrnasState *shared=get_state_map();
    DWORD first,second;
    unsigned attempt;
    if(!shared || InterlockedCompareExchange(&xinput_enabled,1,1)==0)return FALSE;
    for(attempt=0;attempt<3;attempt++) {
        first=shared->seq;
        if(first & 1u)continue;
        MemoryBarrier();
        memcpy(out,shared,sizeof(*out));
        MemoryBarrier();
        second=shared->seq;
        if(first==second && !(second&1u) &&
           out->magic==DRNAS_MAGIC && out->version==DRNAS_VERSION &&
           out->connected!=0) return TRUE;
    }
    return FALSE;
}

DWORD WINAPI XInputGetState(DWORD index,XINPUT_STATE *result) {
    DrnasState s;
    if(!result)return ERROR_BAD_ARGUMENTS;
    if(index!=0 || !read_state(&s))return ERROR_DEVICE_NOT_CONNECTED;
    ZeroMemory(result,sizeof(*result));
    result->dwPacketNumber=s.packet;
    result->Gamepad.wButtons=s.buttons;
    result->Gamepad.bLeftTrigger=s.left_trigger;
    result->Gamepad.bRightTrigger=s.right_trigger;
    result->Gamepad.sThumbLX=s.lx;
    result->Gamepad.sThumbLY=s.ly;
    result->Gamepad.sThumbRX=s.rx;
    result->Gamepad.sThumbRY=s.ry;
    return ERROR_SUCCESS;
}

DWORD WINAPI XInputGetStateEx(DWORD index,XINPUT_STATE *state_out) {
    return XInputGetState(index,state_out);
}
DWORD WINAPI XInputSetState(DWORD index,XINPUT_VIBRATION *vibration) {
    DrnasState snapshot;
    if(!vibration)return ERROR_BAD_ARGUMENTS;
    if(index!=0 || !read_state(&snapshot))return ERROR_DEVICE_NOT_CONNECTED;
    /* Touch-only controller has no physical rumble output. */
    return ERROR_SUCCESS;
}
DWORD WINAPI XInputGetCapabilities(DWORD index,DWORD flags,XINPUT_CAPABILITIES *caps) {
    DrnasState snapshot;
    (void)flags;
    if(!caps)return ERROR_BAD_ARGUMENTS;
    if(index!=0 || !read_state(&snapshot))return ERROR_DEVICE_NOT_CONNECTED;
    ZeroMemory(caps,sizeof(*caps));
    caps->Type=XINPUT_DEVTYPE_GAMEPAD;
    caps->SubType=XINPUT_DEVSUBTYPE_GAMEPAD;
    caps->Gamepad.wButtons=0xffff;
    caps->Gamepad.bLeftTrigger=255;
    caps->Gamepad.bRightTrigger=255;
    caps->Gamepad.sThumbLX=32767;
    caps->Gamepad.sThumbLY=32767;
    caps->Gamepad.sThumbRX=32767;
    caps->Gamepad.sThumbRY=32767;
    return ERROR_SUCCESS;
}
void WINAPI XInputEnable(BOOL enable) {
    InterlockedExchange(&xinput_enabled,enable?1:0);
}
DWORD WINAPI XInputGetDSoundAudioDeviceGuids(DWORD index,GUID *render,GUID *capture) {
    DrnasState snapshot;
    if(index!=0 || !read_state(&snapshot))return ERROR_DEVICE_NOT_CONNECTED;
    if(render)ZeroMemory(render,sizeof(*render));
    if(capture)ZeroMemory(capture,sizeof(*capture));
    return ERROR_SUCCESS;
}
DWORD WINAPI XInputGetBatteryInformation(DWORD index,BYTE dev_type,XINPUT_BATTERY_INFORMATION *battery) {
    DrnasState snapshot;
    (void)dev_type;
    if(!battery)return ERROR_BAD_ARGUMENTS;
    if(index!=0 || !read_state(&snapshot))return ERROR_DEVICE_NOT_CONNECTED;
    battery->BatteryType=BATTERY_TYPE_WIRED;
    battery->BatteryLevel=BATTERY_LEVEL_FULL;
    return ERROR_SUCCESS;
}
DWORD WINAPI XInputGetKeystroke(DWORD index,DWORD reserved,XINPUT_KEYSTROKE *keystroke) {
    DrnasState snapshot;
    (void)reserved;
    if(!keystroke)return ERROR_BAD_ARGUMENTS;
    if(index!=0 || !read_state(&snapshot))return ERROR_DEVICE_NOT_CONNECTED;
    return ERROR_EMPTY;
}

BOOL WINAPI DllMain(HINSTANCE module,DWORD reason,LPVOID reserved) {
    (void)module;(void)reason;(void)reserved;
    /* No locks, allocations, IO or global callbacks under loader lock. */
    return TRUE;
}
