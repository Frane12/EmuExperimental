#define _GNU_SOURCE
#include <dlfcn.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* Scoped compatibility for Mesa's sw,noshm PutImage WSI on an X server
 * without Present. Never advertise an extension or fabricate a frame.
 * ABI declarations match libxcb (no Android XCB development headers needed).
 */
typedef struct { unsigned int sequence; } Cookie;
typedef struct { const char *name; int global_id; } Extension;
typedef struct {
    uint8_t response_type, pad0;
    uint16_t sequence;
    uint32_t length;
    uint8_t present, major_opcode, first_event, first_error;
} ExtensionReply;

static void *resolve(const char *name, const char *library) {
    void *symbol = dlsym(RTLD_NEXT, name);
    if (!symbol) {
        void *handle = dlopen(library, RTLD_NOW | RTLD_LOCAL | RTLD_NOLOAD);
        if (handle) { symbol = dlsym(handle, name); dlclose(handle); }
    }
    if (!symbol) { fprintf(stderr, "DRNAS_XCB_GUARD missing symbol %s\n", name); abort(); }
    return symbol;
}
static int absent_present(void *connection) {
    const char *enabled = getenv("DRNAS_XCB_PRESENT_GUARD");
    const char *mode = getenv("MESA_VK_WSI_DEBUG");
    if (!enabled || strcmp(enabled, "1") || !mode || strcmp(mode, "sw,noshm")) return 0;
    Cookie (*query)(void *, uint16_t, const char *) = resolve("xcb_query_extension", "libxcb.so.1");
    ExtensionReply *(*reply)(void *, Cookie, void **) = resolve("xcb_query_extension_reply", "libxcb.so.1");
    ExtensionReply *answer = reply(connection, query(connection, 7, "Present"), NULL);
    int absent = answer && !answer->present;
    free(answer);
    return absent;
}
static Cookie select_input(void *connection, uint32_t event, uint32_t window,
                           uint32_t mask, int checked) {
    if (absent_present(connection)) {
        /* Use a real core-protocol cookie: callers may discard/check it.
         * No Present events are required by the synchronous PutImage path. */
        Cookie (*noop)(void *) = resolve(checked ? "xcb_no_operation_checked" : "xcb_no_operation", "libxcb.so.1");
        fprintf(stderr, "DRNAS_XCB_GUARD skipped unavailable Present SelectInput window=%u mask=%u checked=%d; PutImage only\n", window, mask, checked);
        return noop(connection);
    }
    Cookie (*real)(void *, uint32_t, uint32_t, uint32_t) = resolve(
        checked ? "xcb_present_select_input_checked" : "xcb_present_select_input", "libxcb-present.so.0");
    return real(connection, event, window, mask);
}
Cookie xcb_present_select_input(void *c, uint32_t e, uint32_t w, uint32_t m) { return select_input(c,e,w,m,0); }
Cookie xcb_present_select_input_checked(void *c, uint32_t e, uint32_t w, uint32_t m) { return select_input(c,e,w,m,1); }
void *xcb_register_for_special_xge(void *c, Extension *ext, uint32_t id, uint32_t *stamp) {
    if (ext && ext->name && !strcmp(ext->name,"Present") && absent_present(c)) {
        fprintf(stderr,"DRNAS_XCB_GUARD no Present event queue for PutImage\n");
        return NULL;
    }
    void *(*real)(void *, Extension *, uint32_t, uint32_t *) = resolve("xcb_register_for_special_xge", "libxcb.so.1");
    return real(c,ext,id,stamp);
}
void xcb_unregister_for_special_event(void *c, void *event) {
    if (!event) return;
    void (*real)(void *, void *) = resolve("xcb_unregister_for_special_event", "libxcb.so.1");
    real(c,event);
}
