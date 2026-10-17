"""Exercise ELF interposition, gating, real cookies and delegation on host CI.
Android linker binding and visible pixels still require the device test.
"""
import os
import pathlib
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
FAKE = r'''
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
typedef struct { unsigned sequence; } Cookie;
typedef struct {const char *name; int global_id;} Extension;
typedef struct {uint8_t type,pad; uint16_t sequence; uint32_t length; uint8_t present,opcode,event,error;} Reply;
Cookie xcb_query_extension(void *c,uint16_t n,const char *s) {(void)c;(void)n;(void)s;return (Cookie){1};}
Reply *xcb_query_extension_reply(void *c,Cookie k,void **e) {(void)c;(void)k;(void)e;Reply *r=calloc(1,sizeof(*r));r->present=atoi(getenv("FAKE_PRESENT"));return r;}
Cookie xcb_no_operation(void *c) {(void)c;return (Cookie){101};}
Cookie xcb_no_operation_checked(void *c) {(void)c;return (Cookie){102};}
Cookie xcb_present_select_input(void *c,uint32_t e,uint32_t w,uint32_t m) {(void)c;(void)e;(void)w;(void)m;return (Cookie){201};}
Cookie xcb_present_select_input_checked(void *c,uint32_t e,uint32_t w,uint32_t m) {(void)c;(void)e;(void)w;(void)m;return (Cookie){202};}
void *xcb_register_for_special_xge(void *c,Extension *e,uint32_t id,uint32_t *s) {(void)c;(void)e;(void)id;(void)s;return (void *)1;}
void xcb_unregister_for_special_event(void *c,void *e) {(void)c;if(e!=(void *)1)abort();}
'''
MAIN = r'''
#include <stdint.h>
#include <stdio.h>
typedef struct {unsigned sequence;} Cookie;
typedef struct {const char *name;int global_id;} Extension;
extern Cookie xcb_present_select_input(void *,uint32_t,uint32_t,uint32_t);
extern Cookie xcb_present_select_input_checked(void *,uint32_t,uint32_t,uint32_t);
extern void *xcb_register_for_special_xge(void *,Extension *,uint32_t,uint32_t *);
extern void xcb_unregister_for_special_event(void *,void *);
int main(void) {
 Extension e={"Present",0},other={"Other",0};
 printf("%u %u ",xcb_present_select_input(0,1,2,7).sequence,xcb_present_select_input_checked(0,1,2,0).sequence);
 void *q=xcb_register_for_special_xge(0,&e,1,0);
 printf("%d %d\n",q!=0,xcb_register_for_special_xge(0,&other,1,0)!=0);
 xcb_unregister_for_special_event(0,q);return 0;
}
'''

class GuardTest(unittest.TestCase):
    def test_interposition_and_scope(self):
        with tempfile.TemporaryDirectory() as folder:
            p=pathlib.Path(folder)
            (p/'fake.c').write_text(FAKE)
            (p/'main.c').write_text(MAIN)
            def cc(*args): subprocess.run(['cc','-Wall','-Wextra','-Werror',*map(str,args)],check=True)
            cc('-shared','-fPIC',p/'fake.c','-o',p/'libfake.so')
            cc('-shared','-fPIC',ROOT/'android-app/app/src/main/cpp/xcb_present_guard.c','-ldl','-o',p/'guard.so')
            cc(p/'main.c','-L'+folder,'-lfake','-Wl,-rpath,'+folder,'-o',p/'test')
            for enabled,mode,present,want in [
                ('1','sw,noshm','0','101 102 0 1'),
                ('1','sw,noshm','1','201 202 1 1'),
                ('0','sw,noshm','0','201 202 1 1'),
                ('1','hw','0','201 202 1 1')]:
                with self.subTest(enabled=enabled,mode=mode,present=present):
                    env=dict(os.environ,LD_PRELOAD=str(p/'guard.so'),DRNAS_XCB_PRESENT_GUARD=enabled,MESA_VK_WSI_DEBUG=mode,FAKE_PRESENT=present)
                    result=subprocess.run([str(p/'test')],env=env,text=True,capture_output=True,check=True)
                    self.assertEqual(result.stdout.strip(),want)
                    self.assertEqual('DRNAS_XCB_GUARD skipped' in result.stderr,want.startswith('101'))

if __name__=='__main__': unittest.main()
