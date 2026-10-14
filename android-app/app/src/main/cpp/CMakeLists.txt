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
