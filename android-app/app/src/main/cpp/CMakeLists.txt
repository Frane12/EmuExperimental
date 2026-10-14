cmake_minimum_required(VERSION 3.22.1)
project(drnas_native_host C)
add_library(drnas_host SHARED drnas_host.c drnas_cpu_assist.c drnas_accelerator_probe.c)
# Optimize the CPU reference kernel even in debug APKs; runtime/Wine
# code keeps the normal debug flags for diagnostics.
set_source_files_properties(drnas_cpu_assist.c PROPERTIES COMPILE_OPTIONS "-O3")
target_compile_features(drnas_host PRIVATE c_std_11)
target_compile_options(drnas_host PRIVATE -Wall -Wextra -Werror -fstack-protector-strong)
target_link_libraries(drnas_host PRIVATE dl)
