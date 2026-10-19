cmake_minimum_required(VERSION 3.22.1)
project(drnas_native_host C)
add_library(drnas_host SHARED drnas_host.c)
target_compile_features(drnas_host PRIVATE c_std_11)
target_compile_options(drnas_host PRIVATE -Wall -Wextra -Werror -fstack-protector-strong)
target_link_libraries(drnas_host PRIVATE dl)

# Wine server NLS source-path compatibility when the ELF is loaded through
# Android linker64 instead of the executable being the process image.
add_library(drnas_wine_nls SHARED wine_nls_compat.c)
target_compile_features(drnas_wine_nls PRIVATE c_std_11)
target_compile_options(drnas_wine_nls PRIVATE -Wall -Wextra -Werror -fstack-protector-strong)
target_link_libraries(drnas_wine_nls PRIVATE dl)

# Scoped Wine child-process execv relay, isolated to the Wineboot lab.
add_library(drnas_wine_child_exec SHARED wine_child_exec_compat.c)
target_compile_features(drnas_wine_child_exec PRIVATE c_std_11)
target_compile_options(drnas_wine_child_exec PRIVATE -Wall -Wextra -Werror -fstack-protector-strong)
target_link_libraries(drnas_wine_child_exec PRIVATE dl)


# Experimental diagnostic: dynamic NNAPI accelerator enumeration only.
# No neural inference, proprietary QNN binary, or runtime GPU intervention.
add_library(drnas_npu_probe SHARED neuroflow_npu_probe.c)
target_compile_features(drnas_npu_probe PRIVATE c_std_11)
target_compile_options(drnas_npu_probe PRIVATE -Wall -Wextra -Werror)
target_link_libraries(drnas_npu_probe PRIVATE dl)

# Only suppress unavailable Present subscriptions for the PutImage WSI path.
add_library(drnas_xcb_present_guard SHARED xcb_present_guard.c)
target_compile_features(drnas_xcb_present_guard PRIVATE c_std_11)
target_compile_options(drnas_xcb_present_guard PRIVATE -Wall -Wextra -Werror)
target_link_libraries(drnas_xcb_present_guard PRIVATE dl)
