#define VK_NO_PROTOTYPES
#include <vulkan/vulkan.h>
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* Same Bionic loader and environment as Wine; never equate env vars with activation. */
int main(int argc,char **argv) {
    if(argc!=2)return 2;
    void *loader=dlopen(argv[1],RTLD_NOW|RTLD_LOCAL);
    if(!loader){fprintf(stderr,"Vulkan loader failed: %s\n",dlerror());return 3;}
    PFN_vkGetInstanceProcAddr get=(PFN_vkGetInstanceProcAddr)dlsym(loader,"vkGetInstanceProcAddr");
    if(!get)return 4;
    PFN_vkCreateInstance create=(PFN_vkCreateInstance)get(VK_NULL_HANDLE,"vkCreateInstance");
    if(!create)return 5;
    VkApplicationInfo app={.sType=VK_STRUCTURE_TYPE_APPLICATION_INFO,.pApplicationName="DRNAS Wine Vulkan probe",.apiVersion=VK_API_VERSION_1_1};
    VkInstanceCreateInfo ci={.sType=VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO,.pApplicationInfo=&app};
    VkInstance instance=VK_NULL_HANDLE;VkResult result=create(&ci,NULL,&instance);
    if(result!=VK_SUCCESS){fprintf(stderr,"vkCreateInstance=%d\n",result);return 6;}
    PFN_vkEnumeratePhysicalDevices enumerate=(PFN_vkEnumeratePhysicalDevices)get(instance,"vkEnumeratePhysicalDevices");
    PFN_vkGetPhysicalDeviceProperties properties=(PFN_vkGetPhysicalDeviceProperties)get(instance,"vkGetPhysicalDeviceProperties");
    PFN_vkDestroyInstance destroy=(PFN_vkDestroyInstance)get(instance,"vkDestroyInstance");
    if(!enumerate||!properties||!destroy)return 7;
    uint32_t count=0;result=enumerate(instance,&count,NULL);
    if(result!=VK_SUCCESS||!count){destroy(instance,NULL);return 8;}
    VkPhysicalDevice *devices=calloc(count,sizeof(*devices));
    if(!devices){destroy(instance,NULL);return 9;}
    result=enumerate(instance,&count,devices);int turnip=0;
    if(result==VK_SUCCESS)for(uint32_t i=0;i<count;i++) {
        VkPhysicalDeviceProperties p;properties(devices[i],&p);
        printf("Vulkan device: %s | vendor=%04x device=%04x driver=%u\n",p.deviceName,p.vendorID,p.deviceID,p.driverVersion);
        if(strstr(p.deviceName,"Turnip")||strstr(p.deviceName,"turnip"))turnip=1;
    }
    free(devices);destroy(instance,NULL);dlclose(loader);
    puts(turnip?"DRNAS_TURNIP_DEVICE_VERIFIED":"DRNAS_TURNIP_DEVICE_NOT_VERIFIED");
    return turnip?0:10;
}
