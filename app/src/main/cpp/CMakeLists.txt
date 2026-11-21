cmake_minimum_required(VERSION 3.22.1)
project("openbullet_scanner" LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(
    openbullet_scanner
    SHARED
    native_scanner.cpp
)

target_compile_options(
    openbullet_scanner
    PRIVATE
    -O3
    -Wall
    -Wextra
)

target_link_options(
    openbullet_scanner
    PRIVATE
    "-Wl,-z,max-page-size=16384"
)

find_library(log-lib log)

target_link_libraries(
    openbullet_scanner
    ${log-lib}
)
