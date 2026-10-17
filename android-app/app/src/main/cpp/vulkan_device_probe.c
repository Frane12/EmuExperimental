#define VK_NO_PROTOTYPES
#include <vulkan/vulkan.h>
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>
#include <stdatomic.h>
#include <dirent.h>
#include <unistd.h>
#include <time.h>
#include <ctype.h>

/* Opaque Xlib ABI, dynamically loaded from the exact Wine dependency path.
 * No compile-time dependency on host Xlib headers or host Vulkan libraries. */
typedef struct _XDisplay Display;
typedef unsigned long Window;
typedef struct {
    VkStructureType sType;
    const void *pNext;
    VkFlags flags;
    Display *dpy;
    Window window;
} XlibSurfaceInfo;
typedef VkResult (VKAPI_PTR *CreateXlibSurface)(VkInstance,const XlibSurfaceInfo *,const VkAllocationCallbacks *,VkSurfaceKHR *);

/* The diagnostic child is process-isolated. Never skip Vulkan teardown:
 * report which native threads remain blocked after a timed observation. */
static atomic_int drnas_destroy_completed=0;
static void *drnas_wsi_watch(void *unused) {
    (void)unused;
    struct timespec delay={.tv_sec=3,.tv_nsec=0};
    nanosleep(&delay,NULL);
    if(atomic_load(&drnas_destroy_completed))return NULL;
    puts("DRNAS_WSI_WATCHDOG: vkDestroySwapchainKHR STILL BLOCKED after 3s");
    DIR *tasks=opendir("/proc/self/task");
    if(!tasks){perror("DRNAS_WSI_WATCHDOG opendir task");return NULL;}
    struct dirent *entry;
    int listed=0;
    while((entry=readdir(tasks))!=NULL && listed<48) {
        if(!isdigit((unsigned char)entry->d_name[0]))continue;
        char path[160],name[64]="?",wchan[96]="unavailable";
        snprintf(path,sizeof(path),"/proc/self/task/%s/comm",entry->d_name);
        FILE *f=fopen(path,"r");if(f){if(!fgets(name,sizeof(name),f))strcpy(name,"?");fclose(f);}
        snprintf(path,sizeof(path),"/proc/self/task/%s/wchan",entry->d_name);
        f=fopen(path,"r");if(f){if(!fgets(wchan,sizeof(wchan),f))strcpy(wchan,"unavailable");fclose(f);}
        name[strcspn(name,"\r\n")]=0;
        wchan[strcspn(wchan,"\r\n")]=0;
        printf("DRNAS_WSI_THREAD tid=%s comm=%s wchan=%s\n",entry->d_name,name,wchan);
        listed++;
    }
    closedir(tasks);
    puts("DRNAS_WSI_WATCHDOG: wchan is kernel wait location, NOT a native stack/backtrace");
    return NULL;
}

static int present_probe(PFN_vkGetInstanceProcAddr get,VkInstance instance,VkPhysicalDevice gpu,
                         VkPresentModeKHR requested_mode,int create_only,int sync_after_present) {
    int status=20;
    void *xlib=dlopen("libX11.so.6",RTLD_NOW|RTLD_LOCAL);
    if(!xlib){fprintf(stderr,"Xlib: %s\n",dlerror());return status;}
    int (*init_threads)(void)=(int (*)(void))dlsym(xlib,"XInitThreads");
    int xthreads=init_threads?init_threads():0;
    printf("DRNAS_WSI_XINITTHREADS result=%d (must precede XOpenDisplay)\n",xthreads);
    Display *(*open_display)(const char *)=(Display *(*)(const char *))dlsym(xlib,"XOpenDisplay");
    Window (*root_window)(Display *)=(Window (*)(Display *))dlsym(xlib,"XDefaultRootWindow");
    Window (*create_window)(Display *,Window,int,int,unsigned,unsigned,unsigned,unsigned long,unsigned long)=
        (Window (*)(Display *,Window,int,int,unsigned,unsigned,unsigned,unsigned long,unsigned long))dlsym(xlib,"XCreateSimpleWindow");
    int (*map_window)(Display *,Window)=(int (*)(Display *,Window))dlsym(xlib,"XMapWindow");
    int (*sync_display)(Display *,int)=(int (*)(Display *,int))dlsym(xlib,"XSync");
    int (*destroy_window)(Display *,Window)=(int (*)(Display *,Window))dlsym(xlib,"XDestroyWindow");
    int (*close_display)(Display *)=(int (*)(Display *))dlsym(xlib,"XCloseDisplay");
    if(!open_display||!root_window||!create_window||!map_window||!sync_display||!destroy_window||!close_display){dlclose(xlib);return 21;}
    Display *display=open_display(NULL);
    if(!display){dlclose(xlib);return 22;}
    Window window=create_window(display,root_window(display),0,0,64,64,0,0,0);
    map_window(display,window);sync_display(display,0);
    VkSurfaceKHR surface=VK_NULL_HANDLE;
    VkDevice device=VK_NULL_HANDLE;
    VkSwapchainKHR chain=VK_NULL_HANDLE;
    VkCommandPool pool=VK_NULL_HANDLE;
    VkFence fence=VK_NULL_HANDLE;
    PFN_vkDestroyDevice destroy_device=NULL;
    PFN_vkDestroySwapchainKHR destroy_chain=NULL;
    PFN_vkDestroyCommandPool destroy_pool=NULL;
    PFN_vkDestroyFence destroy_fence=NULL;
    PFN_vkDeviceWaitIdle idle=NULL;
#define INSTANCE(type,name) type name=(type)get(instance,#name)
    INSTANCE(CreateXlibSurface,vkCreateXlibSurfaceKHR);
    INSTANCE(PFN_vkDestroySurfaceKHR,vkDestroySurfaceKHR);
    INSTANCE(PFN_vkGetPhysicalDeviceSurfaceSupportKHR,vkGetPhysicalDeviceSurfaceSupportKHR);
    INSTANCE(PFN_vkGetPhysicalDeviceSurfaceCapabilitiesKHR,vkGetPhysicalDeviceSurfaceCapabilitiesKHR);
    INSTANCE(PFN_vkGetPhysicalDeviceSurfaceFormatsKHR,vkGetPhysicalDeviceSurfaceFormatsKHR);
    INSTANCE(PFN_vkGetPhysicalDeviceSurfacePresentModesKHR,vkGetPhysicalDeviceSurfacePresentModesKHR);
    INSTANCE(PFN_vkGetPhysicalDeviceQueueFamilyProperties,vkGetPhysicalDeviceQueueFamilyProperties);
    INSTANCE(PFN_vkCreateDevice,vkCreateDevice);
    INSTANCE(PFN_vkGetDeviceProcAddr,vkGetDeviceProcAddr);
    if(!vkCreateXlibSurfaceKHR||!vkDestroySurfaceKHR||!vkGetPhysicalDeviceSurfaceSupportKHR||
       !vkGetPhysicalDeviceSurfaceCapabilitiesKHR||!vkGetPhysicalDeviceSurfaceFormatsKHR||
       !vkGetPhysicalDeviceQueueFamilyProperties||!vkGetPhysicalDeviceSurfacePresentModesKHR||!vkCreateDevice||!vkGetDeviceProcAddr)goto done;
    XlibSurfaceInfo si={.sType=VK_STRUCTURE_TYPE_XLIB_SURFACE_CREATE_INFO_KHR,.dpy=display,.window=window};
    VkResult result=vkCreateXlibSurfaceKHR(instance,&si,NULL,&surface);
    printf("X11 Vulkan surface create=%d\n",result);
    if(result!=VK_SUCCESS){status=23;goto done;}
    uint32_t n=0,family=UINT32_MAX;
    vkGetPhysicalDeviceQueueFamilyProperties(gpu,&n,NULL);
    if(!n||n>64){status=24;goto done;}
    VkQueueFamilyProperties queues[64];vkGetPhysicalDeviceQueueFamilyProperties(gpu,&n,queues);
    for(uint32_t i=0;i<n;i++){
        VkBool32 supported=VK_FALSE;
        result=vkGetPhysicalDeviceSurfaceSupportKHR(gpu,i,surface,&supported);
        if(result==VK_SUCCESS&&supported&&(queues[i].queueFlags&VK_QUEUE_GRAPHICS_BIT)){family=i;break;}
    }
    if(family==UINT32_MAX){status=25;goto done;}
    float priority=1.0f;
    VkDeviceQueueCreateInfo qi={.sType=VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO,.queueFamilyIndex=family,.queueCount=1,.pQueuePriorities=&priority};
    const char *extension="VK_KHR_swapchain";
    VkDeviceCreateInfo di={.sType=VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO,.queueCreateInfoCount=1,.pQueueCreateInfos=&qi,.enabledExtensionCount=1,.ppEnabledExtensionNames=&extension};
    result=vkCreateDevice(gpu,&di,NULL,&device);
    printf("Vulkan logical device create=%d\n",result);
    if(result!=VK_SUCCESS){status=26;goto done;}
#define DEVICE(type,name) type name=(type)vkGetDeviceProcAddr(device,#name)
    destroy_device=(PFN_vkDestroyDevice)vkGetDeviceProcAddr(device,"vkDestroyDevice");
    destroy_chain=(PFN_vkDestroySwapchainKHR)vkGetDeviceProcAddr(device,"vkDestroySwapchainKHR");
    destroy_pool=(PFN_vkDestroyCommandPool)vkGetDeviceProcAddr(device,"vkDestroyCommandPool");
    destroy_fence=(PFN_vkDestroyFence)vkGetDeviceProcAddr(device,"vkDestroyFence");
    idle=(PFN_vkDeviceWaitIdle)vkGetDeviceProcAddr(device,"vkDeviceWaitIdle");
    DEVICE(PFN_vkGetDeviceQueue,vkGetDeviceQueue);
    DEVICE(PFN_vkCreateSwapchainKHR,vkCreateSwapchainKHR);
    DEVICE(PFN_vkGetSwapchainImagesKHR,vkGetSwapchainImagesKHR);
    DEVICE(PFN_vkAcquireNextImageKHR,vkAcquireNextImageKHR);
    DEVICE(PFN_vkCreateCommandPool,vkCreateCommandPool);
    DEVICE(PFN_vkAllocateCommandBuffers,vkAllocateCommandBuffers);
    DEVICE(PFN_vkBeginCommandBuffer,vkBeginCommandBuffer);
    DEVICE(PFN_vkCmdPipelineBarrier,vkCmdPipelineBarrier);
    DEVICE(PFN_vkCmdClearColorImage,vkCmdClearColorImage);
    DEVICE(PFN_vkEndCommandBuffer,vkEndCommandBuffer);
    DEVICE(PFN_vkCreateFence,vkCreateFence);
    DEVICE(PFN_vkWaitForFences,vkWaitForFences);
    DEVICE(PFN_vkQueueSubmit,vkQueueSubmit);
    DEVICE(PFN_vkQueuePresentKHR,vkQueuePresentKHR);
    if(!destroy_device||!destroy_chain||!destroy_pool||!destroy_fence||!idle||!vkGetDeviceQueue||
       !vkCreateSwapchainKHR||!vkGetSwapchainImagesKHR||!vkAcquireNextImageKHR||!vkCreateCommandPool||
       !vkAllocateCommandBuffers||!vkBeginCommandBuffer||!vkCmdPipelineBarrier||!vkCmdClearColorImage||
       !vkEndCommandBuffer||!vkCreateFence||!vkWaitForFences||!vkQueueSubmit||!vkQueuePresentKHR){status=27;goto done;}
    VkSurfaceCapabilitiesKHR cap;
    result=vkGetPhysicalDeviceSurfaceCapabilitiesKHR(gpu,surface,&cap);
    if(result!=VK_SUCCESS){status=28;goto done;}
    n=0;result=vkGetPhysicalDeviceSurfaceFormatsKHR(gpu,surface,&n,NULL);
    if(result!=VK_SUCCESS||!n||n>256){status=29;goto done;}
    VkSurfaceFormatKHR formats[256];result=vkGetPhysicalDeviceSurfaceFormatsKHR(gpu,surface,&n,formats);
    if(result!=VK_SUCCESS){status=30;goto done;}
    uint32_t mode_count=0;
    result=vkGetPhysicalDeviceSurfacePresentModesKHR(gpu,surface,&mode_count,NULL);
    if(result!=VK_SUCCESS||mode_count==0||mode_count>32){status=45;goto done;}
    VkPresentModeKHR modes[32];
    result=vkGetPhysicalDeviceSurfacePresentModesKHR(gpu,surface,&mode_count,modes);
    if(result!=VK_SUCCESS){status=46;goto done;}
    int found_mode=0;
    for(uint32_t m=0;m<mode_count;m++)if(modes[m]==requested_mode)found_mode=1;
    printf("DRNAS_WSI_MODE requested=%s supported=%d count=%u\n",
           requested_mode==VK_PRESENT_MODE_IMMEDIATE_KHR?"IMMEDIATE":"FIFO",found_mode,mode_count);
    if(!found_mode){status=47;goto done;}
    VkSurfaceFormatKHR format=formats[0];
    if(format.format==VK_FORMAT_UNDEFINED)format.format=VK_FORMAT_B8G8R8A8_UNORM;
    VkExtent2D extent=cap.currentExtent;
    if(extent.width==UINT32_MAX){extent.width=64;extent.height=64;}
    uint32_t count=cap.minImageCount+1;
    if(cap.maxImageCount&&count>cap.maxImageCount)count=cap.maxImageCount;
    if(!(cap.supportedUsageFlags&VK_IMAGE_USAGE_TRANSFER_DST_BIT)){status=31;goto done;}
    VkCompositeAlphaFlagBitsKHR alpha=(VkCompositeAlphaFlagBitsKHR)(cap.supportedCompositeAlpha&(~cap.supportedCompositeAlpha+1));
    VkSwapchainCreateInfoKHR sci={.sType=VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR,.surface=surface,
        .minImageCount=count,.imageFormat=format.format,.imageColorSpace=format.colorSpace,.imageExtent=extent,
        .imageArrayLayers=1,.imageUsage=VK_IMAGE_USAGE_TRANSFER_DST_BIT,.imageSharingMode=VK_SHARING_MODE_EXCLUSIVE,
        .preTransform=cap.currentTransform,.compositeAlpha=alpha,.presentMode=requested_mode,.clipped=VK_TRUE};
    result=vkCreateSwapchainKHR(device,&sci,NULL,&chain);
    printf("X11 Vulkan swapchain create=%d\n",result);
    if(result!=VK_SUCCESS){status=32;goto done;}
    if(create_only) {
        puts("DRNAS_WSI_EXPERIMENT CREATE_ONLY: swapchain created, NO acquire/submit/present");
        status=0;
        goto done;
    }
    n=0;result=vkGetSwapchainImagesKHR(device,chain,&n,NULL);
    if(result!=VK_SUCCESS||!n||n>16){status=33;goto done;}
    VkImage images[16];result=vkGetSwapchainImagesKHR(device,chain,&n,images);
    if(result!=VK_SUCCESS){status=34;goto done;}
    VkFenceCreateInfo fi={.sType=VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};
    result=vkCreateFence(device,&fi,NULL,&fence);
    if(result!=VK_SUCCESS){status=35;goto done;}
    uint32_t index;
    result=vkAcquireNextImageKHR(device,chain,2000000000ULL,VK_NULL_HANDLE,fence,&index);
    if(result!=VK_SUCCESS&&result!=VK_SUBOPTIMAL_KHR){status=36;goto done;}
    if(index>=n||vkWaitForFences(device,1,&fence,VK_TRUE,2000000000ULL)!=VK_SUCCESS){status=37;goto done;}
    VkCommandPoolCreateInfo pi={.sType=VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO,.queueFamilyIndex=family};
    if(vkCreateCommandPool(device,&pi,NULL,&pool)!=VK_SUCCESS){status=38;goto done;}
    VkCommandBufferAllocateInfo ai={.sType=VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO,.commandPool=pool,.level=VK_COMMAND_BUFFER_LEVEL_PRIMARY,.commandBufferCount=1};
    VkCommandBuffer cmd;
    if(vkAllocateCommandBuffers(device,&ai,&cmd)!=VK_SUCCESS){status=39;goto done;}
    VkCommandBufferBeginInfo bi={.sType=VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,.flags=VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT};
    if(vkBeginCommandBuffer(cmd,&bi)!=VK_SUCCESS){status=40;goto done;}
    VkImageSubresourceRange range={.aspectMask=VK_IMAGE_ASPECT_COLOR_BIT,.levelCount=1,.layerCount=1};
    VkImageMemoryBarrier barrier={.sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER,.dstAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT,
        .oldLayout=VK_IMAGE_LAYOUT_UNDEFINED,.newLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
        .srcQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED,.dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED,.image=images[index],.subresourceRange=range};
    vkCmdPipelineBarrier(cmd,VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,VK_PIPELINE_STAGE_TRANSFER_BIT,0,0,NULL,0,NULL,1,&barrier);
    VkClearColorValue color={.float32={0.04f,0.2f,0.4f,1.0f}};
    vkCmdClearColorImage(cmd,images[index],VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,&color,1,&range);
    barrier.srcAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT;barrier.dstAccessMask=0;
    barrier.oldLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;barrier.newLayout=VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
    vkCmdPipelineBarrier(cmd,VK_PIPELINE_STAGE_TRANSFER_BIT,VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,0,0,NULL,0,NULL,1,&barrier);
    if(vkEndCommandBuffer(cmd)!=VK_SUCCESS){status=41;goto done;}
    VkQueue queue;vkGetDeviceQueue(device,family,0,&queue);
    VkSubmitInfo submit={.sType=VK_STRUCTURE_TYPE_SUBMIT_INFO,.commandBufferCount=1,.pCommandBuffers=&cmd};
    if(vkQueueSubmit(queue,1,&submit,VK_NULL_HANDLE)!=VK_SUCCESS||idle(device)!=VK_SUCCESS){status=42;goto done;}
    VkPresentInfoKHR present={.sType=VK_STRUCTURE_TYPE_PRESENT_INFO_KHR,.swapchainCount=1,.pSwapchains=&chain,.pImageIndices=&index};
    result=vkQueuePresentKHR(queue,&present);
    printf("X11 Vulkan clear/present=%d\n",result);
    status=(result==VK_SUCCESS||result==VK_SUBOPTIMAL_KHR)?0:43;
    if(!status)puts("DRNAS_GPU_X11_SWAPCHAIN_PRESENT_VERIFIED");
    if(!status && sync_after_present) {
        /* This Xlib connection is not Mesa's XCB display. A successful
         * XSync proves only this connection's requests completed. */
        puts("DRNAS_WSI_XSYNC BEFORE XSync (different connection from Mesa WSI)");
        sync_display(display,0);
        puts("DRNAS_WSI_XSYNC AFTER XSync; NOT PROOF of Mesa queue completion");
        struct timespec delay={.tv_sec=0,.tv_nsec=400000000};
        nanosleep(&delay,NULL);
        puts("DRNAS_WSI_XSYNC 400ms observation completed; teardown follows");
    }
done:
    printf("DRNAS_WSI_TEARDOWN begin after present status=%d\n",status);
    if(device&&idle) {
        VkResult drained=idle(device);
        printf("DRNAS_WSI_TEARDOWN device_idle=%d\n",drained);
        if(drained!=VK_SUCCESS && status==0)status=44;
    }
    puts("DRNAS_WSI_TEARDOWN BEFORE vkDestroyFence");
    if(device&&fence&&destroy_fence)destroy_fence(device,fence,NULL);
    puts("DRNAS_WSI_TEARDOWN AFTER vkDestroyFence; BEFORE vkDestroyCommandPool");
    if(device&&pool&&destroy_pool)destroy_pool(device,pool,NULL);
    puts("DRNAS_WSI_TEARDOWN AFTER vkDestroyCommandPool; BEFORE vkDestroySwapchainKHR");
    if(device&&chain&&destroy_chain) {
        atomic_store(&drnas_destroy_completed,0);
        pthread_t watcher;
        if(pthread_create(&watcher,NULL,drnas_wsi_watch,NULL)==0) {
            pthread_detach(watcher);
            puts("DRNAS_WSI_WATCHDOG armed: kernel wchan snapshot if destroy blocks >3s");
        } else puts("DRNAS_WSI_WATCHDOG thread creation failed");
        destroy_chain(device,chain,NULL);
        atomic_store(&drnas_destroy_completed,1);
    }
    puts("DRNAS_WSI_TEARDOWN AFTER vkDestroySwapchainKHR; BEFORE vkDestroyDevice");
    if(device&&destroy_device)destroy_device(device,NULL);
    puts("DRNAS_WSI_TEARDOWN AFTER vkDestroyDevice; BEFORE vkDestroySurfaceKHR");
    if(surface&&vkDestroySurfaceKHR)vkDestroySurfaceKHR(instance,surface,NULL);
    puts("DRNAS_WSI_TEARDOWN Vulkan objects destroyed; XDestroyWindow next");
    destroy_window(display,window);
    puts("DRNAS_WSI_TEARDOWN XDestroyWindow sent; XSync next");
    sync_display(display,0);
    puts("DRNAS_WSI_TEARDOWN XSync completed; XCloseDisplay next");
    close_display(display);
    puts("DRNAS_WSI_TEARDOWN XCloseDisplay completed");
    dlclose(xlib);
    if(status)printf("DRNAS_X11_PRESENT_PROBE_FAILED stage=%d\n",status);
    return status;
}

int main(int argc,char **argv) {
    setvbuf(stdout,NULL,_IONBF,0);setvbuf(stderr,NULL,_IONBF,0);
    if(argc!=2&&argc!=3)return 2;
    int enumerate_only=argc==3&&strcmp(argv[2],"--enumerate-only")==0;
    int immediate=argc==3&&strcmp(argv[2],"--present-immediate")==0;
    int create_only=argc==3&&strcmp(argv[2],"--create-destroy")==0;
    int sync_after_present=argc==3&&strcmp(argv[2],"--present-sync")==0;
    if(argc==3&&!enumerate_only&&!immediate&&!create_only&&!sync_after_present)return 2;
    if(create_only)puts("DRNAS_WSI_TEST_A create-destroy only, no Present");
    if(sync_after_present)puts("DRNAS_WSI_TEST_B FIFO Present + XSync/delay observation");
    void *loader=dlopen(argv[1],RTLD_NOW|RTLD_LOCAL);
    if(!loader){fprintf(stderr,"Vulkan loader failed: %s\n",dlerror());return 3;}
    PFN_vkGetInstanceProcAddr get=(PFN_vkGetInstanceProcAddr)dlsym(loader,"vkGetInstanceProcAddr");
    if(!get)return 4;
    PFN_vkCreateInstance create=(PFN_vkCreateInstance)get(VK_NULL_HANDLE,"vkCreateInstance");
    if(!create)return 5;
    const char *extensions[]={"VK_KHR_surface","VK_KHR_xlib_surface"};
    VkApplicationInfo app={.sType=VK_STRUCTURE_TYPE_APPLICATION_INFO,.pApplicationName="DRNAS GPU X11 probe",.apiVersion=VK_API_VERSION_1_1};
    VkInstanceCreateInfo ci={.sType=VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO,.pApplicationInfo=&app,.enabledExtensionCount=2,.ppEnabledExtensionNames=extensions};
    VkInstance instance=VK_NULL_HANDLE;VkResult result=create(&ci,NULL,&instance);
    if(result!=VK_SUCCESS){fprintf(stderr,"vkCreateInstance(X11)=%d\n",result);return 6;}
    PFN_vkEnumeratePhysicalDevices enumerate=(PFN_vkEnumeratePhysicalDevices)get(instance,"vkEnumeratePhysicalDevices");
    PFN_vkGetPhysicalDeviceProperties2 properties=(PFN_vkGetPhysicalDeviceProperties2)get(instance,"vkGetPhysicalDeviceProperties2");
    PFN_vkDestroyInstance destroy=(PFN_vkDestroyInstance)get(instance,"vkDestroyInstance");
    if(!enumerate||!properties||!destroy)return 7;
    uint32_t count=0;result=enumerate(instance,&count,NULL);
    if(result!=VK_SUCCESS||!count||count>64){destroy(instance,NULL);return 8;}
    VkPhysicalDevice devices[64];result=enumerate(instance,&count,devices);
    int status=10;
    if(result==VK_SUCCESS)for(uint32_t i=0;i<count;i++) {
        VkPhysicalDeviceDriverProperties driver={.sType=VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DRIVER_PROPERTIES};
        VkPhysicalDeviceProperties2 p={.sType=VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2,.pNext=&driver};
        properties(devices[i],&p);
        printf("Vulkan device: %s | vendor=%04x device=%04x | driverID=%d %s %s\n",
            p.properties.deviceName,p.properties.vendorID,p.properties.deviceID,driver.driverID,driver.driverName,driver.driverInfo);
        puts(driver.driverID==VK_DRIVER_ID_MESA_TURNIP?"DRNAS_TURNIP_DEVICE_VERIFIED":"DRNAS_NATIVE_VULKAN_DEVICE_PRESENT__TURNIP_NOT_VERIFIED");
        if(p.properties.deviceType==VK_PHYSICAL_DEVICE_TYPE_CPU){puts("CPU Vulkan device rejected");continue;}
        const char *preferred=getenv("DRNAS_TURNIP_PREFERRED");
        if(preferred && strcmp(preferred,"1")==0 && driver.driverID!=VK_DRIVER_ID_MESA_TURNIP) {
            puts("DRNAS_TURNIP_REQUEST_NOT_HONORED: system/wrapper device is not proof of Turnip activation");
            status=12;continue;
        }
        status=enumerate_only?0:present_probe(get,instance,devices[i],immediate?VK_PRESENT_MODE_IMMEDIATE_KHR:VK_PRESENT_MODE_FIFO_KHR,create_only,sync_after_present);
        if(!status)break;
    }
    destroy(instance,NULL);dlclose(loader);
    return status;
}
